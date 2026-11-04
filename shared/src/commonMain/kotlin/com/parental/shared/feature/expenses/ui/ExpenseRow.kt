package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.ui.theme.parseHexColor
import com.parental.shared.core.util.formatMoney
import com.parental.shared.feature.expenses.domain.ledgerDate
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.displayName
import com.parental.shared.feature.expenses.domain.viewerShare
import com.parental.shared.feature.expenses.ui.swipe.ExpenseSwipeBox

/**
 * Fila de la lista de gastos como asiento de un libro (S3).
 *
 * Anatomía: fecha a la izquierda (día grande sobre el mes abreviado), concepto, chips de
 * estado + categoría (y `sin doc.` cuando falta el documento), y a la derecha el monto total
 * con la parte que le toca a quien pregunta.
 *
 * S4: si el SERVIDOR habilitó aprobar o rechazar (`canApprove`/`canReject`), la fila se desliza
 * a la izquierda y deja las dos acciones detrás; el cliente no deduce el permiso del estado.
 */
@Composable
fun ExpenseRow(
    expense: ExpenseResponseDto,
    categoryMap: Map<String, CategoryInfo>,
    currentUserId: String? = null,
    modifier: Modifier = Modifier,
    swipeOpen: Boolean = false,
    onSwipeOpenChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    val date = ledgerDate(expense.expenseDate)
    val share = viewerShare(expense, currentUserId)
    val showShare = share != null
    val canSwipe = expense.canApprove || expense.canReject

    ExpenseSwipeBox(
        open = swipeOpen,
        onOpenChange = onSwipeOpenChange,
        enabled = canSwipe,
        onApprove = onApprove,
        onReject = onReject,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                // Fondo OPACO: las acciones del swipe viven detrás y no deben verse a
                // través de la fila cuando está cerrada.
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xs, vertical = Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = date.day,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (date.parsed) {
                    Text(
                        text = date.month,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = expense.concept,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = expense.status)
                    CategoryTag(categoryMap[expense.categoryId])
                    if (!expense.hasDocument) {
                        MissingDocumentTag()
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                if (showShare) {
                    Text(
                        text = "Tu parte ${formatMoney(share)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
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
            Canvas(modifier = Modifier.size(6.dp)) {
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
