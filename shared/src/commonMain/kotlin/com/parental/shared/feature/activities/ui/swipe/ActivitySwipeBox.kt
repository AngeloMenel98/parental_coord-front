package com.parental.shared.feature.activities.ui.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fracción del ancho que hay que recorrer para COMPROMETER el gesto y no devolverlo solo.
 *
 * ⚠ No es un parámetro: es geometría de la interacción, no una opción de quien llama.
 * Si cada pantalla pasara su propio umbral, dos pantallas mostrarían la misma acción a
 * recorridos distintos y el usuario tendría que reaprender el gesto en cada una.
 */
private const val COMMIT_THRESHOLD = 0.35f

/** Duración del retorno al reposo. Corta a propósito: el gesto ya tardó en decidirse. */
private val SettleSpec = tween<Float>(durationMillis = 200)

/**
 * R2-T2-7 · Capa de swipe de UNA actividad.
 *
 * Se monta en el PUNTO DE RENDER de cada fila y RECIBE el contenido en vez de dibujarlo:
 * es un envoltorio de slot, como `SwipeToDismissBox`. Si pintara la tarjeta por su cuenta
 * habría dos copias de la misma fila —una dentro del gesto y otra sin él— y la de dentro
 * nunca recibiría los datos de la agenda.
 *
 * 🔴 TRES MONTajes, y este es el único sitio donde se eligen. `WeekAgenda.kt` lo envuelve
 * sobre la llamada a `DayAgendaRow` (filas con hora) y sobre la de `AgendaTimelineRow`
 * (filas sin fecha); `AgendaList.kt` lo pasa a `AgendaTimelineRow` (modo lista). Antes sólo
 * existía el primero, y montarlo sobre una fila equivocada fue el defecto que encontró
 * `sdd-verify`: una superficie sin gesto no se ve, se compila.
 *
 * ⚠ POR QUÉ EL EJE CRUZADO SE DECIDE AQUÍ Y NO SIEMPRE ES `fillMaxSize`. `fillMaxSize`
 * necesita que el alto de ENTRADA esté acotado. Dentro del hueco de una fila
 * (`Box(weight(1f))` en un `Row(height(IntrinsicSize.Min))`) lo hay, y ahí `fillMaxSize` es
 * lo correcto. Envolviendo la fila ENTERA desde un `Column` de un `LazyColumn` el alto llega
 * infinito, y `fillMaxSize` acabaría midiendo con `Constraints.fixed(0, 0)`: la fila se
 * cerraría a cero píxeles. Por eso se pregunta a `maxHeight` y se llena el alto sólo cuando
 * está acotado.
 *
 * 🔴 EL GESTO **REVELA** el menú de acciones; no las ejecuta. Un desliz horizontal que
 * borrara en el mismo gesto haría indistinguibles "Cancelar" de "Eliminar" y dejaría sin
 * ninguna lectura previa a una acción irreversible — y R7 pide TRES acciones, no una.
 * Revelar y luego elegir es lo que permite que las tres compartan gesto.
 *
 * ⚠ Sin acciones NO se monta ni el fondo ni el detector de gestos: una fila que el
 * servidor no habilitó (`can_*` en `false`) no debe capturar un solo toque. [content]
 * se dibuja igualmente, pero sin superficie alrededor. Es donde R1 se nota de verdad en
 * la UI — sin `can_*`, no hay swipe, no hay 403.
 *
 * 🔴 DOS MODOS DE BARRA, y los decide el ANCHO MEDIDO de este componente, nunca quien llama
 * (T9, L12). Por encima de [SwipeNarrowBarMinWidth] de barra revelada, cada acción es un
 * botón con ícono + texto, como siempre. Por debajo, cada acción es un botón SÓLO ÍCONO con
 * su etiqueta como `contentDescription`: en un bloque de media calle la barra mide ~80dp y
 * tres botones con texto necesitan ~286dp, así que el último quedaba recortado — se veía por
 * `customActions` de TalkBack y no se alcanzaba con el dedo. La bandera NO puede ser un
 * parámetro: quien monta el envoltorio no sabe cuánto mide su bloque, y dos pantallas
 * acabarían decidiendo distinto con el mismo ancho.
 *
 * @param actividad se pasa ENTERA y no un `List<SwipeAction>`: la política es del
 *   servidor (R1) y [availableSwipeActions] sólo la LEE. Si esta función derivara las
 *   acciones, existiría una segunda copia de las reglas del backend, y divergiría en
 *   cuanto el servidor cambiara una.
 */
@Composable
fun ActivitySwipeBox(
    actividad: Actividad,
    modifier: Modifier = Modifier,
    /**
     * 🔴 `fillMaxSize` sólo si el alto de ENTRADA está acotado (por defecto `true`).
     *
     * En los TRES montajes reales lo está: `AgendaList.kt` envuelve la fila entera
     * dentro de un `LazyColumn` (alto acotado) y `WeekAgenda.kt` envuelve
     * `DayAgendaRow`/`AgendaTimelineRow`, filas cuyo `Row` mide
     * `height(IntrinsicSize.Min)` — que también acota. Un montaje futuro de alto
     * INFINITO (p.ej. dentro de un `Column` con scroll) debe pasar `false` para que
     * el eje cruzado lo decida el contenido en vez de medir `Constraints.fixed(0, 0)`
     * (la fila se cerraría a cero píxeles).
     *
     * ⚠ POR QUÉ NO SE PREGUNTA EN RUNTIME. Antes esto se resolvía con un
     * `BoxWithConstraints` y `maxHeight.isSpecified`. Pero `BoxWithConstraints` es un
     * `SubcomposeLayout`, y pedirle intrínsecos REVIENTA con
     * `IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout
     * layouts is not supported` — exactamente lo que ocurría al montarlo sobre
     * `AgendaTimelineRow` (su `Row(height(IntrinsicSize.Min))` propaga la medición
     * intrínseca hacia abajo). Con este parámetro el envoltorio deja de ser un
     * `SubcomposeLayout` y el crash desaparece.
     */
    fillCrossAxis: Boolean = true,
    onSwipe: (SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val actions = remember(
        actividad.id,
        actividad.canDelete,
        actividad.canCancel,
        actividad.canDecline,
    ) {
        availableSwipeActions(actividad)
    }

    if (actions.isEmpty()) {
        Box(modifier = modifier) { content() }
        return
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val fill = if (fillCrossAxis) Modifier.fillMaxSize() else Modifier.fillMaxWidth()

    // 🔴 NO volver a meter un `BoxWithConstraints` aquí: pedirle intrínsecos desde un
    // `Row(height(IntrinsicSize.Min))` es el crash documentado arriba. El ancho se
    // observa con `onSizeChanged` (0dp/0px hasta el primer layout), que no es un
    // `SubcomposeLayout` y por tanto no rompe la medición intrínseca.
    var fullWidthPx by remember { mutableStateOf(0f) }
    // T9 · El ancho se observa en PX y el umbral se convierte a px con la densidad del
    // dispositivo: comparar dp contra px elegiría un modo angosto distinto en cada pantalla.
    val density = LocalDensity.current
    val narrowThresholdPx = with(density) { SwipeNarrowBarMinWidth.toPx() }
    val narrowBar = isNarrowSwipeBar(fullWidthPx, narrowThresholdPx)
    val barWidthPx = swipeBarWidthPx(fullWidthPx, narrowThresholdPx)
    // En modo angosto la barra se reparte ENTERA entre las acciones: el ancho de cada botón
    // es `barra / acciones`, no un dp adivinado.
    val narrowActionWidth = with(density) { (barWidthPx / actions.size).toDp() }

    Box(
        modifier = modifier.onSizeChanged { fullWidthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = fill
                .clip(MaterialTheme.shapes.medium)
                // R4-T2-4 · Cada acción se declara como `customActions` para que TalkBack
                // la ofrezca SIN gesto. Sin esto, swipe sería exclusivamente táctil y
                // "No asistir" quedaría inalcanzable para parte de los usuarios.
                //
                // Se lee `action.confirmLabel` y no `label`: lo que se anuncia es la
                // acción completa ("Cancelar con motivo"), no la palabra suelta. Y el
                // destino es `onAction`, la MISMA función que el botón — así no hay una
                // vía accesible y otra táctil que puedan divergir.
                .semantics {
                    customActions = actions.map { action ->
                        CustomAccessibilityAction(action.confirmLabel) {
                            onSwipe(action)
                            true
                        }
                    }
                }
                .pointerInput(actions, fullWidthPx, barWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, SettleSpec) } },
                        onDragEnd = {
                            val commit = abs(offsetX.value) >= fullWidthPx * COMMIT_THRESHOLD
                            scope.launch {
                                offsetX.animateTo(if (commit) -barWidthPx else 0f, SettleSpec)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    // ⇦ `coerceIn`: sin tope, un arrastre rápido hacia la
                                    // derecha sacaría la tarjeta fuera de su hueco y
                                    // `RR-4` (riel y nodo) se desalinearían.
                                    (offsetX.value + dragAmount).coerceIn(-barWidthPx, 0f),
                                )
                            }
                        },
                    )
                },
        ) {
            // ── Barra de acciones, revelada al desplazar ──────────────────────
            if (narrowBar) {
                // T9 (L12) · MODO ANGOSTO. `Arrangement.End` sin separación y cada acción con
                // `barra / acciones` de ancho: es la ÚNICA distribución que deja el objetivo
                // táctil de 48dp en media calle (146dp / 3 = 48,6dp). Con la separación de 8dp
                // de siempre, tres objetivos de 48dp piden 160dp y en 146dp quedarían en 43,3dp
                // — por eso el umbral y el reparto son la misma aritmética.
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        NarrowActionButton(
                            action = action,
                            onClick = { onSwipe(action) },
                            modifier = Modifier.width(narrowActionWidth),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(ActionGap, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        ActionButton(action = action, onClick = { onSwipe(action) })
                    }
                }
            }

            // ── La tarjeta, DESPLAZADA encima ────────────────────────────────
            Box(
                modifier = fill
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    // El fondo sólo se pinta si la tarjeta está desplazada. Siempre, sin
                    // condición, el `surfaceVariant` se transparentaría en reposo y
                    // aparecería un rectángulo de color detrás de cada fila de la agenda.
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                content()
            }
        }
    }
}

/** Fracción del ancho que ocupa la barra revelada. */
private const val BAR_WIDTH_FRACTION = 0.55f

/** Separación entre acciones de la barra — 8dp, la misma de siempre y en los dos modos. */
private val ActionGap = 8.dp

/**
 * Lado mínimo de un botón de acción en MODO ANGOSTO — 48dp, el objetivo táctil de R2/S11.
 *
 * Es el mismo número que [SwipeTouchTargetHeight] pero no se deriva de ahí: alto y ancho son
 * dos decisiones distintas y el modo angosto necesita el ANCHO.
 */
internal val SwipeTouchTargetWidth = 48.dp

/**
 * Umbral del MODO ANGOSTO — **160dp**, medido sobre la BARRA REVELADA y no sobre el bloque.
 *
 * 🔴 Es aritmética, no un número redondo: tres acciones de ícono con el objetivo táctil de 48dp
 * y la separación de siempre ocupan `3 × 48 + 2 × 8 = 160dp`. Por debajo de eso la barra de
 * íconos tampoco cabe, así que ahí lo que se ajusta es el ancho revelado (ver
 * [swipeBarWidthPx]), no el botón. La comparación va sobre la barra y no sobre el bloque a
 * propósito: `BAR_WIDTH_FRACTION` es lo que decide cuánta barra hay, y es ese ancho —no el del
 * bloque— el que tiene que alojar las acciones.
 *
 * ⚠ Las consecuencias del umbral están calculadas con el ancho de contenido del riel del
 * artboard (≈300dp, pantalla de ~392dp): calle completa 296dp → barra 162,8dp ≥ 160 → ETIQUETA;
 * dos calles 146dp → barra 80,3dp → ÍCONOS; tres calles 96dp → barra 52,8dp → ÍCONOS. En una
 * pantalla de 360dp el riel pierde más ancho (≈268dp), así que la calle completa se queda en
 * 264dp → barra 145,2dp → también ÍCONOS: es lo correcto, porque esa barra tampoco cabe los
 * botones con texto, y no es una regresión de nadie (hoy se recortan ahí igual).
 */
internal val SwipeNarrowBarMinWidth: Dp = SwipeTouchTargetWidth * 3f + ActionGap * 2f

/**
 * `true` si la barra revelada de un bloque de [widthPx] no llega a [thresholdPx]: MODO ANGOSTO.
 *
 * Las dos cifras van en la MISMA unidad (las dos en px, o las dos en dp): el sitio de composición
 * convierte el umbral a px con `LocalDensity`, y así esta decisión se puede probar sin densidad.
 *
 * ⚠ `widthPx > 0f` no es defensa: `onSizeChanged` entrega 0 hasta el primer layout, y sin la
 * guarda TODA fila empezaría en modo angosto y la barra cambiaría de forma en el primer frame.
 */
internal fun isNarrowSwipeBar(widthPx: Float, thresholdPx: Float): Boolean =
    widthPx > 0f && widthPx * BAR_WIDTH_FRACTION < thresholdPx

/**
 * Ancho REVELADO de la barra, en la unidad de [widthPx] — la geometría de la que cuelgan el
 * `offsetX` del tope del arrastre y el ancho real de la barra.
 *
 * Modo ancho: la fracción de siempre (`BAR_WIDTH_FRACTION`), intacta. Modo angosto: el bloque
 * ENTERO, acotado al umbral — revelar una fracción de un bloque estrecho es justamente el defecto
 * que dejaba acciones fuera del alcance del dedo, pero en un bloque más ancho que el umbral no hay
 * razón para tapar la tarjeta de más, así que se revela lo que la barra necesita (160dp) y no más.
 *
 * ⚠ Suelo geométrico: en un bloque de un tercio (~96dp) tres objetivos de 48dp son 144dp y no
 * caben en 96dp de ancho disponible. Ahí la barra se revela entera y las acciones se reparten
 * 96/3 = 32dp: se ven y se tocan las tres, por debajo del objetivo de 48dp, pero hoy ese bloque no
 * ofrece NINGUNA acción alcanzable con el dedo. El camino que sí cumple 48dp para un bloque así es
 * la hoja de detalle, y elegir entre las dos cosas es una decisión de producto, no de geometría.
 */
internal fun swipeBarWidthPx(widthPx: Float, thresholdPx: Float): Float =
    if (isNarrowSwipeBar(widthPx, thresholdPx)) {
        minOf(widthPx, thresholdPx)
    } else {
        widthPx * BAR_WIDTH_FRACTION
    }

/** Papel de color del contenedor de cada acción — el color DISTINGUE la acción, no la decora. */
@Composable
private fun SwipeAction.containerColor(): Color = when (this) {
    SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
    SwipeAction.CANCEL -> MaterialTheme.colorScheme.secondaryContainer
    SwipeAction.DECLINE -> MaterialTheme.colorScheme.primaryContainer
}

/** Tinta sobre [containerColor]: el par del rol, nunca un literal. */
@Composable
private fun SwipeAction.contentColor(): Color = when (this) {
    SwipeAction.DELETE -> MaterialTheme.colorScheme.onErrorContainer
    SwipeAction.CANCEL -> MaterialTheme.colorScheme.onSecondaryContainer
    SwipeAction.DECLINE -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun ActionButton(action: SwipeAction, onClick: () -> Unit) {
    // 🔴 EL COLOR DISTINGUE LA ACCIÓN. Antes los tres botones eran `primary` lleno: el
    // mismo aspecto para "No asistir", "Cancelar" y "Eliminar", con "Eliminar" —la
    // única irreversible— painted like a neutral action. Se vio en el teléfono ("se
    // siente mal el tipo de botones"). Ahora cada acción usa su propio papel del
    // esquema: la destructiva en `error`, la reversible en `secondary` y la de la
    // asistencia en `primary`. CERO colores nuevos: son papeles, no literales.
    val container = action.containerColor()
    val content = action.contentColor()
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
        // 48dp de alto (R2 · `touchTarget`): tres botones en el 55% del ancho no caben
        // más anchos, así que la separación va en el `padding` y no en el ancho del botón.
        modifier = Modifier
            .heightIn(min = SwipeTouchTargetHeight)
            .padding(horizontal = 4.dp),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            modifier = Modifier.size(SwipeIconSize),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** El vector de cada acción: el icono REFUERZA el color (el color nunca es la única señal). */
private fun SwipeAction.icon(): ImageVector = when (this) {
    SwipeAction.DELETE -> Icons.Filled.Delete
    SwipeAction.CANCEL -> Icons.Filled.Close
    SwipeAction.DECLINE -> Icons.Filled.PersonOff
}

/**
 * T9 (L12) · Botón de acción en MODO ANGOSTO: sólo ícono, con la ETIQUETA como nombre accesible.
 *
 * 🔴 `contentDescription = action.label` no es opcional. En modo ancho el nombre lo pone el
 * `Text` del botón y el ícono es decorativo; aquí el ícono ES la acción, así que sin esto TalkBack
 * anunciaría "Botón" tres veces seguidas y la acción se volvería adivinanza. Se usa la MISMA
 * etiqueta del enum —`"Eliminar"`, `"Cancelar"`, `"No asistir"`— para que el nombre hablado y el
 * nombre escrito del modo ancho no puedan divergir.
 *
 * ⚠ `contentPadding` en cero porque el relleno por defecto de un `Button` (24dp por lado) es más
 * ancho que el botón entero de un bloque de un tercio: el ícono no cabría y el botón quedaría
 * vacío, que se lee como un hueco y no como una acción.
 *
 * Los colores salen de los MISMOS roles que el modo ancho ([containerColor]/[contentColor]): el
 * modo angosto cambia la FORMA del botón, no su lectura.
 */
@Composable
private fun NarrowActionButton(
    action: SwipeAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = action.containerColor(),
            contentColor = action.contentColor(),
        ),
        contentPadding = PaddingValues(0.dp),
        // 48dp de alto (R2 · objetivo táctil): el ancho lo pone el reparto de la barra, no el botón.
        modifier = modifier.heightIn(min = SwipeTouchTargetHeight),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = action.label,
            modifier = Modifier.size(SwipeNarrowIconSize),
        )
    }
}

/** Alto mínimo de los botones de acción — 48dp según R2 (target táctil). */
internal val SwipeTouchTargetHeight = 48.dp

/** Lado del icono del botón de acción — 18dp (es texto de apoyo, no el protagonist). */
private val SwipeIconSize = 18.dp

/**
 * Lado del icono en MODO ANGOSTO — 24dp.
 *
 * Más grande que [SwipeIconSize] porque ahí el ícono no acompaña a un texto: ES el botón. 24dp es
 * el tamaño de ícono de Material y cabe en el botón de 32dp del bloque más estrecho (96dp / 3) sin
 * tocar el borde.
 */
private val SwipeNarrowIconSize = 24.dp