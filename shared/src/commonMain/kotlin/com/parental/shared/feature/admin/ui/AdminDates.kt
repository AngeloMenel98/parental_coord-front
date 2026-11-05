package com.parental.shared.feature.admin.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/*
 * F1 · S4 — puente entre el `DatePicker` y el backend, SIEMPRE en UTC.
 *
 * El picker marca el día elegido con milisegundos a la medianoche UTC. Leerlo con
 * la zona del dispositivo corre la fecha un día en husos negativos (UTC-3), y el
 * backend guarda `date`. Es el mismo ida y vuelta que documenta
 * `CreateDateConversion.kt` para el alta de actividades.
 */

/** `YYYY-MM-DD` → milis UTC de ese día, para precargar el picker. */
internal fun adminIsoToPickerMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(iso).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
        .getOrNull()
}

/** Milis UTC del picker → `YYYY-MM-DD` (lo que el backend valida como `date`). */
internal fun adminPickerMillisToIso(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    val month = date.monthNumber.toString().padStart(2, '0')
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}

/** `dd/mm/aaaa` fijo para mostrar; el backend habla ISO. */
internal fun adminFormatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val parts = iso.split('-')
    if (parts.size != 3) return iso
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

/** Hoy en UTC, en ISO. Se calcula al abrir la pantalla, no por frame. */
internal fun adminTodayIso(): String =
    Instant.fromEpochMilliseconds(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()

/** Primer día del mes en curso: el rango con el que abre la pestaña de reportes. */
internal fun adminMonthStartIso(): String = "${adminTodayIso().take(7)}-01"

/** `12,5%` — dos decimales con coma, sin `String.format` (no existe en common). */
internal fun adminFormatPercent(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}%"
}

/** `1.234,56` — separador de miles con punto y decimales con coma. */
internal fun adminFormatMoney(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    val whole = (cents / 100).toString()
    val grouped = whole.reversed().chunked(3).joinToString(".").reversed()
    return "$grouped,${(cents % 100).toString().padStart(2, '0')}"
}
