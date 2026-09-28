package com.parental.shared.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Tokens de espaciado y geometría — cambio `calendar-design-redesign`, tema T4.
 *
 * REGLA DE ESTE ARCHIVO (DD-4 / R5-T4-5): es ADITIVO. Cada valor reproduce
 * exactamente un literal `.dp` que ya existe en el código; este archivo sólo le
 * pone nombre. Ningún valor cambia aquí. T4 es un refactor probadamente de
 * delta visual cero.
 *
 * `Color.kt` es el único propietario de literales hex; este archivo no declara
 * colores, radios ni estilos tipográficos — ni los declarará.
 */

/**
 * Escala de espaciado. La escala es **4 / 6 / 8 / 12 / 16 / 24**.
 *
 * 6dp es un peldaño genuino de la escala, no deuda heredada: los call-sites
 * `:806` y `:910` de `ActivitiesScreen.kt` emparejan un 4dp y un 6dp en la
 * *misma* llamada, algo que un typo de 6→4 o 6→8 no podría producir. Por eso
 * **ninguna tarea de T3 o T5 normaliza un 6dp** (ERR-1 cerrado en T4-1).
 */
object Spacing {
    // ── Escala (únicos valores que el código nuevo puede usar) ──────────
    val xxs: Dp = 6.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    // ── Absorbedores heredados: nombran un literal existente. El código
    //    nuevo no debe usarlos; cada uno tiene una tarea propietaria. ───
    /** Regla de 1dp (conector de agenda, conector de nodo, divisor del esqueleto). `R6-T4-6a` — permanente. */
    val hairline: Dp = 1.dp

    /** Collar de 2dp entre puntos contiguos. `R6-T4-6f` — permanente. */
    val collar: Dp = 2.dp

    /** Relleno vertical de los estados vacíos. Propietario: T5 (`R3-T5-3`). */
    val padded: Dp = 32.dp

    /** Ancho máximo de contenido — convierte `MAX_TIMELINE_WIDTH` (Int 640). `R6-T4-6d` — permanente. */
    val maxContentWidth: Dp = 640.dp
}

/**
 * Geometría del agenda. Son **dimensiones con nombre**, no espaciado: columnas,
 * celdas, tarjetas y objetivos táctiles. Un revisor NO debe "corregir" un valor
 * a la escala de [Spacing] — cada uno pertenece a una tarea que lo cambia.
 */
object AgendaGeometry {
    /**
     * Columna de fecha de la fila de lista. Propietario del cambio: T3 (`R1-T3-1`).
     *
     * **78 → 48 (T3-1).** La anatomía de dos líneas —día encima, mes abreviado
     * debajo, ambas centradas (PRD `:77`)— no tiene dónde meter un año, y eso es
     * lo que hace satisfacible la regla del año condicional (`R2-T3-2`).
     * 48dp es la misma medida que `AgendaGeometry.timeColumn`: la única
     * dimensión de 48dp que sí vive en la escala como `Layout.touchMin` es el
     * objetivo táctil, no una columna — no "corregir" esto a un valor de escala.
     */
    val dateColumn: Dp = 48.dp

    /**
     * Nodo de línea del timeline de agenda. `R8-T4-8` — permanente.
     *
     * Declarado antes que [undatedGutter] a propósito: la sangría se **deriva**
     * de él, y los inicializadores de un `object` se ejecutan en orden de
     * declaración (T3-7).
     */
    val lineNode: Dp = 14.dp

    /**
     * Sangría del grupo sin fecha de inicio. Propietario del cambio: T3 (`R7-T3-7`).
     *
     * **88 → derivado (T3-7).** 88dp no coincidía con nada: la canalización real
     * era 78+1+12+14+12 = 117dp, así que 88 caía entre la línea y el título,
     * alineado con ninguna de las dos. Ahora se **deriva** de las partes fijas de
     * la fila —columna de fecha + línea + collar del nodo + nodo + collar— y por
     * construcción es independiente del ancho. No reescribir el 87 a mano.
     *
     * **Dueños de esta sangría (T3-7).** Los DOS encabezados "Sin fecha de
     * inicio" la consumen: `AgendaList.kt` y `WeekAgenda.kt`. El de la semana
     * vivía en `padding(top, bottom)` —su `start` era 0, así que **no** Era
     * una columna sino un rótulo pegado al borde— y ahora comparte `start` con
     * el del agenda, que es lo único que hace que se lean como una columna
     * (`prd-v4:73`, grupo de varias filas). Es el único `padding` de la fila que
     * no es de chrome.
     */
    val undatedGutter: Dp = dateColumn + Spacing.hairline + Spacing.md + lineNode + Spacing.md

    /**
     * Tarjeta de nodo de la semana. Propietario del cambio: **T2-5 / T2-6** (`OD-1`,
     * 190 → 40). T4 declara el nombre y **no toca el valor**: renombrar aquí y
     * cambiar el número en T2 es lo que mantiene a T4 en delta cero.
     */
    val nodeCardLegacy: Dp = 190.dp

    /** Ancho de la celda de día de la tarjeta de semana. Propietario del cambio: A2 (`R7-T1-7`). */
    val dayCellWidth: Dp = 52.dp

    /** Alto mínimo de la celda de día. Propietario: A2 (`R7-T1-7`). */
    val dayCellMinHeight: Dp = 76.dp

    /** Punto de categoría dentro de la celda de día. Propietario: T1-6 (7 → 8). */
    val dotLegacy: Dp = 7.dp

    /**
     * Punto de estado. Va emparejado con [dotTray]: los dos son una sola
     * geometría (8dp dentro de 12dp) y no deben cambiar por separado.
     */
    val statusBadge: Dp = 8.dp

    /** Bandeja de 12dp que aloja el punto de estado de 8dp. Propietario: T1-6. */
    val dotTray: Dp = 12.dp

    /** Columna de tiempo del agenda del día. Propietario: T2 (`R4-T2-4`). */
    val timeColumn: Dp = 48.dp

    /**
     * Objetivo táctil mínimo. Propietario del cambio: T3-8 (**44 → 48**).
     *
     * **44 → 48 (T3-8).** 48dp es el mínimo del objetivo táctil (WCAG 2.5.5 /
     * `prd-v4:130`) y ya es el valor de `Layout.touchMin` en `Type.kt` — este
     * token pasó a coincidir con la escala en vez de contradecirla. El nombre
     * `…Legacy` es histórico y **se conserva**: renombrarlo sería un
     * movimiento de renombre en 9 sitios de 5 archivos sin ganancia visual.
     *
     * Dueños: 9 consumidores en `feature/activities/` — `ActivitiesScreen.kt`,
     * `ActivityBody.kt`, `WeekOverview.kt` (x2), `WeekAgenda.kt` (x2),
     * `CalendarChrome.kt` (x3). Ninguno codifica su propio dp.
     */
    val touchTargetLegacy: Dp = 48.dp

    /**
     * Relleno vertical del chip Confirmar. Propietario: T3-8 (**10 → 8**).
     *
     * 10 + 10 = 20dp de relleno vertical sobre un chip de contenido corto daba
     * una altura propia *mayor* que `touchTargetLegacy`. Bajar el relleno a 8
     * deja que **`touchTargetLegacy` sea el que manda**: el chip crece por su
     * `heightIn(min = …)`, no por su padding. Así los dos tokens dejan de
     * competir y 48dp significa 48dp en todas partes. No volver a 10.
     */
    val chevronPad: Dp = 8.dp

    /**
     * Bloque de fecha del esqueleto de carga. Propietario: T5-6 (**64 → 48**).
     *
     * **64 → 48 (T5-6).** Igual que [dateColumn] tras T3-1, para que el esqueleto
     * previsualice el ancho que de verdad carga y la fila no dé un tirón
     * horizontal al llegar el contenido. No es un valor de escala: es la medida
     * de la columna real.
     */
    val skeletonDateBlock: Dp = 48.dp

    /** Alto del bloque de fecha del esqueleto. Se nombra junto a [skeletonDateBlock]. */
    val skeletonDateHeight: Dp = 48.dp
}

/** Grosor de trazo de un indicador circular. Un trazo no es un espaciado. */
object ProgressStroke {
    val thin: Dp = 2.dp
}

/** Geometría de la pantalla de detalle. */
object DetailGeometry {
    /** Diámetro del spinner de confirmación. `R2-T2-2` — permanente. */
    val progressSize: Dp = 18.dp
}
