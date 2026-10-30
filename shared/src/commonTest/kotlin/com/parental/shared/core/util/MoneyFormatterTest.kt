package com.parental.shared.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * S5 · Formateo de dinero para es-AR.
 *
 * El importe llega en la misma unidad que el backend (`amount: number` con escala 2),
 * es decir, pesos con decimales. La función vive en `core` porque la van a usar
 * varias superficies (Gastos en T29/T33, el saldo de Home en T20).
 */
class MoneyFormatterTest {

    @Test
    fun `S5 separadores locales y dos decimales`() {
        assertEquals("$632,80", formatMoney(632.8))
    }

    @Test
    fun `S5 separador de miles`() {
        assertEquals("$1.234,56", formatMoney(1234.56))
    }

    @Test
    fun `S5 cero`() {
        assertEquals("$0,00", formatMoney(0.0))
    }

    @Test
    fun `S5 monto negativo`() {
        assertEquals("-$632,80", formatMoney(-632.8))
    }

    @Test
    fun `S5 redondea el tercer decimal por exceso`() {
        // El redondeo es sobre el NÚMERO ESCRITO: 632.805 → 632,81.
        assertEquals("$632,81", formatMoney(632.805))
        assertEquals("$632,80", formatMoney(632.804))
    }

    @Test
    fun `S5 un importe grande no revienta`() {
        // `Double.toString()` salta a notación científica desde 1E7 y el backend admite
        // numeric(12,2): hasta 9.999.999.999,99.
        assertEquals("$10.000.000,00", formatMoney(10_000_000.0))
        assertEquals("$9.999.999.999,99", formatMoney(9_999_999_999.99))
    }

    @Test
    fun `S5 un importe diminuto no revienta`() {
        assertEquals("$0,00", formatMoney(0.0000123))
    }

    @Test
    fun `S5 un importe absurdo se rechaza`() {
        assertFailsWith<IllegalArgumentException> { formatMoney(1e13) }
    }

    @Test
    fun `S5 un importe no finito se rechaza`() {
        assertFailsWith<IllegalArgumentException> { formatMoney(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { formatMoney(Double.POSITIVE_INFINITY) }
    }
}
