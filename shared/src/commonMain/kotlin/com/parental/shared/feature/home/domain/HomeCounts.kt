package com.parental.shared.feature.home.domain

import com.parental.shared.feature.activities.domain.model.Actividad

/**
 * Los dos conteos del Home (T20).
 *
 * R11, textual: "Both counts are derived **client-side** from the already-loaded
 * bond activity list (the list is per-bond and small)."
 *
 * Cada campo es nullable a propósito: `null` significa "NO SE PUDO CALCULAR" (la
 * lista no llegó, o no hay forma de saber a quién son las asignadas) y la tarjeta lo
 * muestra como no disponible. Un `0` inventado es peor que un hueco: el usuario lee
 * el 0 como un dato ("no tenés ninguna") cuando en realidad no se sabe.
 */
internal data class HomeCounts(
    val assignedToMe: Int?,
    val overdue: Int?,
)

/**
 * Los estados que ya NO cuentan como carga pendiente.
 *
 * `done` y `cancelled` son terminales (R3). `not_assisting` NO es terminal: el que la
 * rechazó la tiene asignada y sigue siendo carga de alguien — es justamente el
 * estado desde el que se reasigna (T9).
 *
 * ⚠ No es el mismo conjunto que el `TERMINAL_STATUSES` de la hoja de detalle: aquel
 * incluye `not_assisting` porque "No asistir" no tiene sentido en una actividad
 * vencida o ya cerrada, que es otra pregunta. Dos conjuntos con dos nombres
 * distintos: una lista de estados terminal copiada dos veces diverge sola.
 */
private val FINISHED_STATUSES = setOf("done", "cancelled")

/**
 * Calcula los conteos del Home sobre la lista del vínculo.
 *
 * ⚠ Las dos cuentas son DEL USUARIO, no del vínculo: el Home es un panel personal y
 * el saldo ya viene firmado desde el punto de vista de quien pregunta (R1). "Asignadas"
 * son las que están asignadas a MÍ y siguen abiertas; "Vencidas" son las que están
 * asignadas a MÍ y llegaron a `overdue` — la parte tardía de la misma carga, no un
 * número ajeno.
 *
 * @param activities la lista del vínculo, o `null` si no se pudo cargar.
 * @param currentUserId el usuario que pregunta, o `null` si todavía no se conoce
 *   (la sesión legacy lo recuperó por `GET /auth/me`): sin él no se puede contar
 *   "asignadas a mí" y el campo queda en `null` en vez de `0`.
 */
internal fun computeHomeCounts(
    activities: List<Actividad>?,
    currentUserId: String?,
): HomeCounts {
    if (activities == null) {
        return HomeCounts(assignedToMe = null, overdue = null)
    }
    val assignedToMe = currentUserId?.let { userId ->
        activities.count { it.assignedTo == userId && it.status !in FINISHED_STATUSES }
    }
    val overdue = currentUserId?.let { userId ->
        activities.count { it.assignedTo == userId && it.status == "overdue" }
    }
    return HomeCounts(assignedToMe = assignedToMe, overdue = overdue)
}
