package com.parental.shared.feature.activities.domain.model

import kotlinx.serialization.Serializable

/**
 * Modelo del contrato real del backend (GET /activities/bond/:bondId).
 * CamelCase, categoryId es UUID, enums en minúsculas, fechas ISO-8601 nullable.
 * No requiere @SerialName: los nombres de propiedad coinciden con las claves JSON.
 */
@Serializable
data class Actividad(
    val id: String,
    val title: String,
    val type: String,             // "event" | "obligation"
    val status: String,           // "created" | "assigned" | "in_progress" | "verify" | "done" | "overdue"
    val categoryId: String,       // UUID
    val criticality: String,      // "critical" | "high" | "medium" | "low"
    val scheduledStart: String? = null, // ISO-8601
    val scheduledEnd: String? = null,   // ISO-8601 (D14)
    val deadline: String? = null,      // ISO-8601
    val assignedTo: String? = null,    // user UUID
    val assignedConfirmed: Boolean = false, // flag-only: confirmar asignación no toca status
    val confirmedAt: String? = null,   // ISO-8601, null mientras assignedConfirmed=false
    val completedAt: String? = null,   // ISO-8601 (D14)
)

@Serializable
data class ActividadDetalle(
    val id: String,
    val title: String,
    val type: String,
    val status: String,
    val categoryId: String,
    val criticality: String,
    val description: String = "",
    val scheduledStart: String? = null,
    val scheduledEnd: String? = null,   // ISO-8601 (D14)
    val deadline: String? = null,
    val assignedTo: String? = null,
    val createdBy: String,
    val createdAt: String,
    val assignedConfirmed: Boolean = false,
    val confirmedAt: String? = null,
    val completedAt: String? = null,   // ISO-8601 (D14)
) {
    /** Código corto derivado del id (el backend no expone un campo short-id). */
    val code: String get() = "ACT-" + id.take(4).uppercase()
}

/** Respuesta exacta de POST /activities/:id/confirm (R4: exactamente 3 claves). */
@Serializable
data class ConfirmAssignmentResponse(
    val id: String,
    val assignedConfirmed: Boolean,
    val confirmedAt: String? = null,
)

/**
 * Regla de visibilidad EXACTA (R10): el affordance de confirmar se muestra
 * IFF assignedTo == currentUserId && !assignedConfirmed. Un único lugar
 * para la regla — usada por Actividad y ActividadDetalle.
 */
internal fun canConfirmAssignment(
    assignedTo: String?,
    assignedConfirmed: Boolean,
    currentUserId: String?,
): Boolean = assignedTo == currentUserId && !assignedConfirmed

fun Actividad.canConfirm(currentUserId: String?) =
    canConfirmAssignment(assignedTo, assignedConfirmed, currentUserId)

fun ActividadDetalle.canConfirm(currentUserId: String?) =
    canConfirmAssignment(assignedTo, assignedConfirmed, currentUserId)

@Serializable
data class CreateActividadRequest(
    val title: String,
    val description: String? = null,
    val type: String = "event",
    val categoryId: String,
    val assignedTo: String? = null,
    val scheduledStart: String,
    val scheduledEnd: String? = null,
    val childrenIds: List<String>,
)

/** DTO mínimo: `ignoreUnknownKeys = true` en plataformas lo hace seguro. */
@Serializable
data class CreatedActividad(
    val id: String,
    val title: String,
    val status: String,
)

@Serializable
data class CompleteActivityResponse(
    val id: String,
    val status: String,
    val completedAt: String? = null,
)