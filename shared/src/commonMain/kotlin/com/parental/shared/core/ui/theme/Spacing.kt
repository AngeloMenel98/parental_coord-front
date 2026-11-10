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

    /**
     * Nodo del timeline de día — **T2-5 / T2-6** (`OD-1`, 190 → 40).
     *
     * **190 → 40 (T2-5).** Es el "timeline de nodos" real (B2), no una tarjeta.
     * [nodeCardLegacy] **no se borra**: es la casa de 190dp que T4 dejó intacta a
     * propósito, y T4 vuelve a ella si C9 se confirma como "sin título". Los dos
     * tokens conviven; el legacy no se toca ni se reescribe.
     */
    val nodeCard: Dp = 40.dp

    /**
     * Punto de categoría dentro de la celda de día. `R6-T1-6` — **7 → 8**.
     *
     * 8dp es el mínimo de indicador de Material y el suelo de legibilidad: por
     * debajo degrada el contraste no textual (WCAG 1.4.11) justo en el viewport
     * más estrecho. `R6-T1-6` prohíbe expresamente encogerlo para que quepa.
     */
    val dotLegacy: Dp = 8.dp

    /**
     * Punto de estado. 8dp. `R6-T2-6`.
     *
     * ⚠ KDoc corregido en T1-6 (`OQ-A2-3`): antes decía ir emparejado con
     * [dotTray] como "8dp dentro de 12dp". Es FALSO y nunca lo fue — [dotTray]
     * es el `padding` de la tarjeta y este punto va en la insignia de la fila
     * (`ActivityBody.kt:160`, `DayAgenda.kt:285,292`). No son una geometría.
     */
    val statusBadge: Dp = 8.dp

    /**
     * Relleno INTERIOR de la tarjeta de semana — 12dp. Conectado desde T4-7.
     * Propietario del cambio: T1-6.
     *
     * ⚠ El nombre engaña; el KDoc anterior ("bandeja que aloja el punto de 8dp")
     * describía una bandeja que NADA dibuja. Los 12dp son el `padding` de esta
     * tarjeta y el 2.º término de la cadena de 56dp de la que depende toda la
     * tabla de anchos (2 × 12 = 24). La bandeja real del punto de día la dibuja
     * T1-6 con `Spacing.xs`/`collar`, NO con este token: cambiarlo rompe la cadena.
     */
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

/**
 * Geometría del RIEL del tiempo — T2/T3 de `odd/tasks/activity-rail.md` (S3, S11).
 *
 * A diferencia de [AgendaGeometry], estos valores NO nombran literales que ya existían: son
 * la medida del artboard `design/activity-calendar-ui/03-riel-del-tiempo.html` traducida a
 * dp. Por eso viven en su propio objeto y no se “absorben” en la escala de [Spacing].
 *
 * 🔴 AQUÍ NO HAY COLORES. La paleta de la canaleta, del bloque y de la línea de “ahora” sale
 * de `MaterialTheme.colorScheme` en el call-site; `Color.kt` sigue siendo el único dueño de
 * los literales hex y este archivo no declara ninguno.
 */
object RailGeometry {
    /**
     * Alto de una hora de eje — 76f. Es `PXH` del artboard (`:264`), la unidad de la que
     * cuelgan la altura proporcional del bloque y la posición de cada tick.
     *
     * **Es `Float` porque así lo pide `railOffsetDp`/`railHeightDp`**: la escala
     * minutos→dp de `RailDerivation.kt` opera en `Float` y convertir a `Dp` en cada tick
     * añadiría una ronda de redondeo por hora a lo largo de un eje de 14 horas.
     */
    val DpPerHour: Float = 76f

    /**
     * Alto MÍNIMO de un bloque — 48dp. El piso de toque de S11: una actividad de 20
     * minutos mide 25,3dp por proporción, y un bloque de esa altura no se puede tocar.
     *
     * 48dp es el mismo valor de [AgendaGeometry.touchTargetLegacy] y del `heightIn` de
     * `ActivityBody`; NO se deriva de ahí porque [AgendaGeometry] no es su dueño.
     */
    val MinBlockHeight: Dp = 48.dp

    /**
     * Alpha del tinte de fondo de un bloque sobre `surface` — 0.14f.
     *
     * El fondo NO sale de `CategoryInfo.colorHex` ni del `container` del triple: cuando el
     * servidor manda un hex, `container == content == dot` (`TagColors.kt`) y el título
     * quedaba del mismo color que su propio fondo. Un tinte del `dot` al 14% siempre compone
     * un fondo claro sobre `surface`, así que el título va en `onSurface` y el bloque se lee
     * con cualquier hex, sin depender de qué rama del resolver ganó.
     */
    val BlockTintAlpha: Float = 0.14f

    /**
     * Alpha de la línea de media hora sobre `outlineVariant` — 0.5f.
     *
     * La media hora tiene que ser MÁS sutil que la hora sin ser otro color: el artboard lo
     * resuelve con un `rgba()` de la misma tinta (`:90`), que en Compose es el mismo rol con
     * alpha. Por debajo de 0.35 el trazo de 1dp desaparece en pantallas de baja densidad.
     */
    val HalfHourLineAlpha: Float = 0.5f

    /**
     * Separación entre carriles solapados — 4dp. Es el hueco de 4px del artboard (`:372`):
     * sin él dos bloques del mismo grupo se leen como uno solo.
     */
    val LaneGap: Dp = 4.dp

    /**
     * Riel de categoría dentro del bloque — 4dp de ancho.
     *
     * El artboard lo llama `urow .rail` (`:147`) y es el ancla de identidad de la categoría
     * dentro del bloque; el fondo sólo da el tono.
     */
    val CategoryRail: Dp = 4.dp

    /** Grosor de la línea de “ahora” — 2dp, como `.now` del artboard (`:119`). */
    val NowLineStroke: Dp = 2.dp

    /**
     * Borde de bloque vencido — 1,5dp en `error`, como `.pill.late` (`:118`).
     *
     * Es 1,5 y no 1 porque comparte bloque con el riel de 4dp y con el borde redondeado de
     * 12dp: a 1dp el contorno se pierde entre el antialiasing de la esquina.
     */
    val OverdueBorderWidth: Dp = 1.5.dp

    /**
     * Alpha de un bloque `Done` — 0.62f, el `.pill.done` del artboard (`:115`).
     *
     * ⚠ No es la ÚNICA señal de “cumplida”: el título va tachado y `StatusTag` dice el
     * estado en palabras (S11 — el estado nunca se codifica sólo con color u opacidad).
     */
    val DoneAlpha: Float = 0.62f

    /**
     * Umbral de bloque CORTO — 64dp, `shortPill` del artboard (`:375`).
     *
     * Por debajo, el bloque pierde la fila de chip y muestra `hora · estado` en una sola
     * línea: a 64dp un chip de 24dp no cabe junto a título y hora. El estado sigue siendo
     * una PALABRA (`statusSemanticLabel`), nunca sólo el punto de color.
     */
    val ShortBlockHeight: Dp = 64.dp

    /**
     * Altura mínima para dibujar la fila de chip de estado — 76dp.
     *
     * Derivación, no un número inventado: título `titleSmall` (20) + hueco (4) + rango
     * `TypeTokens.time` (16) + hueco (4) + `StatusTag` (16 de línea + 8 de relleno = 24) +
     * relleno vertical del bloque (8) = **76**. Un bloque de entre 64 y 76dp conserva el
     * rango con la duración y NO dibuja chip: pintarlo lo dejaría cortado por el clip.
     */
    val ChipRowMinHeight: Dp = 76.dp
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

/**
 * Tope de altura de las HOJAS MODALES, como fracción de la ventana.
 *
 * Propietarios: `activity-detail-modal` (`ActividadDetailSheet.kt`) y `activity-create-modal`
 * (`CreateActivitySheet.kt`). Antes vivía como constante privada en el primer fichero: dos
 * hojas con el mismo tope en dos sitios es una deriva esperando a ocurrir.
 *
 * 🔴 NO se "arregla" con `skipPartiallyExpanded = false`: da el 50 % por cero líneas y rompe
 * el pie fijo (la anclura `PartiallyExpanded` es una POSICIÓN, no un redimensionado). El
 * `ModalBottomSheet` tampoco expone altura máxima y `Modifier.draggableAnchors` es `internal`,
 * así que el único tope posible es de CONTENIDO y tiene que leer la ventana vigente
 * (`BoxWithConstraints.maxHeight`) — rotación, plegable, ventana dividida y teclado abierto.
 *
 * Propietario del cambio: `create-activity` T-FE-15.
 */
object SheetGeometry {
    val maxHeightRatio: Float = 0.85f
}
