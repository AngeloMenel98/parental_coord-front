package com.parental.shared.feature.home.domain

import com.parental.shared.core.ui.theme.StatusTokens
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S4 · la banda del anillo.
 *
 * Los cortes son los del diseño (25 / 50 / 75) y se prueban en el borde exacto y en el
 * centavo anterior: una bandeja que cambia un punto antes se lee como un bug de color.
 */
class ComplianceRingTest {

    @Test
    fun `cero es rojo`() {
        assertEquals(StatusTokens.Status.Overdue, complianceBand(0.0))
    }

    @Test
    fun `justo debajo de 25 sigue en rojo`() {
        assertEquals(StatusTokens.Status.Overdue, complianceBand(24.9))
    }

    @Test
    fun `25 ya es naranja`() {
        assertEquals(StatusTokens.Status.InProgress, complianceBand(25.0))
    }

    @Test
    fun `justo debajo de 50 sigue en naranja`() {
        assertEquals(StatusTokens.Status.InProgress, complianceBand(49.9))
    }

    @Test
    fun `50 ya es ambar`() {
        assertEquals(StatusTokens.Status.Verify, complianceBand(50.0))
    }

    @Test
    fun `justo debajo de 75 sigue en ambar`() {
        assertEquals(StatusTokens.Status.Verify, complianceBand(74.9))
    }

    @Test
    fun `75 ya es verde`() {
        assertEquals(StatusTokens.Status.Done, complianceBand(75.0))
    }

    @Test
    fun `cien es verde`() {
        assertEquals(StatusTokens.Status.Done, complianceBand(100.0))
    }

    @Test
    fun `un porcentaje que no es numero cae en rojo`() {
        assertEquals(
            StatusTokens.Status.Overdue,
            complianceBand(Double.NaN),
            "Sin avance demostrable el anillo no puede pintar verde.",
        )
        assertEquals(StatusTokens.Status.Overdue, complianceBand(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `un negativo se queda en la banda roja`() {
        assertEquals(StatusTokens.Status.Overdue, complianceBand(-5.0))
    }

    @Test
    fun `un porcentaje mayor a cien no se sale de verde`() {
        assertEquals(StatusTokens.Status.Done, complianceBand(140.0))
    }

    @Test
    fun `los segmentos encendidos respetan el rango del anillo`() {
        assertEquals(0, complianceSegmentsFilled(0.0))
        assertEquals(0, complianceSegmentsFilled(-5.0))
        assertEquals(3, complianceSegmentsFilled(25.0))
        assertEquals(6, complianceSegmentsFilled(50.0))
        assertEquals(8, complianceSegmentsFilled(66.7))
        assertEquals(9, complianceSegmentsFilled(75.0))
        assertEquals(COMPLIANCE_RING_SEGMENTS, complianceSegmentsFilled(100.0))
        assertEquals(COMPLIANCE_RING_SEGMENTS, complianceSegmentsFilled(140.0))
        assertEquals(0, complianceSegmentsFilled(Double.NaN))
    }

    @Test
    fun `el anillo tiene doce segmentos`() {
        assertEquals(12, COMPLIANCE_RING_SEGMENTS)
    }
}
