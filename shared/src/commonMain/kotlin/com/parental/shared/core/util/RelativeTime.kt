package com.parental.shared.core.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Tiempo relativo en castellano — UNA sola fuente para toda la app.
 *
 * 🔴 ESTA UTILIDAD VIVE AQUÍ, NO EN EL FEATURE DE NOTIFICACIONES: el vocabulario de
 *   las fechas es de la APP, no de una pantalla. La lista de Gastos (T29) va a
 *   pintar "María G. · 1d ago" con EXACTAMENTE este mismo texto, y dos
 *   formatters distintos acabarían diciendo "hace 1 d" y "1d ago" en dos filas de
 *   la misma familia.
 *
 * El texto es el del PRD (`PRD_expense` issue #2: "UI is Spanish but dates read
 * "1d ago", "10 ago", "05 dé"") — en castellano y sin truncar el mes.
 *
 * La API entrega ISO-8601 (`createdAt`), no un epoch: el parseo y el formato son
 * responsabilidad de la UI. `now` y `timeZone` son parámetros —con default de
 * producción— para que el test sea determinista sin tocar el reloj del sistema.
 *
 * Un `createdAt` ilegible devuelve `""`: es preferible no pintar nada a pintar
 * una fecha inventada.
 */
fun formatRelativeTime(
    iso: String,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""

    // Skew de reloj o fecha futura: "ahora" es la lectura honesta.
    val elapsedMs = (now - instant).inWholeMilliseconds.coerceAtLeast(0L)
    val minutes = elapsedMs / MS_PER_MINUTE
    val hours = elapsedMs / MS_PER_HOUR
    val days = elapsedMs / MS_PER_DAY

    return when {
        minutes < 1L -> "ahora"
        minutes < 60L -> "hace $minutes min"
        hours < 24L -> "hace $hours h"
        days < 7L -> "hace $days d"
        else -> formatShortDate(instant.toLocalDateTime(timeZone).date)
    }
}

/**
 * Fecha corta numérica: `"19/10/2026"`.
 *
 * 🔴 NUMÉRICA A PROPÓSITO: una fecha con el MES EN LETRAS abrevia y se corta
 * ("05 dé") cuando la columna es estrecha. Los números no se cortan nunca.
 */
fun formatShortDate(date: LocalDate): String =
    "${date.dayOfMonth.pad2()}/${date.monthNumber.pad2()}/${date.year}"

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_HOUR = 3_600_000L
private const val MS_PER_DAY = 86_400_000L
