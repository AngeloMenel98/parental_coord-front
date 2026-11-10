package com.parental.shared.feature.activities.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Derivación pura del RIEL del tiempo — T1 de `odd/tasks/activity-rail.md` (S3).
 *
 * Kotlin puro: sin `androidx.compose.*`, sin estado, sin `@OptIn`, sin `Clock.System`.
 * La zona entra SIEMPRE por parámetro para que las pruebas sean deterministas; la fecha
 * del filtro también. La UI (T2–T4) consume estos grafos y no los recalcula.
 *
 * ── Decisiones que el enunciado de T1 no fijaba ─────────────────────────────
 *  · MINUTOS DE DÍA, NO INSTANTES. Los bloques miden `[startMinute, endMinute)` como
 *    minutos desde la medianoche LOCAL del día pedido: es la unidad que la canaleta de
 *    horas (T2) y la altura proporcional (T3) necesitan, y evita repetir la conversión
 *    de zona en cada capa.
 *  · UN BLOQUE NO SALE DE SU DÍA. Una actividad que termina después de medianoche
 *    conserva su duración completa para la altura, pero el fin se recorta a las 24:00:
 *    el eje de T2 es el eje de UN día y un bloque que se pinta fuera de él queda
 *    huérfano (no hay dónde anclar su tick de fin ni su etiqueta).
 *  · `scheduledEnd` se mide en DURACIÓN (fin − inicio en instantes), no como minutos de
 *    día del fin: así un fin en otro día no produce un bloque de duración negativa.
 *  · El desempate FINAL del orden es `id` y luego `title` — nunca estado ni hora de
 *    creación: el orden tiene que ser estable entre recomposiciones y recargas.
 */

/** Ancho del carril de un eje: minutos de día, `[start, end)`. */
internal data class RailAxis(val startMinute: Int, val endMinute: Int)

/** Un bloque del riel: la fila original más su geometría ya resuelta. */
internal data class RailBlock(
    val row: TimelineRow,
    val startMinute: Int,
    val endMinute: Int,
    /** Índice del carril, 0 = más a la izquierda. */
    val lane: Int,
    /** Total de carriles del GRUPO de solapamiento al que pertenece el bloque. */
    val laneCount: Int,
)

/** Un día de riel: su eje, sus bloques ordenados y los ticks horarios del eje. */
internal data class RailDay(
    val date: LocalDate,
    val axis: RailAxis,
    val blocks: List<RailBlock>,
    val hourTicks: List<Int>,
)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * Construye el riel de [date] con las filas que empiezan ese día LOCAL en [zone].
 *
 * Las filas sin `scheduledStart` no entran: pertenecen a la sección "sin fecha de
 * inicio", que no tiene eje horario.
 */
internal fun buildRailDay(
    rows: List<TimelineRow>,
    date: LocalDate,
    zone: TimeZone,
    defaultStartMinute: Int = 7 * MINUTES_PER_HOUR,
    defaultEndMinute: Int = 21 * MINUTES_PER_HOUR,
    minBlockMinutes: Int = 20,
): RailDay {
    val placed = rows
        .mapNotNull { row -> placedBlock(row, date, zone, minBlockMinutes) }
        .sortedWith(railOrder)
    val blocks = assignLanes(placed)
    val axis = railAxis(blocks, defaultStartMinute, defaultEndMinute)
    return RailDay(date = date, axis = axis, blocks = blocks, hourTicks = railHourTicks(axis))
}

/** Bloque con la geometría resuelta antes de repartir carriles. */
private data class PlacedBlock(
    val row: TimelineRow,
    val startMinute: Int,
    val endMinute: Int,
)

/** Punto de barrido para la concurrencia máxima de un grupo. */
private data class SweepEvent(val minute: Int, val delta: Int)

/**
 * Orden del día: inicio, y a igual inicio la MÁS LARGA primero (la píldora alta queda
 * detrás, que es lo que el arteboard dibuja); `id`/`title` sólo desempatan.
 */
private val railOrder: Comparator<PlacedBlock> = compareBy<PlacedBlock> { it.startMinute }
    .thenByDescending { it.endMinute - it.startMinute }
    .thenBy { it.row.actividad.id }
    .thenBy { it.row.actividad.title }

private fun placedBlock(
    row: TimelineRow,
    date: LocalDate,
    zone: TimeZone,
    minBlockMinutes: Int,
): PlacedBlock? {
    val startInstant = row.scheduledStart ?: return null
    val localStart = startInstant.toLocalDateTime(zone)
    if (localStart.date != date) return null
    val startMinute = localStart.hour * MINUTES_PER_HOUR + localStart.minute
    return PlacedBlock(
        row = row,
        startMinute = startMinute,
        endMinute = endMinute(row, startInstant, startMinute, minBlockMinutes),
    )
}

/**
 * Fin del bloque en minutos de día.
 *
 * Un `scheduledEnd` legible y ESTRICTAMENTE posterior al inicio fija la duración; uno
 * igual (o anterior) es un rango vacío y no dice nada, y uno ilegible tampoco: los dos
 * caen al mínimo de toque ([minBlockMinutes]), no a cero (un bloque de altura cero no
 * se puede tocar).
 */
private fun endMinute(
    row: TimelineRow,
    startInstant: Instant,
    startMinute: Int,
    minBlockMinutes: Int,
): Int {
    val endInstant = parseInstant(row.actividad.scheduledEnd)
    val duration = if (endInstant != null && endInstant > startInstant) {
        (endInstant - startInstant).inWholeMinutes.toInt()
    } else {
        minBlockMinutes
    }
    return (startMinute + duration).coerceAtMost(MINUTES_PER_DAY)
}

/**
 * Reparto de carriles por coloración de intervalos sobre `[start, end)`.
 *
 * Cada bloque toma el MENOR índice libre (coloración greedy por inicio), y dentro de su
 * grupo de solapamiento transitivo todos comparten `laneCount` = concurrencia máxima del
 * grupo. Así una cadena A–B–C (donde A y C no se tocan) pinta el mismo ancho en los
 * tres: si C se pintara a ancho completo se comería el hueco de B.
 */
private fun assignLanes(placed: List<PlacedBlock>): List<RailBlock> {
    val blocks = mutableListOf<RailBlock>()
    var groupStart = 0
    while (groupStart < placed.size) {
        // El grupo se cierra cuando el inicio siguiente no toca ningún bloque ya abierto.
        var groupEnd = groupStart + 1
        var reach = placed[groupStart].endMinute
        while (groupEnd < placed.size && placed[groupEnd].startMinute < reach) {
            reach = maxOf(reach, placed[groupEnd].endMinute)
            groupEnd += 1
        }
        val group = placed.subList(groupStart, groupEnd)
        val laneCount = maxConcurrency(group)
        // `laneEnds[i]` es el fin del último bloque pintado en el carril `i`.
        val laneEnds = mutableListOf<Int>()
        group.forEach { block ->
            var lane = laneEnds.indexOfFirst { end -> end <= block.startMinute }
            if (lane < 0) {
                laneEnds.add(block.endMinute)
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = block.endMinute
            }
            blocks.add(
                RailBlock(
                    row = block.row,
                    startMinute = block.startMinute,
                    endMinute = block.endMinute,
                    lane = lane,
                    laneCount = laneCount,
                ),
            )
        }
        groupStart = groupEnd
    }
    return blocks
}

/**
 * Concurrencia máxima de un grupo: barrido con los FINES antes que los INICIOS en el
 * mismo minuto, porque los intervalos son semiabiertos (`[start, end)`) y dos bloques
 * contiguos no se solapan.
 */
private fun maxConcurrency(group: List<PlacedBlock>): Int {
    val events = group.flatMap { block ->
        listOf(SweepEvent(block.startMinute, 1), SweepEvent(block.endMinute, -1))
    }.sortedWith(compareBy<SweepEvent>({ it.minute }, { it.delta }))
    var current = 0
    var peak = 0
    events.forEach { event ->
        current += event.delta
        peak = maxOf(peak, current)
    }
    return maxOf(peak, 1)
}

/**
 * Eje del día: arranca en el mínimo entre el inicio temprano y el arranque por defecto
 * (redondeado a la hora hacia abajo) y termina en el máximo entre el fin tardío y el
 * cierre por defecto (redondeado hacia arriba). Un día sin bloques usa los valores por
 * defecto tal cual.
 */
private fun railAxis(
    blocks: List<RailBlock>,
    defaultStartMinute: Int,
    defaultEndMinute: Int,
): RailAxis {
    if (blocks.isEmpty()) return RailAxis(defaultStartMinute, defaultEndMinute)
    val start = floorToHour(minOf(defaultStartMinute, blocks.minOf { it.startMinute }))
    val end = ceilToHour(maxOf(defaultEndMinute, blocks.maxOf { it.endMinute }))
    return RailAxis(start, end)
}

private fun floorToHour(minuteOfDay: Int): Int =
    minuteOfDay.coerceAtLeast(0) / MINUTES_PER_HOUR * MINUTES_PER_HOUR

private fun ceilToHour(minuteOfDay: Int): Int =
    (minuteOfDay.coerceAtLeast(0) + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR * MINUTES_PER_HOUR

/** Ticks cada hora desde el inicio del eje, sin pasar del fin. */
private fun railHourTicks(axis: RailAxis): List<Int> =
    generateSequence(axis.startMinute) { minute -> minute + MINUTES_PER_HOUR }
        .takeWhile { minute -> minute <= axis.endMinute }
        .toList()

/** Distancia vertical de [minuteOfDay] al inicio del eje, en dp. */
internal fun railOffsetDp(minuteOfDay: Int, axis: RailAxis, dpPerHour: Float): Float =
    (minuteOfDay - axis.startMinute) / MINUTES_PER_HOUR.toFloat() * dpPerHour

/** Alto proporcional a la duración, con el mínimo de toque (S11) como piso. */
internal fun railHeightDp(block: RailBlock, dpPerHour: Float, minHeightDp: Float): Float {
    val durationMinutes = block.endMinute - block.startMinute
    val proportional = durationMinutes / MINUTES_PER_HOUR.toFloat() * dpPerHour
    return maxOf(minHeightDp, proportional)
}

/** Tramo horizontal del bloque dentro de su grupo: `lane/laneCount .. (lane+1)/laneCount`. */
internal fun railLaneFraction(block: RailBlock): ClosedFloatingPointRange<Float> {
    val count = block.laneCount.coerceAtLeast(1)
    val lane = block.lane.coerceIn(0, count - 1)
    return (lane.toFloat() / count)..((lane + 1).toFloat() / count)
}
