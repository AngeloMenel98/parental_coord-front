package com.parental.shared.feature.activities.ui.swipe

import com.parental.shared.feature.activities.domain.model.Actividad

/**
 * R2 · Ventana de deshacer, en milisegundos: **5000 ms, límite INCLUSIVO**.
 *
 * ⚠ El valor está duplicado aquí y en el backend a propósito, pero NO es el móvil quien
 * manda. El backend usa su propio reloj (`now()` en SQL) y devuelve
 * `ACTIVITY_UNDO_WINDOW_EXPIRED` (409) cuando se pasó. Este número sirve para dos cosas
 * muy distintas y hay que no confundirlas:
 *  1. **UX**: cuánto texto de "Deshacer" se le enseña al usuario. Que no coincida con el
 *     servidor sólo produce un botón que ya no sirve, y eso lo resuelve el 409.
 *  2. **Cliente**: dejar de ofrecer un botón inútil. Ni una cosa ni otra son autoridad.
 *
 * Si el backend cambiara la ventana, este `const` quedaría desfasado y lo correcto sería
 * que la API lo mandara en la respuesta de la acción. Está duplicado porque hoy no lo
 * hace, y por eso hay un comentario que lo dice.
 */
const val SWIPE_UNDO_WINDOW_MS = 5_000L

/** Etiqueta del botón "Deshacer" del snackbar — R2. */
const val SWIPE_UNDO_LABEL = "Deshacer"

/**
 * R2 · Oferta de deshacer viva, con la fila que se quitó de la lista.
 *
 * ⚠ `previous` guarda la actividad COMPLETA, no un id. Al deshacer hay que reinsertar
 * exactamente el objeto que se retiró —si sólo se guardara el id habría que refetchear
 * el detalle, y la ventana es de 5000 ms: no da tiempo a una ida y vuelta. Es también la
 * razón de que el estado no sea un simple `String?`.
 *
 * @property expiresAtEpochMillis instante en que la oferta deja de ser honesta. Se guarda
 *   como tiempo ABSOLUTO y no como un contador restante porque un contador se desincroniza
 *   en cuanto la app pasa a segundo plano y el proceso se congela; el instante se recalcula
 *   desde el reloj en cada consulta.
 */
data class UndoOffer(
    val id: String,
    val action: SwipeAction,
    val previous: Actividad,
    val expiresAtEpochMillis: Long,
) {
    /**
     * Milisegundos que quedan, o `0` si ya venció.
     *
     * ⇦ `coerceAtLeast(0)` en vez de devolver negativo: un valor negativo se colaría en un
     * `if (remaining > 0)` que por un `>=` mal puesto dejaría el botón visible para siempre.
     */
    fun remainingMillis(nowEpochMillis: Long): Long =
        (expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(0L)

    /** ¿Sigue siendo honesto ofrecer "Deshacer"? Límite inclusivo (R2). */
    fun isLive(nowEpochMillis: Long): Boolean = nowEpochMillis <= expiresAtEpochMillis
}

/** Texto del snackbar para una acción recién aplicada. */
fun UndoOffer.successMessage(): String = "Actividad ${action.pastTense}"