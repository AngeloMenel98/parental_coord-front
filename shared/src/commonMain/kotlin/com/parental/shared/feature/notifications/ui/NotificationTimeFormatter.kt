package com.parental.shared.feature.notifications.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Tiempo relativo en castellano para la marca de tiempo de una notificación.
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
        else -> {
            val local = instant.toLocalDateTime(timeZone)
            "${local.dayOfMonth.pad2()}/${local.monthNumber.pad2()}/${local.year}"
        }
    }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_HOUR = 3_600_000L
private const val MS_PER_DAY = 86_400_000L
