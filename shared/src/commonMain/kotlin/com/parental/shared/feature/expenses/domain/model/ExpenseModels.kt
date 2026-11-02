package com.parental.shared.feature.expenses.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estados de un gasto según el contrato S1/T28.
 *
 * El backend envía minúsculas (`pending`, `approved`, `rejected`, `paid`);
 * la UI los muestra como `Pendiente` / `Aprobado` / `Rechazado` / `Pagado`.
 */
@Serializable
enum class ExpenseStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("paid") PAID,
}

fun ExpenseStatus.displayName(): String = when (this) {
    ExpenseStatus.PENDING -> "Pendiente"
    ExpenseStatus.APPROVED -> "Aprobado"
    ExpenseStatus.REJECTED -> "Rechazado"
    ExpenseStatus.PAID -> "Pagado"
}

/**
 * Respuesta completa de un gasto (GET /expenses/:id y items de la lista).
 * Los nombres de campo son exactamente los del contrato S1.
 */
@Serializable
data class ExpenseResponseDto(
    val id: String,
    val bondId: String,
    val categoryId: String,
    val categoryName: String,
    val concept: String,
    val description: String? = null,
    val amount: Double,
    val expenseDate: String,
    val splitRatioPayer: Int,
    val splitRatioOther: Int,
    val status: ExpenseStatus,
    val requestedBy: String,
    val counterpartId: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val paidAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val canEdit: Boolean = false,
    val canCancel: Boolean = false,
    val canApprove: Boolean = false,
    val canReject: Boolean = false,
    val canMarkPaid: Boolean = false,
    val isTerminal: Boolean = false,
    val hasDocument: Boolean = false,
)

/**
 * Conteos por status devueltos por GET /expenses/bond/:bondId/summary.
 */
@Serializable
data class ExpenseSummaryCountsDto(
    val pendientes: Int,
    val aprobados: Int,
    val pagados: Int,
    val rechazados: Int,
)

/**
 * Resumen de saldos y conteos (GET /expenses/bond/:bondId/summary).
 * El front nunca recomputa estos valores; los muestra tal cual llegan.
 */
@Serializable
data class ExpenseSummaryDto(
    val saldo: Double,
    val porPagar: Double,
    val porCobrar: Double,
    val counts: ExpenseSummaryCountsDto,
)

/**
 * Paginación de la lista (GET /expenses/bond/:bondId).
 */
@Serializable
data class PaginatedExpensesDto(
    val items: List<ExpenseResponseDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

/**
 * Respuesta de las acciones de estado (approve / reject / pay).
 */
@Serializable
data class ExpenseActionResponse(
    val id: String,
    val status: ExpenseStatus,
)

/**
 * Cuerpo de PATCH /expenses/:id. Todos los campos son opcionales;
 * el front solo envía los que el usuario editó.
 */
@Serializable
data class ExpenseUpdateRequest(
    val concept: String? = null,
    val amount: Double? = null,
    val categoryId: String? = null,
    val expenseDate: String? = null,
    val description: String? = null,
    // SOLO la parte del solicitante: el servidor deriva la contraparte
    // (`split_ratio_other = 100 - split_ratio_payer`) y rechaza con 400
    // cualquier propiedad que no exista en su DTO.
    val splitRatioPayer: Int? = null,
)

/**
 * Cuerpo de POST /expenses/{bondId} (S6/T27).
 *
 * NO se serializa directamente: el cliente Ktor tiene `explicitNulls` activo y
 * el backend rechaza con 400 cualquier propiedad que no exista en su DTO. El
 * cuerpo se arma campo por campo en [ExpensesApi.createExpense].
 */
data class CreateExpenseRequest(
    val concept: String,
    val amount: Double,
    val categoryId: String,
    val expenseDate: String,
    val description: String? = null,
    val splitRatioPayer: Int = 50,
)

/**
 * Cuerpo opcional de POST /expenses/:id/reject.
 */
@Serializable
data class RejectExpenseRequest(
    val reason: String? = null,
)
