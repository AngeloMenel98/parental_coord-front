package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onLogout: () -> Unit = {},
    onNavigateBottom: (String) -> Unit = {},
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    onStatusSelected = { viewModel.selectStatusFilter(it) },
                    onExpenseClick = { viewModel.openDetail(it) },
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
}

@Composable
private fun ExpensesContent(
    uiState: ExpensesUiState,
    onStatusSelected: (ExpenseStatus?) -> Unit,
    onExpenseClick: (String) -> Unit,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary != null) {
            item(key = "summary") {
                SummaryCards(
                    summary = summary,
                    selectedStatus = uiState.selectedStatus,
                    onStatusSelected = onStatusSelected,
                )
            }
        }

        if (uiState.items.isEmpty()) {
            item(key = "empty") {
                ExpensesEmptyState(modifier = Modifier.padding(vertical = 32.dp))
            }
        } else {
            items(uiState.items, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    categoryMap = categoryMap,
                    onClick = { onExpenseClick(expense.id) },
                )
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

@Composable
private fun SummaryCards(
    summary: ExpenseSummaryDto,
    selectedStatus: ExpenseStatus?,
    onStatusSelected: (ExpenseStatus?) -> Unit,
) {
    val inProgressTriple = StatusTokens.triple(StatusTokens.Status.InProgress, isSystemInDarkTheme())
    val doneTriple = StatusTokens.triple(StatusTokens.Status.Done, isSystemInDarkTheme())

    // Cuatro tarjetas: las dos de SALDO y las dos de CONTEO (PRD_expense §5.1 más
    // "Por cobrar", que R1 agrega). Las de saldo NO son filtros: un saldo no es
    // un status, y el signo ya dice quién le debe a quién. Las de conteo sí
    // filtran, porque su status es único (FR-2).
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Por pagar",
                value = formatMoney(summary.porPagar),
                isSelected = false,
                onClick = null,
                dotColor = inProgressTriple.dot,
                containerColor = inProgressTriple.container,
                contentColor = inProgressTriple.content,
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Por cobrar",
                value = formatMoney(summary.porCobrar),
                isSelected = false,
                onClick = null,
                dotColor = doneTriple.dot,
                containerColor = doneTriple.container,
                contentColor = doneTriple.content,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Pendientes",
                value = summary.counts.pendientes.toString(),
                isSelected = selectedStatus == ExpenseStatus.PENDING,
                onClick = { onStatusSelected(toggleStatus(selectedStatus, ExpenseStatus.PENDING)) },
                dotColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Pagados",
                value = summary.counts.pagados.toString(),
                isSelected = selectedStatus == ExpenseStatus.PAID,
                onClick = { onStatusSelected(toggleStatus(selectedStatus, ExpenseStatus.PAID)) },
                dotColor = doneTriple.dot,
                containerColor = doneTriple.container,
                contentColor = doneTriple.content,
            )
        }
    }
}

private fun toggleStatus(current: ExpenseStatus?, target: ExpenseStatus): ExpenseStatus? =
    if (current == target) null else target

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    dotColor: Color,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        // `onClick = null` ⇒ la tarjeta NO es filtro (las de saldo): un saldo no
        // tiene status por el cual filtrar.
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) dotColor else containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
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
            .padding(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No hay gastos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Los gastos que registres o te compartan en este vínculo van a aparecer acá.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
