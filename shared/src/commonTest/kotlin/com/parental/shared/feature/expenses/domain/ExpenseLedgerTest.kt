package com.parental.shared.feature.expenses.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S3 · los dos cálculos de la fila del libro.
 *
 * `payerShare` aplica el porcentaje que manda el servidor; no hay una segunda regla de
 * reparto en el cliente. `ledgerDate` es tolerante a propósito: la fecha del gasto puede
 * llegar como `yyyy-MM-dd` o como ISO con hora.
 */
class ExpenseLedgerTest {

    @Test
    fun `la parte del solicitante es su porcentaje del total`() {
        assertEquals(5_000.0, payerShare(10_000.0, 50))
        assertEquals(6_000.0, payerShare(10_000.0, 60))
        assertEquals(10_000.0, payerShare(10_000.0, 100))
        assertEquals(0.0, payerShare(10_000.0, 0))
    }

    @Test
    fun `un porcentaje fuera de rango se recorta`() {
        assertEquals(10_000.0, payerShare(10_000.0, 150))
        assertEquals(0.0, payerShare(10_000.0, -20))
    }

    @Test
    fun `un monto que no es numero no produce una parte`() {
        assertEquals(0.0, payerShare(Double.NaN, 50))
        assertEquals(0.0, payerShare(Double.POSITIVE_INFINITY, 50))
    }

    @Test
    fun `la parte no inventa centavos`() {
        assertEquals(3_600.0, payerShare(7_200.0, 50))
        assertEquals(4_900.0, payerShare(9_800.0, 50))
    }

    @Test
    fun `una fecha iso se parte en dia y mes abreviado`() {
        val date = ledgerDate("2026-11-03")

        assertEquals("3", date.day)
        assertEquals("nov", date.month)
        assertTrue(date.parsed)
    }

    @Test
    fun `una fecha iso con hora usa solo la parte de fecha`() {
        val date = ledgerDate("2026-11-03T00:00:00Z")

        assertEquals("3", date.day)
        assertEquals("nov", date.month)
        assertTrue(date.parsed)
    }

    @Test
    fun `el mes se abrevia en tres letras`() {
        assertEquals("ene", ledgerDate("2026-01-15").month)
        assertEquals("dic", ledgerDate("2026-12-01").month)
        assertEquals("jul", ledgerDate("2026-07-09").month)
    }

    @Test
    fun `una fecha ilegible se muestra tal cual y se marca como no leida`() {
        val date = ledgerDate("03/11/2026")

        assertFalse(date.parsed)
        assertEquals("03/11/2026", date.day)
        assertEquals("", date.month)
    }
}
