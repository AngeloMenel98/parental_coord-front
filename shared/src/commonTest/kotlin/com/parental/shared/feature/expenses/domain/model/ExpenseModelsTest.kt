package com.parental.shared.feature.expenses.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato de modelos de gastos (S1/T28).
 *
 * Verifica que los DTOs deserializan el JSON exacto del backend y que los defaults
 * protegen contra servidores que omitan los flags de permisos.
 */
class ExpenseModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val expenseJson = """
        {
            "id": "exp-1",
            "bondId": "bond-1",
            "categoryId": "cat-1",
            "categoryName": "Educación",
            "concept": "Cuota escolar",
            "description": "Marzo",
            "amount": 125000.50,
            "expenseDate": "2026-03-01",
            "splitRatioPayer": 50,
            "splitRatioOther": 50,
            "status": "pending",
            "requestedBy": "user-a",
            "counterpartId": "user-b",
            "approvedBy": null,
            "approvedAt": null,
            "paidAt": null,
            "createdAt": "2026-03-01T10:00:00.000Z",
            "updatedAt": "2026-03-01T10:00:00.000Z",
            "canEdit": true,
            "canCancel": true,
            "canApprove": false,
            "canReject": false,
            "canMarkPaid": false,
            "isTerminal": false,
            "hasDocument": false
        }
    """.trimIndent()

    @Test
    fun `deserializa un gasto completo con todos los campos del contrato`() {
        val expense = json.decodeFromString(ExpenseResponseDto.serializer(), expenseJson)

        assertEquals("exp-1", expense.id)
        assertEquals("bond-1", expense.bondId)
        assertEquals("cat-1", expense.categoryId)
        assertEquals("Educación", expense.categoryName)
        assertEquals("Cuota escolar", expense.concept)
        assertEquals("Marzo", expense.description)
        assertEquals(125000.50, expense.amount)
        assertEquals("2026-03-01", expense.expenseDate)
        assertEquals(50, expense.splitRatioPayer)
        assertEquals(50, expense.splitRatioOther)
        assertEquals(ExpenseStatus.PENDING, expense.status)
        assertEquals("user-a", expense.requestedBy)
        assertEquals("user-b", expense.counterpartId)
        assertNull(expense.approvedBy)
        assertNull(expense.approvedAt)
        assertNull(expense.paidAt)
        assertEquals("2026-03-01T10:00:00.000Z", expense.createdAt)
        assertEquals("2026-03-01T10:00:00.000Z", expense.updatedAt)
        assertTrue(expense.canEdit)
        assertTrue(expense.canCancel)
        assertFalse(expense.canApprove)
        assertFalse(expense.canReject)
        assertFalse(expense.canMarkPaid)
        assertFalse(expense.isTerminal)
        assertFalse(expense.hasDocument)
    }

    @Test
    fun `los flags de permisos defaultan a false si el servidor no los manda`() {
        val minimal = """
            {
                "id": "exp-1",
                "bondId": "bond-1",
                "categoryId": "cat-1",
                "categoryName": "Otros",
                "concept": "X",
                "amount": 100.0,
                "expenseDate": "2026-03-01",
                "splitRatioPayer": 50,
                "splitRatioOther": 50,
                "status": "approved",
                "requestedBy": "user-a",
                "counterpartId": "user-b",
                "createdAt": "2026-03-01T10:00:00.000Z",
                "updatedAt": "2026-03-01T10:00:00.000Z"
            }
        """.trimIndent()

        val expense = json.decodeFromString(ExpenseResponseDto.serializer(), minimal)

        assertFalse(expense.canEdit)
        assertFalse(expense.canCancel)
        assertFalse(expense.canApprove)
        assertFalse(expense.canReject)
        assertFalse(expense.canMarkPaid)
        assertFalse(expense.isTerminal)
        assertFalse(expense.hasDocument)
    }

    @Test
    fun `serializa los estados a las cadenas del backend`() {
        assertEquals("\"pending\"", json.encodeToString(ExpenseStatus.serializer(), ExpenseStatus.PENDING))
        assertEquals("\"approved\"", json.encodeToString(ExpenseStatus.serializer(), ExpenseStatus.APPROVED))
        assertEquals("\"rejected\"", json.encodeToString(ExpenseStatus.serializer(), ExpenseStatus.REJECTED))
        assertEquals("\"paid\"", json.encodeToString(ExpenseStatus.serializer(), ExpenseStatus.PAID))
    }

    @Test
    fun `los nombres de display son los del PRD`() {
        assertEquals("Pendiente", ExpenseStatus.PENDING.displayName())
        assertEquals("Aprobado", ExpenseStatus.APPROVED.displayName())
        assertEquals("Rechazado", ExpenseStatus.REJECTED.displayName())
        assertEquals("Pagado", ExpenseStatus.PAID.displayName())
    }

    @Test
    fun `deserializa la paginacion`() {
        val pageJson = """
            {
                "items": [$expenseJson],
                "total": 1,
                "limit": 20,
                "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString(PaginatedExpensesDto.serializer(), pageJson)

        assertEquals(1, page.items.size)
        assertEquals(1, page.total)
        assertEquals(20, page.limit)
        assertEquals(0, page.offset)
    }

    @Test
    fun `deserializa el resumen de saldos`() {
        val summaryJson = """
            {
                "saldo": -25000.00,
                "porPagar": 25000.00,
                "porCobrar": 0.00,
                "counts": {
                    "pendientes": 2,
                    "aprobados": 1,
                    "pagados": 0,
                    "rechazados": 0
                }
            }
        """.trimIndent()

        val summary = json.decodeFromString(ExpenseSummaryDto.serializer(), summaryJson)

        assertEquals(-25000.00, summary.saldo)
        assertEquals(25000.00, summary.porPagar)
        assertEquals(0.00, summary.porCobrar)
        assertEquals(2, summary.counts.pendientes)
        assertEquals(1, summary.counts.aprobados)
        assertEquals(0, summary.counts.pagados)
        assertEquals(0, summary.counts.rechazados)
    }
}
