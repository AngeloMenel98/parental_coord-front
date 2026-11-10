package com.parental.shared.feature.activities.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T-FE-09 — etiquetas de criticidad (CC-5).
 */
class TimelineFormattersCriticalityTest {
    @Test
    fun `critical se mapea a Alta`() {
        assertEquals("Alta", criticalityLabel("critical"))
    }

    @Test
    fun `high se mapea a Alta`() {
        assertEquals("Alta", criticalityLabel("high"))
    }

    @Test
    fun `medium se mapea a Media`() {
        assertEquals("Media", criticalityLabel("medium"))
    }

    @Test
    fun `low se mapea a Baja`() {
        assertEquals("Baja", criticalityLabel("low"))
    }

    @Test
    fun `valor desconocido se devuelve tal cual`() {
        assertEquals("unknown", criticalityLabel("unknown"))
    }
}