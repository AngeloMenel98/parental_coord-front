package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.isOverdueStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Densidad mensual — T5 de `odd/tasks/activity-rail.md` (S4).
 *
 * "codifica cantidad con opacidad y número —y que se muestra u oculta desde el pill del
 * encabezado del calendario—". Este archivo es la mitad PURA: cuántas actividades hay por
 * día, si ese día tiene alguna vencida, y en qué escalón de opacidad cae. La mitad visual es
 * `MonthDensityMap.kt`, que no deriva nada: consume este mapa.
 *
 * 🔴 NO ESTÁ MONTADO EN `ActivitiesScreen` (eso es T6) NI REEMPLAZA A NADIE: `CalendarJump.kt`
 * sigue siendo la fuente del calendario modal, que se retira en T8.
 *
 * Kotlin puro: sin `androidx.compose.*`, sin estado, sin reloj. La zona entra por parámetro,
 * así que el mes de una actividad lo decide la zona del usuario y no el huso de la máquina.
 *
 * DENSIDAD ≠ CALENDARIO. `buildDayDots` cuenta CATEGORÍAS (un punto por categoría, tope 3)
 * porque allí el punto identifica el área; aquí se cuenta ACTIVIDADES, porque lo que la celda
 * codifica es cuánto trabajo tiene el día. Son dos preguntas distintas sobre las mismas filas.
 */

/**
 * Lo que una celda de día codifica: cuántas actividades empiezan ese día y si alguna está
 * vencida.
 *
 * @param count actividades que EMPIEZAN ese día (no categorías, y sin tope: el número se
 *   escribe, así que un día de seis actividades dice 6 y no tres puntos).
 * @param hasOverdue `true` si al menos una de esas actividades está vencida.
 */
internal data class MonthDensity(val count: Int, val hasOverdue: Boolean)

/**
 * Escalón de opacidad de la celda — 0, 1, 2, 3 y 4+.
 *
 * Son CINCO y no cuatro porque "sin actividades" no puede ser el primer peldaño de la escala:
 * un día vacío se lee como una celda sin tinte, no como "apenas lleno". El arteboard
 * `03-riel-del-tiempo.html` tiene exactamente esta forma (`lvl1..lvl4` más la celda base).
 */
internal enum class DensityLevel { EMPTY, LOW, MEDIUM, HIGH, FULL }

/** Escalón al que cae [count]. Los negativos no existen; se tratan como vacío. */
internal fun densityLevel(count: Int): DensityLevel = when {
    count <= 0 -> DensityLevel.EMPTY
    count == 1 -> DensityLevel.LOW
    count == 2 -> DensityLevel.MEDIUM
    count == 3 -> DensityLevel.HIGH
    else -> DensityLevel.FULL
}

/**
 * Mapa día → densidad para [year]/[monthNumber], decidido EN [zone].
 *
 * REGLAS, y cada una se prueba en `MonthDensityTest`:
 *  · Sólo cuentan las filas cuyo INICIO PROGRAMADO cae dentro del mes en [zone]. La fecha se
 *    recalcula desde `scheduledStart` con la zona recibida; `localStart` pudo derivarse con
 *    otra zona (lo hace `buildTimelineRows`), y leerlo aquí haría que la misma actividad
 *    cambiara de mes según quién construyó la fila.
 *  · Las filas SIN fecha de inicio quedan fuera: pertenecen a la sección "Sin fecha de
 *    inicio" del riel, no a un día del mes.
 *  · Los días sin filas NO aparecen en el mapa (`null`). El mapa no rellena el mes: quien
 *    pinta la grilla ya sabe qué fechas existen (`buildMonthGrid`) y pide la densidad celda
 *    por celda.
 */
internal fun buildMonthDensity(
    rows: List<TimelineRow>,
    year: Int,
    monthNumber: Int,
    zone: TimeZone,
): Map<LocalDate, MonthDensity> = rows
    .mapNotNull { row ->
        val start = row.scheduledStart ?: return@mapNotNull null
        start.toLocalDateTime(zone).date to row
    }
    .filter { (date, _) -> date.year == year && date.monthNumber == monthNumber }
    .groupBy({ (date, _) -> date }, { (_, row) -> row })
    .mapValues { (_, dayRows) ->
        MonthDensity(
            count = dayRows.size,
            hasOverdue = dayRows.any { row -> isOverdueStatus(row.actividad.status) },
        )
    }

/**
 * Rótulo accesible de una celda del mapa — la ÚNICA frase que la celda anuncia.
 *
 * No reutiliza `dayCellSemanticLabel` porque esa firma pide la LISTA DE CATEGORÍAS del día y
 * el mapa de densidad no las tiene (ni las necesita: pide un resolver de categorías que la
 * pantalla no pasa a T5). Se conserva su forma exacta —`formatDayTitle`, ", hoy", el mismo
 * criterio de plural—, así que las dos celdas del producto se leen igual.
 *
 * El estado se dice en PALABRAS ("vencida") y no sólo con el punto de `error`: S11 exige que
 * el estado nunca se codifique sólo con color.
 */
internal fun densityCellSemanticLabel(
    date: LocalDate,
    today: LocalDate,
    density: MonthDensity?,
): String {
    val marker = if (date == today) ", hoy" else ""
    val body = when {
        density == null || density.count <= 0 -> "sin actividades"
        density.count == 1 -> "1 actividad"
        else -> "${density.count} actividades"
    }
    val overdue = if (density?.hasOverdue == true) ", vencida" else ""
    return "${formatDayTitle(date)}$marker, $body$overdue"
}
