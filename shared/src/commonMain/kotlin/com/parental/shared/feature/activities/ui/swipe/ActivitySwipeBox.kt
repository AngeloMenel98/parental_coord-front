package com.parental.shared.feature.activities.ui.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
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
 * @param actividad se pasa ENTERA y no un `List<SwipeAction>`: la política es del
 *   servidor (R1) y [availableSwipeActions] sólo la LEE. Si esta función derivara las
 *   acciones, existiría una segunda copia de las reglas del backend, y divergiría en
 *   cuanto el servidor cambiara una.
 */
@Composable
fun ActivitySwipeBox(
    actividad: Actividad,
    modifier: Modifier = Modifier,
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
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val fullWidthPx = with(density) { maxWidth.toPx() }
        // La barra se mide como una fracción del ancho y NO como un ancho fijo: con tres
        // etiquetas ("Eliminar", "Cancelar", "No asistir") un ancho fijo no cabe en móvil
        // estrecho y el texto se parte. Fracción ⇒ siempre cabe, siempre legible.
        val barWidthPx = fullWidthPx * BAR_WIDTH_FRACTION

        // 🔴 `fillMaxSize` SÓLO si el alto de entrada está acotado — ver el KDoc. Con alto
        // infinito o sin especificar se mide el ancho y el alto lo decide el contenido.
        // `Dp.isSpecified` es justamente `value.isFinite()`, así que cubre los dos casos.
        val fill = if (maxHeight.isSpecified) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        }

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
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    ActionButton(action = action, onClick = { onSwipe(action) })
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

@Composable
private fun ActionButton(action: SwipeAction, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        // 48dp de alto (R2 · `touchTarget`): tres botones en el 55% del ancho no caben
        // más anchos, así que la separación va en el `padding` y no en el ancho del botón.
        modifier = Modifier
            .heightIn(min = SwipeTouchTargetHeight)
            .padding(horizontal = 4.dp),
    ) {
        Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Alto mínimo de los botones de acción — 48dp según R2 (target táctil). */
internal val SwipeTouchTargetHeight = 48.dp