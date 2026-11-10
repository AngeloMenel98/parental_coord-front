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
import com.parental.shared.core.ui.theme.StatusTokens

/**
 * Tag de status: 17 claves MAYÚSCULAS → entry vía [StatusTokens.aliasMap]
 * (design §9), color = dot modo-aware (ADR-7), patrón de overlay alpha 0.15f
 * preservado (migración). Status desconocido → outline + texto crudo.
 */
@Composable
fun StatusTag(
    status: String,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val key = status.uppercase()
    val color: Color = StatusTokens.aliasMap[key]
        ?.let { StatusTokens.triple(it, darkTheme).dot }
        ?: MaterialTheme.colorScheme.outline
    val label = when (key) {
        // Activity statuses
        "CREATED" -> "Creada"
        "ASSIGNED" -> "Asignada"
        "IN_PROGRESS" -> "En progreso"
        "VERIFY" -> "Por verificar"
        "DONE" -> "Cumplido"
        "PENDING_VERIFICATION" -> "Pend. verificación"
        "COMPLETED" -> "Cumplida"
        "OVERDUE" -> "Vencida"
        "CANCELLED" -> "Cancelada"
        // Expense statuses
        "PENDIENTE" -> "Pendiente"
        "APROBADO" -> "Aprobado"
        "RECHAZADO" -> "Rechazado"
        "PAGADO" -> "Pagado"
        "DISPUTA" -> "En disputa"
        // Third party statuses
        "ACTIVE" -> "Activo"
        "INACTIVE" -> "Inactivo"
        "PENDING" -> "Pendiente"
        // Default
        else -> status
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