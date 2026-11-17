package com.parental.shared.feature.expenses.domain

import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S5 · la línea temporal del detalle.
 *
 * Regla del artboard: los pasos **sólo existen cuando ocurrieron**. Un gasto pendiente no
 * muestra "Pago" con un guion; el paso futuro no se dibuja. Cuando el gasto está aprobado y
 * todavía no se pagó, el paso actual es "Esperando pago".
 */
class ExpenseTimelineTest {

    @Test
    fun `un gasto pendiente muestra creado y la espera actual`() {
        val steps = expenseTimeline(sampleExpense(status = ExpenseStatus.PENDING))

        assertEquals(listOf("Gasto creado", "Esperando aprobación"), steps.map { it.title })
        assertEquals(ExpenseStepState.Done, steps[0].state)
        assertEquals(ExpenseStepState.Current, steps[1].state)
    }

    @Test
    fun `un gasto pendiente no inventa el paso de pago`() {
        val steps = expenseTimeline(sampleExpense(status = ExpenseStatus.PENDING))

        assertTrue(
            steps.none { it.title == "Pago" },
            "El paso de pago sólo aparece cuando hay paidAt.",
        )
    }

    @Test
    fun `un gasto aprobado suma aprobado y espera el pago`() {
        val steps = expenseTimeline(
            sampleExpense(
                status = ExpenseStatus.APPROVED,
                approvedAt = "2026-11-03T18:04:00Z",
                approvedByName = "Ángelo Pérez",
            ),
        )

        assertEquals(listOf("Gasto creado", "Aprobado", "Esperando pago"), steps.map { it.title })
        assertEquals(ExpenseStepState.Current, steps.last().state)
        assertTrue(steps[1].detail?.contains("Ángelo Pérez") == true)
    }

    @Test
    fun `un gasto pagado cierra la linea sin paso actual`() {
        val steps = expenseTimeline(
            sampleExpense(
                status = ExpenseStatus.PAID,
                approvedAt = "2026-11-03T18:04:00Z",
                paidAt = "2026-11-04T09:00:00Z",
            ),
        )

        assertEquals(listOf("Gasto creado", "Aprobado", "Pago"), steps.map { it.title })
        assertTrue(steps.all { it.state == ExpenseStepState.Done })
        assertNull(steps.last().detail?.takeIf { it.isBlank() })
    }

    @Test
    fun `un gasto rechazado cierra la linea en rojo`() {
        val steps = expenseTimeline(
            sampleExpense(
                status = ExpenseStatus.REJECTED,
                approvedAt = "2026-11-03T18:04:00Z",
            ),
        )

        assertEquals(listOf("Gasto creado", "Rechazado"), steps.map { it.title })
        assertEquals(ExpenseStepState.Rejected, steps.last().state)
    }

    @Test
    fun `el detalle del creador usa el nombre del solicitante`() {
        val steps = expenseTimeline(
            sampleExpense(status = ExpenseStatus.PENDING, requestedByName = "Mariana González"),
        )

        assertTrue(steps.first().detail?.contains("Mariana González") == true)
    }

    @Test
    fun `sin nombre de solicitante el detalle no queda vacio`() {
        val steps = expenseTimeline(sampleExpense(status = ExpenseStatus.PENDING, requestedByName = null))

        assertTrue(!steps.first().detail.isNullOrBlank())
    }
}

internal fun sampleExpense(
    status: ExpenseStatus,
    approvedAt: String? = null,
    approvedByName: String? = null,
    paidAt: String? = null,
    requestedByName: String? = null,
): ExpenseResponseDto = ExpenseResponseDto(
    id = "exp-1",
    bondId = "bond-1",
    categoryId = "cat-1",
    categoryName = "Salud",
    concept = "Consulta pediátrica",
    description = "Control de rutina",
    amount = 7_200.0,
    expenseDate = "2026-11-03",
    splitRatioPayer = 50,
    splitRatioOther = 50,
    status = status,
    requestedBy = "user-a",
    requestedByName = requestedByName,
    counterpartId = "user-b",
    counterpartName = "Ángelo Pérez",
    approvedBy = approvedAt?.let { "user-b" },
    approvedByName = approvedByName,
    approvedAt = approvedAt,
    paidAt = paidAt,
    createdAt = "2026-11-03T10:12:00Z",
    updatedAt = "2026-11-03T10:12:00Z",
)
