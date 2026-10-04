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
    val status: String,           // "created"|"assigned"|"assisting"|"in_progress"|"verify"|"done"|"overdue"|"not_assisting"|"cancelled"
    val categoryId: String,       // UUID
    val criticality: String,      // "critical" | "high" | "medium" | "low"
    val scheduledStart: String? = null, // ISO-8601
    val scheduledEnd: String? = null,   // ISO-8601 (D14)
    val deadline: String? = null,      // ISO-8601
    val assignedTo: String? = null,    // user UUID
    val assignedConfirmed: Boolean = false, // flag-only: confirmar asignación no toca status
    val confirmedAt: String? = null,   // ISO-8601, null mientras assignedConfirmed=false
    val completedAt: String? = null,   // ISO-8601 (D14)
    // 🔴 R1 · Los TRES flags los calcula el SERVIDOR para el usuario que pregunta
    // (`ActivityPolicyService.evaluate`). El cliente NO los recalcula — a diferencia
    // de `canConfirm` más abajo, que sí es una regla local. Si el cliente los
    // derivara, habría dos copias de la política y divergirían en cuanto cambiara
    // una; con `false` por defecto, un servidor viejo degrada a "sin acciones" en
    // vez de a "acciones que van a fallar".
    val canDelete: Boolean = false,
    val canCancel: Boolean = false,
    val canDecline: Boolean = false,
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
    val canDelete: Boolean = false,
    val canCancel: Boolean = false,
    val canDecline: Boolean = false,
    /** `now >= coalesce(scheduledEnd, scheduledStart)` — sólo lo manda el detalle. */
    val isPast: Boolean = false,
    /**
     * A3 · Motivo de "No asistir", visible para TODA la actividad del vínculo y no
     * sólo para el creador. Que no cruce de vínculos lo garantiza el servidor (404
     * al no miembro), no este campo: si llega aquí, ya es del vínculo del usuario.
     */
    val declinedReason: String? = null,
) {
    /** Código corto derivado del id (el backend no expone un campo short-id). */
    val code: String get() = "ACT-" + id.take(4).uppercase()
}

/**
 * A3 · El motivo de "No asistir" que la hoja de detalle pinta, o `null` si no pinta nada.
 *
 * 🔴 **SIN PARÁMETRO DE VISOR, Y ESO ES LA APLICACIÓN DE A3.** El motivo lo ve HOY CUALQUIER
 * MIEMBRO DEL VÍNCULO, no sólo quien lo escribió. La forma de hacer esa regla imposible de
 * romper por descuido es no ofrecer por dónde meterla: aquí no hay `createdBy`, ni
 * `currentUserId`, ni `isViewer` que comparar. Añadirlos "por si acaso" es exactamente el
 * cambio que devolvería el motivo a verse sólo para quien lo escribió, así que la firma es
 * también la prueba.
 *
 * ⚠ NO filtra por vínculos ni memoriza nada. La garantía es del servidor (404 al no
 * miembro) y de la vida del `DetailUiState.Content` que la envuelve: cuando la hoja se
 * cierra, `detail` vuelve a `Closed` y el motivo se va con él. Aquí sólo se lee el valor
 * que ya está en la actividad cargada — ni `remember`, ni caché, ni `DataStore`.
 *
 * ⚠ Un motivo en blanco NO se pinta. El backend lo recorta y mide antes de guardar
 * (3–200), así que sólo un servidor viejo o un dato importado podrían dejarlo vacío; pintar
 * una tarjeta con el rótulo y el cuerpo en blanco es peor que no pintar la tarjeta.
 */
fun ActividadDetalle.visibleDeclinedReason(): String? =
    declinedReason?.trim()?.takeIf { it.isNotEmpty() }

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

// ═══════════════════════════════════════════════════════════════════════════
// Contratos de las acciones de swipe (R7)
// ═══════════════════════════════════════════════════════════════════════════

/** `DELETE /activities/:id` → `{id, deletedAt}`. */
@Serializable
data class DeleteActivityResponse(
    val id: String,
    val deletedAt: String,
)

/** `POST /activities/:id/restore` → `{id, deletedAt}` (null tras deshacer). */
@Serializable
data class RestoreActivityResponse(
    val id: String,
    val deletedAt: String? = null,
)

/** `POST /activities/:id/cancel` → `{id, status, cancelledAt}`. */
@Serializable
data class CancelActivityResponse(
    val id: String,
    val status: String,
    val cancelledAt: String,
)

/** `POST /activities/:id/decline` → `{id, status, declinedAt}`. */
@Serializable
data class DeclineActivityResponse(
    val id: String,
    val status: String,
    val declinedAt: String,
)

/** `POST /activities/:id/undo-decline` → `{id, status}`. */
@Serializable
data class UndoDeclineResponse(
    val id: String,
    val status: String,
)

/** Cuerpo de cancelar y de declinar. A1 · el motivo es OBLIGATORIO en ambos. */
@Serializable
data class ActivityReasonRequest(
    val reason: String,
)
