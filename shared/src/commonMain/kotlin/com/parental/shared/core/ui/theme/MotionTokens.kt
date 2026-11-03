package com.parental.shared.core.ui.theme

/*
 * Tokens de movimiento — `calendar-design-redesign`, T5-4 (`R4-T5-4`).
 *
 * UNA duración, un nombre. `grep -rn "tween(\|spring(" …/feature/activities/` no
 * puede encontrar un entero crudo: el número vive aquí y en ningún otro sitio.
 *
 * ⚠ DD-8 — el alcance de esto es DELIBERADAMENTE estrecho, y el conflicto con la
 * spec está en el registro, no escondido (ERR-4). `R4-T5-4` nombra cuatro
 * animaciones — `animateColorAsState` en la celda, `animateItem()` en los items,
 * y un esqueleto con brillo — y `R6-T5-6` dice que el esqueleto "SHALL" brillar.
 * El diseño entrega DOS `Crossfade` y un esqueleto ESTÁTICO. La razón está
 * anotada: la señal de movimiento reducido de OI-3 es inalcanzable desde
 * `commonMain`/`androidMain` congelados, y el brillo es justamente la animación
 * larga. La implementación sigue al DISEÑO, que es dueño del CÓMO. El cuerpo del
 * PR debe decir que (i), (iii), (iv) y la mitad del brillo de `R6-T5-6` quedan
 * FUERA por DD-8, pendientes de una enmienda de la spec. Es una entrega parcial
 * marcada, nunca cerrada en silencio.
 *
 * Por lo mismo, aquí NO hay token de "movimiento reducido": escribir uno al que
 * nada puede dar valor sería_documento_, no comportamiento.
 */

/** Tokens de movimiento. */
object MotionTokens {
    /**
     * Duración estándar de una transición — **150 ms**.
     *
     * Escala de 150–400ms de Material: 150 es el piso de "corta", que es lo
     * correcto para un cambio de contenido que sustituye a otro. Con 150ms una
     * transición desaparece tan rápido que el ojo la lee como un cambio y no como
     * una animación; y es lo bastante corta para que un screen reader no la
     * perciba como un retraso.
     */
    val durationStandard: Int = 150
}
