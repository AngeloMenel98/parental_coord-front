package com.parental.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Gasto(
    @SerialName("id") val id: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String = "USD",
    @SerialName("category") val category: String, // "SALUD" | "EDUCACION" | "FAMILIAR" | "SOCIAL" | "RECREACION" | "OTROS"
    @SerialName("status") val status: String, // "PENDIENTE" | "APROBADO" | "RECHAZADO" | "PAGADO" | "DISPUTA"
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("submitted_by") val submittedBy: String? = null,
    @SerialName("submitted_by_name") val submittedByName: String? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("approved_by_name") val approvedByName: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GastoDetalle(
    @SerialName("id") val id: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String = "USD",
    @SerialName("category") val category: String,
    @SerialName("status") val status: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("submitted_by") val submittedBy: String? = null,
    @SerialName("submitted_by_name") val submittedByName: String? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("approved_by_name") val approvedByName: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("dispute_reason") val disputeReason: String? = null,
    @SerialName("status_history") val statusHistory: List<GastoStatusHistoryEntry> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GastoStatusHistoryEntry(
    @SerialName("from_status") val fromStatus: String,
    @SerialName("to_status") val toStatus: String,
    @SerialName("changed_by") val changedBy: String,
    @SerialName("changed_at") val changedAt: String,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class CreateGastoRequest(
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String = "USD",
    @SerialName("category") val category: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
)

@Serializable
data class GastoSummary(
    @SerialName("total_pending") val totalPending: Double = 0.0,
    @SerialName("total_approved") val totalApproved: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("total_disputed") val totalDisputed: Double = 0.0,
    @SerialName("count_pending") val countPending: Int = 0,
    @SerialName("count_approved") val countApproved: Int = 0,
    @SerialName("count_paid") val countPaid: Int = 0,
    @SerialName("count_disputed") val countDisputed: Int = 0,
    @SerialName("currency") val currency: String = "USD",
)

@Serializable
data class ApproveGastoRequest(
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class DisputeGastoRequest(
    @SerialName("reason") val reason: String,
    @SerialName("notes") val notes: String? = null,
)
