package com.parental.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Actividad(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: String, // "EVENT" | "OBLIGATION"
    @SerialName("category") val category: String, // "SALUD" | "EDUCACION" | "FAMILIAR" | "SOCIAL" | "RECREACION"
    @SerialName("status") val status: String, // "CREATED" | "ASSIGNED" | "IN_PROGRESS" | "PENDING_VERIFICATION" | "COMPLETED" | "OVERDUE" | "CANCELLED"
    @SerialName("priority") val priority: String, // "URGENT" | "HIGH" | "MEDIUM" | "LOW"
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ActividadDetalle(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: String,
    @SerialName("category") val category: String,
    @SerialName("status") val status: String,
    @SerialName("priority") val priority: String,
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("status_history") val statusHistory: List<StatusHistoryEntry> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class StatusHistoryEntry(
    @SerialName("from_status") val fromStatus: String,
    @SerialName("to_status") val toStatus: String,
    @SerialName("changed_by") val changedBy: String,
    @SerialName("changed_at") val changedAt: String,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class CreateActividadRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: String,
    @SerialName("category") val category: String,
    @SerialName("priority") val priority: String = "MEDIUM",
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("bond_id") val bondId: String? = null,
)

@Serializable
data class UpdateActividadRequest(
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("priority") val priority: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
)

@Serializable
data class UpdateStatusRequest(
    @SerialName("new_status") val newStatus: String,
    @SerialName("notes") val notes: String? = null,
)
