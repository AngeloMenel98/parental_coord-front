package com.parental.shared.feature.expenses.domain

import com.parental.shared.core.util.formatShortDate
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import kotlinx.datetime.LocalDate

/** Estado visual de un paso de la trazabilidad. */
internal enum class ExpenseStepState { Done, Current, Rejected }

/** Un paso de la línea temporal del detalle. */
internal data class ExpenseTimelineStep(
    val title: String,
    val detail: String?,
    val state: ExpenseStepState,
)

/**
 * S5 · la trazabilidad como LÍNEA TEMPORAL que sólo suma pasos ocurridos.
 *
 * El artboard lo dice explícitamente: "cada instancia va apareciendo a medida que se va
 * realizando, no existe desde un primer momento". Por eso no hay un paso de pago con guion
 * cuando el gasto sigue pendiente: un paso futuro no se dibuja. Si el gasto está esperando algo,
 * se agrega UN paso actual (`Current`), que es el único que puede no tener fecha.
 *
 * El orden es el del ciclo real: creado → aprobado → pagado; el rechazo cierra la línea y no
 * arrastra una aprobación previa.
 */
internal fun expenseTimeline(expense: ExpenseResponseDto): List<ExpenseTimelineStep> {
    val steps = mutableListOf(
        ExpenseTimelineStep(
            title = "Gasto creado",
            detail = joinDetail(
                shortDate(expense.createdAt),
                expense.requestedByName?.takeIf { it.isNotBlank() },
            ),
            state = ExpenseStepState.Done,
        ),
    )

    if (expense.status == ExpenseStatus.REJECTED) {
        steps += ExpenseTimelineStep(
            title = "Rechazado",
            detail = "El gasto se rechazó.",
            state = ExpenseStepState.Rejected,
        )
        return steps
    }

    expense.approvedAt?.let { approvedAt ->
        steps += ExpenseTimelineStep(
            title = "Aprobado",
            detail = joinDetail(
                shortDate(approvedAt),
                expense.approvedByName?.takeIf { it.isNotBlank() },
            ),
            state = ExpenseStepState.Done,
        )
    }

    expense.paidAt?.let { paidAt ->
        steps += ExpenseTimelineStep(
            title = "Pago",
            detail = joinDetail("Pagado el", shortDate(paidAt)),
            state = ExpenseStepState.Done,
        )
    }

    val waiting = when {
        expense.status == ExpenseStatus.PENDING -> "Esperando aprobación"
        expense.status == ExpenseStatus.APPROVED -> "Esperando pago"
        else -> null
    }
    if (waiting != null) {
        steps += ExpenseTimelineStep(title = waiting, detail = null, state = ExpenseStepState.Current)
    }

    return steps
}

private fun joinDetail(vararg parts: String?): String? =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }

/** `2026-11-03T10:12:00Z` o `2026-11-03` → `3 nov 2026`. Nunca inventa una fecha. */
private fun shortDate(raw: String?): String? {
    val dateOnly = raw?.trim()?.take(10)?.takeIf { it.length == 10 } ?: return null
    val parsed = runCatching { LocalDate.parse(dateOnly) }.getOrNull() ?: return null
    return formatShortDate(parsed)
}
