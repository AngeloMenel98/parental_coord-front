package com.parental.shared.feature.activities.ui.swipe

import com.parental.shared.feature.activities.domain.model.Actividad

/**
 * Las TRES acciones de swipe (R7).
 *
 * @property requiresReason A1 · cancelar y declinar llevan motivo OBLIGATORIO; eliminar no.
 *   Vive en el enum y no en cada `when` para que añadir una cuarta acción no pueda
 *   olvidarse de esta decisión: el diálogo se abre **si** `requiresReason`.
 * @property pastTense Etiqueta del snackbar tras el éxito ("Actividad cancelada" vs
 *   "eliminada"): el género y el participio cambian por acción y mantenerlos en un
 *   `when` de la pantalla era una fuente de copy-paste.
 */
enum class SwipeAction(
    val label: String,
    val requiresReason: Boolean,
    val pastTense: String,
) {
    DELETE("Eliminar", requiresReason = false, pastTense = "eliminada"),
    CANCEL("Cancelar", requiresReason = true, pastTense = "cancelada"),
    DECLINE("No asistir", requiresReason = true, pastTense = "declinada"),
    ;

    /** Verbo del diálogo de motivo, para el botón de confirmar. */
    val confirmLabel: String get() = if (requiresReason) "$label con motivo" else label
}

/**
 * Acciones ofrecidas para [actividad], en el ORDEN en que deben mostrarse.
 *
 * 🔴 R1 · Esto **NO recalcula la política**: lee los tres `can_*` que el SERVIDOR manda
 * (`ActivityPolicyService.evaluate` corre en el backend, por el usuario que pregunta).
 * Derivarlos aquí crearía una segunda copia de las reglas —rol del creador, rol del
 * asignado, `isPast`— que divergiría de la del servidor en cuanto cambiara una, y el
 * fallo sería del tipo peor: la UI ofrece "Eliminar" y la petición recibe 403.
 *
 * El `false` por defecto de los flags en el modelo es lo que hace esto seguro frente a
 * un backend viejo: degrada a "sin acciones", nunca a "acciones que van a fallar".
 *
 * El orden (Cancelar, No asistir, Eliminar) pone lo reversible antes que lo
 * destructivo: "Eliminar" es la única sin deshacer más de 5000 ms.
 */
fun availableSwipeActions(actividad: Actividad): List<SwipeAction> = buildList {
    if (actividad.canCancel) add(SwipeAction.CANCEL)
    if (actividad.canDecline) add(SwipeAction.DECLINE)
    if (actividad.canDelete) add(SwipeAction.DELETE)
}

/** `true` si [actividad] admite alguna acción — evita pintar un menú vacío. */
fun Actividad.hasSwipeActions(): Boolean =
    canCancel || canDecline || canDelete

/**
 * A1 · Motivo obligatorio: 3–200 caracteres **tras `trim()`**.
 *
 * 🔴 El trim ANTES de medir es la parte que no es opcional. Sin él, `"  a  "` pasa la
 * comprobación de longitud con 5 caracteres y el usuario ve un diálogo que el servidor
 * rechaza con 422; peor, `"        "` (8 espacios) también. El backend mide igual —
 * es la misma regla a ambos lados a propósito.
 *
 * Esto NO sustituye la validación del servidor: acorta el viaje vacío, nada más. Si el
 * backend cambiara el rango, esta función quedaría desfasada y seguiría siendo el
 * servidor quien decide — por eso el error del 422 se muestra tal cual.
 */
sealed interface ReasonValidation {
    data object Valid : ReasonValidation
    /** @param actual longitud ya recortada, para que el mensaje sea específico. */
    data class TooShort(val actual: Int) : ReasonValidation
    data class TooLong(val actual: Int) : ReasonValidation
    data object Blank : ReasonValidation
}

const val REASON_MIN_LENGTH = 3
const val REASON_MAX_LENGTH = 200

fun validateSwipeReason(raw: String): ReasonValidation {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> ReasonValidation.Blank
        trimmed.length < REASON_MIN_LENGTH ->
            ReasonValidation.TooShort(trimmed.length)

        trimmed.length > REASON_MAX_LENGTH ->
            ReasonValidation.TooLong(trimmed.length)

        else -> ReasonValidation.Valid
    }
}

/** Texto de un [ReasonValidation] para el diálogo. */
fun ReasonValidation.message(): String = when (this) {
    ReasonValidation.Valid -> ""
    ReasonValidation.Blank -> "Escribe un motivo"
    is ReasonValidation.TooShort ->
        "El motivo necesita al menos $REASON_MIN_LENGTH caracteres (llevas $actual)"

    is ReasonValidation.TooLong ->
        "El motivo admite hasta $REASON_MAX_LENGTH caracteres (llevas $actual)"
}

/** Corta a [REASON_MAX_LENGTH] para que el diálogo no deje enviar un texto imposible. */
fun String.trimmedReason(): String = trim().take(REASON_MAX_LENGTH)