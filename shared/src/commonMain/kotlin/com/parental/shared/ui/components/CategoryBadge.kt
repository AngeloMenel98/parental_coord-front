package com.parental.shared.ui.components

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
import androidx.compose.ui.unit.sp

@Composable
fun CategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (category.uppercase()) {
        "SALUD" -> Color(0xFFE53935) to "Salud"
        "EDUCACION" -> Color(0xFF1E88E5) to "Educación"
        "FAMILIAR" -> Color(0xFF8E24AA) to "Familiar"
        "SOCIAL" -> Color(0xFF43A047) to "Social"
        "RECREACION" -> Color(0xFFFB8C00) to "Recreación"
        "OTROS" -> Color(0xFF757575) to "Otros"
        else -> MaterialTheme.colorScheme.outline to category
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
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}
