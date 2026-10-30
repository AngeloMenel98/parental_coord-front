package com.parental.shared.feature.activities.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S6 · Fechas absolutas del detalle de actividad.
 *
 * El detalle muestra `"5 ago 2026"`: día, mes COMPLETO (la abreviatura `ES_MONTHS`) y
 * año SIEMPRE. Los dos defectos que este archivo tapa son los del PRD: un mes
 * truncado ("05 dé") y una fecha sin año.
 */
class TimelineFormattersDateTest {

    private val utc = TimeZone.UTC

    @Test
    fun `S6 la fecha del detalle lleva dia mes y ano`() {
        assertEquals("5 ago 2026", formatDetailDate("2026-08-05T00:00:00Z", utc))
    }

    @Test
    fun `S6 el mes no se trunca`() {
        // "dé" sería un mes cortado a dos letras: ilegible.
        assertEquals("3 dic 2026", formatDetailDate("2026-12-03T00:00:00Z", utc))
        assertEquals("9 ene 2026", formatDetailDate("2026-01-09T00:00:00Z", utc))
    }

    @Test
    fun `S6 el ano sale siempre`() {
        assertEquals("5 ago 2024", formatDetailDate("2024-08-05T00:00:00Z", utc))
    }

    @Test
    fun `S6 entrada ilegible devuelve null`() {
        assertNull(formatDetailDate("no-es-una-fecha", utc))
        assertNull(formatDetailDate(null, utc))
    }

    @Test
    fun `S9 D4 la fecha del detalle lleva la hora`() {
        assertEquals(
            "5 ago 2026 · 16:30",
            formatDetailDateTime("2026-08-05T16:30:00Z", null, utc),
        )
    }

    @Test
    fun `S9 D4 con fin programado se muestra el rango`() {
        assertEquals(
            "5 ago 2026 · 16:30 – 18:00",
            formatDetailDateTime("2026-08-05T16:30:00Z", "2026-08-05T18:00:00Z", utc),
        )
    }

    @Test
    fun `S9 D4 un rango que cruza la medianoche repite el dia`() {
        // Dos horas sueltas ("22:00 - 01:00") en un rango que cambia de día no dicen cuándo
        // termina. El día se repite.
        assertEquals(
            "5 ago 2026 · 22:00 – 6 ago 2026 · 01:00",
            formatDetailDateTime("2026-08-05T22:00:00Z", "2026-08-06T01:00:00Z", utc),
        )
    }

    @Test
    fun `S9 D4 sin inicio no hay par de fecha`() {
        assertNull(formatDetailDateTime(null, "2026-08-05T18:00:00Z", utc))
        assertNull(formatDetailDateTime("no-es-una-fecha", null, utc))
    }

    @Test
    fun `S9 D4 un fin ilegible no borra el inicio`() {
        // Degradar a "cuándo empieza" es mejor que perder el par entero.
        assertEquals(
            "5 ago 2026 · 16:30",
            formatDetailDateTime("2026-08-05T16:30:00Z", "no-es-una-fecha", utc),
        )
    }
}
