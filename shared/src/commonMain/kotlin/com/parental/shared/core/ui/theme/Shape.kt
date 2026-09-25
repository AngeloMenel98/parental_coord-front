package com.parental.shared.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Escala de formas (design §8): 4/8/12/16/24 dp (extraLarge deliberadamente 24
 * vs 28 de M3, per proposal). CircleShape (nodos de 40px) queda reservado para
 * la fase de timeline — ningún screen consume MaterialTheme.shapes hoy (radii
 * hardcodeados) → delta visual cero.
 */

/** Formas del tema Parental Coordination. */
val ParentalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)