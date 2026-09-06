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
fun StatusTag(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (status.uppercase()) {
        // Activity statuses
        "CREATED" -> Color(0xFF9E9E9E) to "Creada"
        "ASSIGNED" -> Color(0xFF1E88E5) to "Asignada"
        "IN_PROGRESS" -> Color(0xFFFB8C00) to "En progreso"
        "PENDING_VERIFICATION" -> Color(0xFFFFB300) to "Pend. verificación"
        "COMPLETED" -> Color(0xFF43A047) to "Cumplida"
        "OVERDUE" -> Color(0xFFE53935) to "Vencida"
        "CANCELLED" -> Color(0xFF757575) to "Cancelada"
        // Expense statuses
        "PENDIENTE" -> Color(0xFFFB8C00) to "Pendiente"
        "APROBADO" -> Color(0xFF43A047) to "Aprobado"
        "RECHAZADO" -> Color(0xFFE53935) to "Rechazado"
        "PAGADO" -> Color(0xFF1E88E5) to "Pagado"
        "DISPUTA" -> Color(0xFF8E24AA) to "En disputa"
        // Third party statuses
        "ACTIVE" -> Color(0xFF43A047) to "Activo"
        "INACTIVE" -> Color(0xFF757575) to "Inactivo"
        "PENDING" -> Color(0xFFFB8C00) to "Pendiente"
        // Default
        else -> MaterialTheme.colorScheme.outline to status
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
