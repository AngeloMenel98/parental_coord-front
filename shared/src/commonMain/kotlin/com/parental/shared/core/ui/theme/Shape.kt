package com.parental.shared.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Escala de formas (design §8): 4/8/12/16/24 dp (extraLarge deliberadamente 24
 * vs 28 de M3, per proposal). CERO delta visual: cada radio aquí reproduce un
 * literal `.dp` que ya existía en el código, así que adoptarlo no cambia ni un
 * píxel de esquina (`RoundedCornerShape(12.dp)` → `ParentalShapes.medium` vale
 * aritméticamente lo mismo).
 *
 * ⚠ CORRECCIÓN (T5-5, `R5-T5-5`): este bloque decía antes *"ningún screen consume
 * MaterialTheme.shapes hoy (radii hardcodeados)"*. Era FALSO y ya no se sostiene:
 * 18 call-sites vivos, en `LoginScreen.kt` (2) y `HomeScreen.kt` (16). No lo
 * reviertas — la afirmación era el bug, no la cuenta.
 *
 * `CircleShape` y su reserva de *"nodos de 40px"*: **GASTADA**. La consumen
 * `TagDot.kt:46` y `HomeScreen.kt:840`. No volver a reservarla ni a suponer que
 * está libre.
 */

/** Formas del tema Parental Coordination. */
val ParentalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)