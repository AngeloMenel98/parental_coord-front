package com.parental.shared.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.parseHexColor

/**
 * Chip de categoría por NOMBRE (canónico ES, ej. "Educación" / "EDUCACION").
 * El color sale de CategoryTokens (modo-aware, ADR-7); si la categoría no es
 * conocida → chip neutro (outline). Migración §9: los 6 re-tints de categoría.
 */
@Composable
fun CategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val label = when (category.uppercase()) {
        "SALUD" -> "Salud"
        "EDUCACION" -> "Educación"
        "FAMILIAR" -> "Familiar"
        "SOCIAL" -> "Social"
        "RECREACION" -> "Recreación"
        "OTROS" -> "Otros"
        else -> category
    }
    val color: Color = CategoryTokens.resolve(category)
        ?.let { CategoryTokens.triple(it, darkTheme).dot }
        ?: MaterialTheme.colorScheme.outline

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Variante UUID: resuelve categoryId vía [resolve] (CategoryResolver).
 * `cat.color` del servidor GANA (parseado con [parseHexColor], no-break);
 * sin color de servidor → fallback CategoryTokens por nombre (modo-aware);
 * sin resolución → chip neutral (outline, "Categoría").
 */
@Composable
fun CategoryBadge(
    categoryId: String,
    resolve: (String) -> CategoryInfo?,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val info = resolve(categoryId)
    val color: Color = info?.colorHex?.let(::parseHexColor)
        ?: info?.name?.let { name ->
            CategoryTokens.resolve(name)?.let { cat -> CategoryTokens.triple(cat, darkTheme).dot }
        }
        ?: MaterialTheme.colorScheme.outline
    val label = when {
        info == null -> "Categoría"
        info.emoji != null -> "${info.emoji} ${info.name}"
        else -> info.name
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}