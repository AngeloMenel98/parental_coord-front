package com.parental.shared.feature.home.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S4 · los dos textos que la pantalla de cumplimiento imprime.
 *
 * `formatPercentage` salía de un `toString()` de `Double` y producía `"50.0%"`; el
 * período llegaba como `"2026-11"` y se mostraba crudo. Los dos se ven a simple vista en
 * el teléfono, y ninguno de los dos rompía un test.
 */
class HomeFormattingTest {

    @Test
    fun `un porcentaje redondo no arrastra decimales`() {
        assertEquals("50%", formatPercentage(50.0))
        assertEquals("0%", formatPercentage(0.0))
        assertEquals("100%", formatPercentage(100.0))
    }

    @Test
    fun `un porcentaje con decimales usa coma`() {
        assertEquals("66,7%", formatPercentage(66.66))
        assertEquals("33,3%", formatPercentage(33.33))
        assertEquals("91,7%", formatPercentage(91.7))
    }

    @Test
    fun `el porcentaje se recorta al rango del anillo`() {
        assertEquals("0%", formatPercentage(-5.0))
        assertEquals("100%", formatPercentage(140.0))
        assertEquals("0%", formatPercentage(Double.NaN))
    }

    @Test
    fun `un valor que redondea a cien no muestra decimal`() {
        assertEquals("100%", formatPercentage(99.99))
    }

    @Test
    fun `el periodo se muestra con el nombre del mes`() {
        assertEquals("Noviembre", compliancePeriodLabel("2026-11"))
        assertEquals("Enero", compliancePeriodLabel("2026-01"))
        assertEquals("Diciembre", compliancePeriodLabel("2026-12"))
    }

    @Test
    fun `el encabezado no repite el conteo que vive dentro del anillo`() {
        val label = compliancePeriodLabel("2026-11")

        assertFalse(
            " de " in label || "tareas" in label,
            "🔴 S4: el \"X de Y tareas\" vive SÓLO dentro del anillo; el encabezado dice el mes.",
        )
    }

    @Test
    fun `un periodo con otro formato se muestra tal cual`() {
        assertEquals("Noviembre 2026", compliancePeriodLabel("Noviembre 2026"))
        assertEquals("2026-13", compliancePeriodLabel("2026-13"))
        assertEquals("Este mes", compliancePeriodLabel("   "))
    }

    @Test
    fun `solo se reconoce el formato anio-mes`() {
        assertEquals("Noviembre", monthNameOf("2026-11"))
        assertEquals(null, monthNameOf("11/2026"))
        assertEquals(null, monthNameOf("2026"))
        assertTrue(monthNameOf("2026-99") == null, "Un mes fuera de 1..12 no inventa nombre.")
    }
}
