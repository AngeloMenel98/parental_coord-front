package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * Derivación pura del timeline — cambio `calendar-design-redesign`, tema T4 (paso 1).
 *
 * Kotlin puro: sin `androidx.compose.*`, sin estado, sin `@OptIn`. Convierte
 * `List<Actividad>` en los grafos `AgendaGroup` / `DayBucket` que la UI consume.
 *
 * CONGELADO (R3-T4-3) — se traslada byte a byte, sin reescribir:
 *   · `resolveSelectedDate` y su comentario de decisión de producto.
 *   · `mondayStart`. NO "limpiar" su aritmética a un `when`: la resta con
 *     `dayOfWeek.ordinal` es correcta y un refactor cosmético aquí mezclaría
 *     una decisión de comportamiento en un commit de reubicación.
 *   · `compareTimedRows` NO adquiere ninguna componente de estado (C7, permanente):
 *     el orden es por inicio y luego por claves de texto, nunca por estado.
 */

internal fun filterActivities(
    all: List<Actividad>,
    tab: ActivityTab,
): List<Actividad> = when (tab) {
    ActivityTab.TODOS -> all
    ActivityTab.EVENTOS -> all.filter { it.type == "event" }
    ActivityTab.OBLIGACIONES -> all.filter { it.type == "obligation" }
}

internal fun buildTimelineRows(
    actividades: List<Actividad>,
    zone: TimeZone,
): List<TimelineRow> = actividades.map { actividad ->
    val start = parseInstant(actividad.scheduledStart)
    TimelineRow(
        actividad = actividad,
        scheduledStart = start,
        localStart = start?.toLocalDateTime(zone),
        deadline = parseInstant(actividad.deadline),
    )
}

internal fun parseInstant(raw: String?): Instant? = raw?.let {
    runCatching { Instant.parse(it) }.getOrNull()
}

internal fun buildAgendaGroups(rows: List<TimelineRow>): List<AgendaGroup> {
    val timedRows = rows
        .filter { it.localStart != null }
        .sortedWith(::compareTimedRows)
    val timedGroups = timedRows
        .groupBy { row -> row.localStart?.date }
        .mapNotNull { (date, entries) ->
            date?.let { AgendaGroup(it, entries.sortedWith(::compareTimedRows)) }
        }
        .sortedBy { it.date }

    val undatedRows = rows
        .filter { it.localStart == null }
        .sortedWith(::compareUndatedRows)
    val undatedGroup = if (undatedRows.isEmpty()) {
        emptyList()
    } else {
        listOf(AgendaGroup(date = null, rows = undatedRows))
    }

    return timedGroups + undatedGroup
}

internal fun buildWeekDates(weekStart: LocalDate): List<LocalDate> =
    (0..6).map { offset -> weekStart.plus(offset, DateTimeUnit.DAY) }

internal fun buildDayBucket(date: LocalDate, rows: List<TimelineRow>): DayBucket {
    val dayRows = rows
        .filter { it.localStart?.date == date }
        .sortedWith(::compareTimedRows)
    val slots = dayRows
        .groupBy { row -> row.localStart!!.hour to row.localStart!!.minute }
        .map { (time, slotRows) -> AgendaSlot(time.first, time.second, slotRows) }
        .sortedWith(compareBy<AgendaSlot> { it.hour }.thenBy { it.minute })
    return DayBucket(date = date, slots = slots)
}

internal fun resolveSelectedDate(
    weekStart: LocalDate,
    today: LocalDate,
    rows: List<TimelineRow>,
): LocalDate {
    val dates = buildWeekDates(weekStart)
    val todayHasTimedRows = rows.any { it.localStart?.date == today }
    if (today in dates && todayHasTimedRows) return today

    val earliestTimedDay = rows
        .mapNotNull { it.localStart?.date }
        .filter { it in dates }
        .minOrNull()
    if (earliestTimedDay != null) return earliestTimedDay

    // La decisión de producto es "hoy" como último recurso, incluso tras navegar.
    return today
}

internal fun compareTimedRows(left: TimelineRow, right: TimelineRow): Int {
    val byStart = compareNullableInstants(left.scheduledStart, right.scheduledStart)
    if (byStart != 0) return byStart
    return compareTextKeys(left.actividad, right.actividad)
}

internal fun compareUndatedRows(left: TimelineRow, right: TimelineRow): Int {
    val byDeadline = compareNullableInstants(left.deadline, right.deadline)
    if (byDeadline != 0) return byDeadline
    return compareTextKeys(left.actividad, right.actividad)
}

internal fun compareTextKeys(left: Actividad, right: Actividad): Int {
    val byId = left.id.compareTo(right.id)
    if (byId != 0) return byId
    return left.title.compareTo(right.title)
}

internal fun compareNullableInstants(left: Instant?, right: Instant?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right)
}

internal fun currentDate(): LocalDate = currentDateIn(TimeZone.currentSystemDefault())

internal fun currentDateIn(zone: TimeZone): LocalDate =
    Clock.System.now().toLocalDateTime(zone).date

internal fun mondayStart(date: LocalDate): LocalDate =
    date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

internal fun parseIsoDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw) }.getOrNull()
