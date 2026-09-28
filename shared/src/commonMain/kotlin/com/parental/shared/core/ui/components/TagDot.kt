package com.parental.shared.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.parental.shared.core.ui.theme.AgendaGeometry

/*
 * Punto decorativo de etiqueta — `calendar-design-redesign`, T4 (paso 4b). `R8-T4-8`.
 *
 * Fusiona los dos primitivos que eran idénticos salvo por un tamaño fijo:
 * `TimelineNode` (14dp, el nodo de línea) y `CategoryNode` (tamaño del caller).
 * La fusión **parametriza** el tamaño, no lo unifica: el nodo de línea sigue
 * siendo 14dp y el punto de la celda de día sigue siendo 7dp. Unificar esos dos
 * tamaños sería un cambio visual, y T4 no cambia nada visualmente.
 *
 * ACCESIBILIDAD (`S8-T4-8d`): el punto es decorativo y termina en
 * `clearAndSetSemantics { }`, igual que ambos originales. La categoría y el
 * estado los anuncia la etiqueta combinada de la fila
 * (`activitySemanticLabel`), no el punto. Si alguna vez se le da una
 * `contentDescription`, hay que revisar esa etiqueta primero.
 */

/**
 * Círculo de [color] de [size] de diámetro, decorativo.
 *
 * @param color el color de la etiqueta. Se resuelve fuera de aquí, por
 *   [categoryTagTriple] / [statusTagTriple] — este composable no decide color.
 */
@Composable
fun TagDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = AgendaGeometry.lineNode,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
    )
}
