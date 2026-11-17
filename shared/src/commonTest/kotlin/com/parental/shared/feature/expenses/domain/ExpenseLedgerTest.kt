package com.parental.shared.feature.expenses.domain

import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S3 · los cálculos de la fila del libro.
 *
 * `payerShare` aplica el porcentaje que manda el servidor; no hay una segunda regla de
 * reparto en el cliente. `viewerShare` (T7) decide qué porcentaje corresponde al viewer:
 * solicitante → `splitRatioPayer`, contraparte → `splitRatioOther`, identidad desconocida
 * u otra persona → `null`. `ledgerDate` es tolerante a propósito: la fecha del gasto puede
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
    fun `la parte del viewer es la del solicitante cuando el viewer pidio el gasto`() {
        val expense = sampleExpense(
            requestedBy = "user-a",
            counterpartId = "user-b",
            splitRatioPayer = 60,
            splitRatioOther = 40,
        )

        assertEquals(6_000.0, viewerShare(expense, viewerId = "user-a"))
    }

    @Test
    fun `la parte del viewer es la de la contraparte cuando el viewer es la contraparte`() {
        val expense = sampleExpense(
            requestedBy = "user-a",
            counterpartId = "user-b",
            splitRatioPayer = 60,
            splitRatioOther = 40,
        )

        assertEquals(4_000.0, viewerShare(expense, viewerId = "user-b"))
    }

    @Test
    fun `la parte del viewer se omite cuando la identidad no se conoce`() {
        val expense = sampleExpense(
            requestedBy = "user-a",
            counterpartId = "user-b",
            splitRatioPayer = 60,
            splitRatioOther = 40,
        )

        assertNull(viewerShare(expense, viewerId = null))
    }

    @Test
    fun `la parte del viewer se omite cuando el viewer no es parte del gasto`() {
        val expense = sampleExpense(
            requestedBy = "user-a",
            counterpartId = "user-b",
            splitRatioPayer = 60,
            splitRatioOther = 40,
        )

        assertNull(viewerShare(expense, viewerId = "user-c"))
    }

    @Test
    fun `la parte del viewer sigue visible cuando el ratio es 100 por ciento`() {
        val expense = sampleExpense(
            requestedBy = "user-a",
            counterpartId = "user-b",
            splitRatioPayer = 100,
            splitRatioOther = 0,
        )

        assertEquals(10_000.0, viewerShare(expense, viewerId = "user-a"))
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

private fun sampleExpense(
    requestedBy: String,
    counterpartId: String,
    splitRatioPayer: Int,
    splitRatioOther: Int,
): ExpenseResponseDto = ExpenseResponseDto(
    id = "exp-1",
    bondId = "bond-1",
    categoryId = "cat-1",
    categoryName = "Comida",
    concept = "Almuerzo",
    description = null,
    amount = 10_000.0,
    expenseDate = "2026-11-03",
    splitRatioPayer = splitRatioPayer,
    splitRatioOther = splitRatioOther,
    status = ExpenseStatus.PENDING,
    requestedBy = requestedBy,
    requestedByName = null,
    counterpartId = counterpartId,
    counterpartName = null,
    approvedBy = null,
    approvedByName = null,
    approvedAt = null,
    paidAt = null,
    createdAt = "2026-11-03T10:00:00Z",
    updatedAt = "2026-11-03T10:00:00Z",
    canEdit = false,
    canCancel = false,
    canApprove = false,
    canReject = false,
    canMarkPaid = false,
)
