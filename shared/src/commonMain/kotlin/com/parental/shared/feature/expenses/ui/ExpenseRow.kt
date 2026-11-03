package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.ui.theme.parseHexColor
import com.parental.shared.core.util.formatMoney
import com.parental.shared.core.util.formatRelativeTime
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.displayName

/**
 * Fila de la lista de gastos (S4/T29).
 *
 * Muestra: concepto, badge de estado, tag de categoría, advertencia `sin doc.`,
 * creador + fecha relativa, y monto alineado a la derecha.
 */
@Composable
fun ExpenseRow(
    expense: ExpenseResponseDto,
    categoryMap: Map<String, CategoryInfo>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = expense.concept,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = expense.status)
                    CategoryTag(categoryMap[expense.categoryId])
                    if (!expense.hasDocument) {
                        MissingDocumentTag()
                    }
                }
                Text(
                    text = "${expense.requestedBy} · ${formatRelativeTime(expense.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatMoney(expense.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: ExpenseStatus) {
    val tokenStatus = status.toTokenStatus()
    val triple = StatusTokens.triple(tokenStatus, isSystemInDarkTheme())
    Surface(
        color = triple.container,
        contentColor = triple.content,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status.displayName(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryTag(info: CategoryInfo?) {
    val name = info?.name ?: "Categoría"
    val triple = info?.colorHex?.let { parseHexColor(it) }
        ?.let { dot ->
            // No tenemos container/content del servidor; usamos el dot con fondo suave.
            Triple(dot, dot.copy(alpha = 0.12f), dot)
        }
        ?: CategoryTokens.byName(name)?.let { Triple(it.dot, it.container, it.content) }
        ?: Triple(
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val (dot, container, content) = triple
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(dot)
            }
            Text(
                text = "${info?.emoji ?: ""} $name".trim(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MissingDocumentTag() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "sin doc.",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun ExpenseStatus.toTokenStatus() = when (this) {
    ExpenseStatus.PENDING -> StatusTokens.Status.InProgress
    ExpenseStatus.APPROVED -> StatusTokens.Status.Done
    ExpenseStatus.PAID -> StatusTokens.Status.Paid
    ExpenseStatus.REJECTED -> StatusTokens.Status.Rejected
}
