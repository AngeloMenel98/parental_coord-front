package com.parental.shared.feature.expenses.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.util.formatMoney
import com.parental.shared.core.util.formatShortDate
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.displayName

/**
 * Pantalla de detalle de un gasto (S5/T19).
 *
 * Renderiza los datos del gasto y los botones de acción que el SERVIDOR manda
 * (`canEdit`, `canCancel`, `canApprove`, `canReject`, `canMarkPaid`, `isTerminal`).
 * Después de una acción exitosa refresca el detalle y el resumen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    viewModel: ExpensesViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val uiState = viewModel.uiState.collectAsState().value
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
                title = "Detalle del gasto",
                onLogout = onLogout,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val detail = uiState.detail) {
                is DetailUiState.Loading -> DetailLoadingState()
                is DetailUiState.Error -> DetailErrorState(
                    message = detail.message,
                    onRetry = { viewModel.openDetail(detail.id) },
                )
                is DetailUiState.Content -> ExpenseDetailContent(
                    expense = detail.expense,
                    onApprove = { viewModel.approve(detail.expense.id) },
                    onReject = { reason -> viewModel.reject(detail.expense.id, reason) },
                    onPay = { viewModel.pay(detail.expense.id) },
                    onCancel = { viewModel.cancel(detail.expense.id); onBack() },
                    onEdit = { request -> viewModel.update(detail.expense.id, request) },
                )
                DetailUiState.Closed -> {
                    LaunchedEffect(Unit) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun ExpenseDetailContent(
    expense: ExpenseResponseDto,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onEdit: (ExpenseUpdateRequest) -> Unit,
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showRejectDialog) {
        ReasonDialog(
            title = "Rechazar gasto",
            confirmLabel = "Rechazar",
            onConfirm = { reason ->
                showRejectDialog = false
                onReject(reason)
            },
            onDismiss = { showRejectDialog = false },
        )
    }

    if (showEditDialog) {
        EditExpenseDialog(
            expense = expense,
            onConfirm = { request ->
                showEditDialog = false
                onEdit(request)
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("¿Cancelar este gasto?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancel()
                    },
                ) {
                    Text("Cancelar gasto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Volver")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailHeader(expense = expense)
        DetailInfo(expense = expense)
        DetailActions(
            expense = expense,
            onApprove = onApprove,
            onReject = { showRejectDialog = true },
            onPay = onPay,
            onCancel = { showCancelDialog = true },
            onEdit = { showEditDialog = true },
        )
    }
}

@Composable
private fun DetailHeader(expense: ExpenseResponseDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = expense.concept,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatMoney(expense.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Estado: ${expense.status.displayName()}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DetailInfo(expense: ExpenseResponseDto) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailInfoRow(label = "Categoría", value = expense.categoryName)
        DetailInfoRow(label = "Fecha", value = formatShortDateString(expense.expenseDate))
        DetailInfoRow(label = "Descripción", value = expense.description?.takeIf { it.isNotBlank() } ?: "—")
        DetailInfoRow(label = "División", value = "Pagador ${expense.splitRatioPayer}% / Otro ${expense.splitRatioOther}%")
        // Los NOMBRES, no los ids: un UUID en pantalla no le dice nada a nadie. El
        // nombre viene del servidor (que ya resuelve el de la categoría); si no hay
        // perfil, se cae al id — mejor un UUID que un hueco sin explicación.
        DetailInfoRow(label = "Solicitado por", value = expense.requestedByName ?: expense.requestedBy)
        DetailInfoRow(
            label = "Contraparte",
            value = expense.counterpartName ?: expense.counterpartId ?: "Sin contraparte",
        )
        expense.approvedBy?.let {
            DetailInfoRow(label = "Aprobado por", value = expense.approvedByName ?: it)
        }
        expense.approvedAt?.let { DetailInfoRow(label = "Aprobado el", value = it) }
        expense.paidAt?.let { DetailInfoRow(label = "Pagado el", value = it) }
        if (!expense.hasDocument) {
            Text(
                text = "⚠️ Este gasto no tiene documento de respaldo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailActions(
    expense: ExpenseResponseDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expense.canApprove) {
            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Aprobar")
            }
        }
        if (expense.canReject) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Rechazar")
            }
        }
        if (expense.canMarkPaid) {
            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Marcar pagado")
            }
        }
        if (expense.canEdit) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Editar")
            }
        }
        if (expense.canCancel) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Cancelar")
            }
        }
        if (expense.isTerminal) {
            Text(
                text = "Este gasto está cerrado y no admite más acciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motivo (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Volver")
            }
        },
    )
}

@Composable
private fun EditExpenseDialog(
    expense: ExpenseResponseDto,
    onConfirm: (ExpenseUpdateRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var concept by remember { mutableStateOf(expense.concept) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var expenseDate by remember { mutableStateOf(expense.expenseDate) }
    var description by remember { mutableStateOf(expense.description ?: "") }
    var splitPayer by remember { mutableStateOf(expense.splitRatioPayer.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar gasto") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Concepto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // La categoría se MUESTRA pero no se edita aquí: pedir un UUID
                // escrito a mano no es una interfaz, es una invitinga a escribir
                // basura. El selector de categoría (una sola taxonomía, R2) llega
                // con el formulario de alta de T27 y se reutiliza en las dos
                // superficies.
                Text(
                    "Categoría: ${expense.categoryName ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = expenseDate,
                    onValueChange = { expenseDate = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = splitPayer,
                        onValueChange = { splitPayer = it },
                        label = { Text("% pagador") },
                        modifier = Modifier.weight(1f),
                    )
                    // La contraparte la DERIVA el servidor: no es un campo que
                    // el usuario pueda mandar.
                    Text(
                        "La otra parte: ${expense.splitRatioOther}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ExpenseUpdateRequest(
                            concept = concept.takeIf { it != expense.concept },
                            amount = amount.toDoubleOrNull()?.takeIf { it != expense.amount },
                            expenseDate = expenseDate.takeIf { it != expense.expenseDate },
                            description = description.takeIf { it != (expense.description ?: "") },
                            splitRatioPayer = splitPayer.toIntOrNull()?.takeIf { it != expense.splitRatioPayer },
                        )
                    )
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun DetailLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
            ) {
                Text("Reintentar")
            }
        }
    }
}

private fun formatShortDateString(isoOrDate: String): String {
    return runCatching {
        kotlinx.datetime.LocalDate.parse(isoOrDate)
    }.getOrNull()?.let { formatShortDate(it) } ?: isoOrDate
}
