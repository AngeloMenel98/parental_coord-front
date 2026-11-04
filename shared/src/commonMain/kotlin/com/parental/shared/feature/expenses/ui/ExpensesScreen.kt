package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.util.formatMoney
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto

/**
 * Pantalla de gastos (S4/T29): tarjetas de resumen + lista filtrable.
 *
 * - Cuatro tarjetas: "Por pagar" y "Por cobrar" (los dos saldos de R1) más
 *   "Pendientes" y "Pagados" (los dos conteos de PRD_expense §5.1).
 * - Las dos de saldo NO filtran: un saldo no es un status. Las dos de conteo
 *   sí filtran la lista por su status (FR-2).
 * - Tocar una fila abre el detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    currentUserId: String? = null,
    onLogout: () -> Unit = {},
    onNavigateBottom: (String) -> Unit = {},
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    pendingActivityAttachmentId: String? = null,
    pendingActivityAttachmentFileName: String? = null,
    onPendingAttachmentConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingActivityAttachmentId, pendingActivityAttachmentFileName) {
        val attachmentId = pendingActivityAttachmentId ?: return@LaunchedEffect
        val concept = pendingActivityAttachmentFileName
            ?.toExpenseConcept()
            ?.takeIf(String::isNotEmpty)
        viewModel.openCreateSheet(initialConcept = concept, activityAttachmentId = attachmentId)
        onPendingAttachmentConsumed()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Gastos",
                onLogout = onLogout,
                unreadCount = unreadCount,
                onOpenNotifications = onOpenNotifications,
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedRoute = "expenses",
                onNavigate = onNavigateBottom,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openCreateSheet() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Solicitar") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> ExpensesLoadingState()
                uiState.error != null && uiState.items.isEmpty() -> ExpensesErrorState(
                    message = uiState.error.orEmpty(),
                    onRetry = { viewModel.refresh() },
                )
                uiState.items.isEmpty() && uiState.summary == null -> ExpensesEmptyState()
                else -> ExpensesContent(
                    uiState = uiState,
                    currentUserId = currentUserId,
                    onStatusSelected = { viewModel.selectStatusFilter(it) },
                    onExpenseClick = { viewModel.openDetail(it) },
                    onApprove = { id -> viewModel.approve(id) },
                    onReject = { id -> viewModel.reject(id) },
                    onLoadMore = { viewModel.loadMore() },
                )
            }
        }
    }

    if (uiState.detail !is DetailUiState.Closed) {
        ExpenseDetailScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeDetail() },
            onLogout = onLogout,
        )
    }

    if (uiState.isCreateSheetOpen) {
        CreateExpenseSheet(
            categories = uiState.categories,
            isLoading = uiState.isCreateSheetLoading,
            initialConcept = uiState.createInitialConcept,
            hasPendingAttachment = uiState.pendingActivityAttachmentId != null,
            onSubmit = { viewModel.submitCreateExpense(it) },
            onDismiss = { viewModel.closeCreateSheet() },
        )
    }
}

@Composable
private fun ExpensesContent(
    uiState: ExpensesUiState,
    currentUserId: String?,
    onStatusSelected: (ExpenseStatus?) -> Unit,
    onExpenseClick: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val summary = uiState.summary
    val categoryMap = remember(uiState.items) {
        CategoryResolver.build(uiState.items.map {
            CategoryDto(
                id = it.categoryId,
                name = it.categoryName,
            )
        })
    }
    // Sólo una fila deslizada a la vez: abrir una cierra la anterior.
    var openExpenseId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (summary != null) {
            item(key = "balance") {
                BalanceHeader(summary = summary)
            }
            item(key = "filters") {
                StatusFilterRow(
                    summary = summary,
                    selectedStatus = uiState.selectedStatus,
                    onStatusSelected = onStatusSelected,
                )
            }
        }

        if (uiState.items.isEmpty()) {
            item(key = "empty") {
                ExpensesEmptyState(modifier = Modifier.padding(vertical = Spacing.padded))
            }
        } else {
            items(uiState.items, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    categoryMap = categoryMap,
                    currentUserId = currentUserId,
                    swipeOpen = openExpenseId == expense.id,
                    onSwipeOpenChange = { open ->
                        openExpenseId = if (open) expense.id else null
                    },
                    onClick = { onExpenseClick(expense.id) },
                    onApprove = { onApprove(expense.id) },
                    onReject = { onReject(expense.id) },
                )
                if (expense.id != uiState.items.last().id) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (uiState.canLoadMore) {
                item(key = "load-more") {
                    LoadMoreRow(
                        isLoading = uiState.isLoadingMore,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

/**
 * S1 · el saldo del vínculo, tal como lo manda el servidor.
 *
 * Es el número que el endpoint de resumen ya calcula y que la pantalla no mostraba. El
 * cliente NO lo recalcula (R1): sólo elige la leyenda y el color por el signo. `Por pagar` y
 * `Por cobrar` quedan como dato: un saldo no es un filtro.
 */
@Composable
private fun BalanceHeader(summary: ExpenseSummaryDto, modifier: Modifier = Modifier) {
    val saldo = summary.saldo
    val dark = isSystemInDarkTheme()
    val status = when {
        saldo > 0 -> StatusTokens.Status.Ok
        saldo < 0 -> StatusTokens.Status.Ko
        else -> null
    }
    val amountColor = status
        ?.let { StatusTokens.triple(it, dark).content }
        ?: MaterialTheme.colorScheme.onSurface
    val legend = when {
        saldo > 0 -> "A favor tuyo"
        saldo < 0 -> "En contra"
        else -> "Al día"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = "Saldo del vínculo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(saldo),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
                maxLines = 1,
            )
            Text(
                text = legend,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.hairline)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                BalanceFigure(
                    label = "Por pagar",
                    value = formatMoney(summary.porPagar),
                    modifier = Modifier.weight(1f),
                )
                BalanceFigure(
                    label = "Por cobrar",
                    value = formatMoney(summary.porCobrar),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BalanceFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * S2 · filtros por estado con el conteo del servidor.
 *
 * Reemplazan a las cuatro tarjetas: antes dos filtraban y dos no, con la misma forma. Ahora
 * el filtro es una fila de chips y los dos saldos viven arriba, donde nadie espera tocarlos.
 */
@Composable
private fun StatusFilterRow(
    summary: ExpenseSummaryDto,
    selectedStatus: ExpenseStatus?,
    onStatusSelected: (ExpenseStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Filter(val label: String, val count: Int, val status: ExpenseStatus?)

    val counts = summary.counts
    val all = counts.pendientes + counts.aprobados + counts.pagados + counts.rechazados
    val filters = listOf(
        Filter("Todas", all, null),
        Filter("Pendientes", counts.pendientes, ExpenseStatus.PENDING),
        Filter("Aprobados", counts.aprobados, ExpenseStatus.APPROVED),
        Filter("Pagados", counts.pagados, ExpenseStatus.PAID),
        Filter("Rechazados", counts.rechazados, ExpenseStatus.REJECTED),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        filters.forEach { filter ->
            val selected = selectedStatus == filter.status
            Surface(
                onClick = { onStatusSelected(filter.status) },
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.medium,
                border = if (selected) {
                    null
                } else {
                    BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outline)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Text(
                        text = filter.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onLoadMore) {
                Text("Cargar más")
            }
        }
    }
}

@Composable
private fun ExpensesLoadingState(modifier: Modifier = Modifier) {
    ContentListSkeleton(contentDescription = "Cargando gastos", modifier = modifier)
}

@Composable
private fun ExpensesErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(Spacing.xl),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun ExpensesEmptyState(modifier: Modifier = Modifier) {
    EmptyContentState(
        title = "No hay gastos",
        message = "Los gastos que registres o te compartan en este vínculo van a aparecer acá.",
        modifier = modifier,
    )
}
