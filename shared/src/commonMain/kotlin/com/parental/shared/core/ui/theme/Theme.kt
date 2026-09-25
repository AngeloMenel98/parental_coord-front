package com.parental.shared.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/*
 * Tema de la app (ADR-2/ADR-10): un único punto de wrap en commonMain —
 * Android hoy, iOS hereda. Solo API pre-M3-1.2 (ADR-1: sin surfaceContainer*,
 * sin dynamic color), compilando contra JB material3 1.7.1 (shared) y
 * androidx material3 1.3.1 (androidApp).
 */

/**
 * Tema Parental Coordination: esquema claro/oscuro según el sistema,
 * tipografía serif/sans y formas 4/8/12/16/24.
 *
 * @param darkTheme sigue al sistema por defecto (isSystemInDarkTheme()).
 * @param content contenido envuelto (App() lo invoca una sola vez).
 */
@Composable
fun ParentalCoordinationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ParentalTypography,
        shapes = ParentalShapes,
        content = content,
    )
}