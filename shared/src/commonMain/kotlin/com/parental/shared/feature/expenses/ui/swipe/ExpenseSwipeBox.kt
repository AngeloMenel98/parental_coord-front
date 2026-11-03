package com.parental.shared.feature.expenses.ui.swipe

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Acciones de swipe de una fila de gasto (S4): Rechazar y Aprobar, rectangulares y a alto
 * completo detrás de la fila.
 *
 * Decisiones:
 * - **Controlado por el llamador** ([open] + [onOpenChange]) para que la lista cierre la fila
 *   anterior al abrir otra; un estado interno por fila dejaría varias abiertas a la vez.
 * - **Sólo se desliza si [enabled]**, y quien decide eso es el llamador a partir de los flags
 *   del servidor (`canApprove`/`canReject`): el cliente no deduce permisos del estado.
 * - **Accesibilidad**: el gesto se anuncia como [CustomAccessibilityAction] en el contenido.
 *   Un swipe no es descubrible con lector de pantalla, y las dos acciones visuales quedan
 *   fuera del árbol de semántica para no duplicar el mismo botón dos veces.
 * - **Sin dependencias experimentales**: `detectHorizontalDragGestures` + `Animatable`, que
 *   es estable en la versión de Compose que usa el módulo.
 */
@Composable
fun ExpenseSwipeBox(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val actionWidth = 78.dp
    val maxSwipePx = with(LocalDensity.current) { (actionWidth * 2).toPx() }
    // El desplazamiento es estado de UI: durante el arrastre sigue el dedo 1:1 y al soltar
    // se anima al extremo. Nada de lanzar una corrutina por evento de drag.
    var offsetPx by remember { mutableFloatStateOf(if (open) -maxSwipePx else 0f) }
    val scope = rememberCoroutineScope()
    val duration = MotionTokens.durationStandard

    suspend fun settle(target: Float) {
        animate(
            initialValue = offsetPx,
            targetValue = target,
            animationSpec = tween(durationMillis = duration),
        ) { value, _ -> offsetPx = value }
    }

    // El estado de apertura vive arriba: acá sólo se anima hacia donde el llamador dice.
    LaunchedEffect(open, maxSwipePx) {
        settle(if (open) -maxSwipePx else 0f)
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .clearAndSetSemantics { },
        ) {
            SwipeAction(
                label = "Rechazar",
                background = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                width = actionWidth,
                onClick = {
                    onOpenChange(false)
                    onReject()
                },
            )
            SwipeAction(
                label = "Aprobar",
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                width = actionWidth,
                onClick = {
                    onOpenChange(false)
                    onApprove()
                },
            )
        }

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(offsetPx.roundToInt(), 0)
                    }
                }
                .pointerInput(open, maxSwipePx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldOpen = offsetPx < -maxSwipePx / 2f
                            onOpenChange(shouldOpen)
                            scope.launch {
                                settle(if (shouldOpen) -maxSwipePx else 0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-maxSwipePx, 0f)
                        },
                    )
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(label = "Aprobar gasto") {
                            onApprove()
                            true
                        },
                        CustomAccessibilityAction(label = "Rechazar gasto") {
                            onReject()
                            true
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    background: Color,
    contentColor: Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}
