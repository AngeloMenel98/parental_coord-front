package com.parental.shared.feature.notifications.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S3 · tiempo relativo de la fila.
 *
 * La API entrega ISO-8601 y no un epoch; el formateo vive en la UI. `now` y la
 * zona son parámetros para que el test no dependa del reloj ni del TZ de la máquina.
 */
class NotificationTimeFormatterTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val utc = TimeZone.UTC

    private fun format(iso: String) = formatRelativeTime(iso, now, utc)

    @Test
    fun `S3 menos de un minuto es ahora`() {
        assertEquals("ahora", format("2026-10-26T11:59:30Z"))
    }

    @Test
    fun `S3 minutos y horas`() {
        assertEquals("hace 5 min", format("2026-10-26T11:55:00Z"))
        assertEquals("hace 59 min", format("2026-10-26T11:01:00Z"))
        assertEquals("hace 1 h", format("2026-10-26T11:00:00Z"))
        assertEquals("hace 23 h", format("2026-10-25T13:00:00Z"))
    }

    @Test
    fun `S3 dias hasta una semana`() {
        assertEquals("hace 1 d", format("2026-10-25T12:00:00Z"))
        assertEquals("hace 6 d", format("2026-10-20T12:00:00Z"))
    }

    @Test
    fun `S3 una semana o mas usa fecha local`() {
        assertEquals("19/10/2026", format("2026-10-19T12:00:00Z"))
    }

    @Test
    fun `S3 una fecha futura por skew se lee como ahora`() {
        assertEquals("ahora", format("2026-10-26T12:05:00Z"))
    }

    @Test
    fun `S3 un createdAt ilegible no inventa una fecha`() {
        assertEquals("", format("no-es-una-fecha"))
    }
}
