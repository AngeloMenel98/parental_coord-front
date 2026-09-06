package com.parental.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.model.Gasto
import com.parental.shared.model.GastoDetalle
import com.parental.shared.ui.components.CategoryBadge
import com.parental.shared.ui.components.StatusTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GastosScreen(
    viewModel: GastosViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedGasto != null) {
        GastoDetailScreen(
            gasto = uiState.selectedGasto!!,
            isApproving = uiState.isApproving,
            isDisputing = uiState.isDisputing,
            showDisputeForm = uiState.showDisputeForm,
            disputeReason = uiState.disputeReason,
            disputeNotes = uiState.disputeNotes,
            onBack = viewModel::clearSelection,
            onApprove = viewModel::approveGasto,
            onShowDisputeForm = viewModel::showDisputeForm,
            onUpdateDisputeReason = viewModel::updateDisputeReason,
            onUpdateDisputeNotes = viewModel::updateDisputeNotes,
            onDispute = viewModel::disputeGasto,
        )
    } else {
        GastosListScreen(
            uiState = uiState,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GastosListScreen(
    uiState: GastosUiState,
    viewModel: GastosViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gastos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { viewModel.showCreateForm(true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Registrar gasto",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateForm(true) },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar gasto")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Error banner
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Summary bar
            uiState.summary?.let { summary ->
                SummaryBar(summary)
            }

            // Create form dialog
            if (uiState.showCreateForm) {
                CreateGastoForm(
                    uiState = uiState,
                    viewModel = viewModel,
                )
            }

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.gastos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No hay gastos registrados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.gastos) { gasto ->
                        GastoCard(
                            gasto = gasto,
                            onClick = { viewModel.selectGasto(gasto.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(summary: com.parental.shared.model.GastoSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(
                    label = "Pendiente",
                    amount = summary.totalPending,
                    count = summary.countPending,
                    color = Color(0xFFFB8C00),
                )
                SummaryItem(
                    label = "Aprobado",
                    amount = summary.totalApproved,
                    count = summary.countApproved,
                    color = Color(0xFF43A047),
                )
                SummaryItem(
                    label = "Pagado",
                    amount = summary.totalPaid,
                    count = summary.countPaid,
                    color = Color(0xFF1E88E5),
                )
                SummaryItem(
                    label = "Disputa",
                    amount = summary.totalDisputed,
                    count = summary.countDisputed,
                    color = Color(0xFF8E24AA),
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Double,
    count: Int,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$${String.format("%.2f", amount)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GastoCard(
    gasto: Gasto,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gasto.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    CategoryBadge(category = gasto.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = gasto.expenseDate.take(10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format("%.2f", gasto.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusTag(status = gasto.status)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GastoDetailScreen(
    gasto: GastoDetalle,
    isApproving: Boolean,
    isDisputing: Boolean,
    showDisputeForm: Boolean,
    disputeReason: String,
    disputeNotes: String,
    onBack: () -> Unit,
    onApprove: (String) -> Unit,
    onShowDisputeForm: (Boolean) -> Unit,
    onUpdateDisputeReason: (String) -> Unit,
    onUpdateDisputeNotes: (String) -> Unit,
    onDispute: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Gasto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Amount and status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "$${String.format("%.2f", gasto.amount)} ${gasto.currency}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusTag(status = gasto.status)
                            CategoryBadge(category = gasto.category)
                        }
                    }
                }
            }

            // Description
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Descripción",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = gasto.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Details
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Detalles",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        DetailRow("Fecha", gasto.expenseDate.take(10))
                        gasto.submittedByName?.let {
                            DetailRow("Registrado por", it)
                        }
                        gasto.approvedByName?.let {
                            DetailRow("Aprobado por", it)
                        }
                        gasto.notes?.let {
                            DetailRow("Notas", it)
                        }
                    }
                }
            }

            // Dispute reason if applicable
            gasto.disputeReason?.let { reason ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Motivo de disputa",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Acciones",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        when (gasto.status) {
                            "PENDIENTE" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onApprove(gasto.id) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isApproving,
                                    ) {
                                        if (isApproving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text("Aprobar")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onShowDisputeForm(true) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Disputar")
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    text = "No hay acciones disponibles para este estado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Status history
            if (gasto.statusHistory.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Historial de estados",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            gasto.statusHistory.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "${entry.fromStatus} → ${entry.toStatus}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = entry.changedAt.take(10),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dispute dialog
        if (showDisputeForm) {
            AlertDialog(
                onDismissRequest = { onShowDisputeForm(false) },
                title = {
                    Text(
                        text = "Disputar Gasto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = disputeReason,
                            onValueChange = { onUpdateDisputeReason(it) },
                            label = { Text("Motivo *") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = disputeNotes,
                            onValueChange = { onUpdateDisputeNotes(it) },
                            label = { Text("Notas adicionales") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onDispute(gasto.id) },
                        enabled = !isDisputing && disputeReason.isNotBlank(),
                    ) {
                        if (isDisputing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Enviar disputa")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onShowDisputeForm(false) }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGastoForm(
    uiState: GastosUiState,
    viewModel: GastosViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.showCreateForm(false) },
        title = {
            Text(
                text = "Registrar Gasto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.newDescription,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Descripción *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newAmount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text("Monto *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Category selector
                Text("Categoría", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SALUD" to "Salud",
                        "EDUCACION" to "Educación",
                        "FAMILIAR" to "Familiar",
                        "SOCIAL" to "Social",
                        "RECREACION" to "Recreación",
                        "OTROS" to "Otros",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newCategory == value,
                            onClick = { viewModel.updateCategory(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newExpenseDate,
                    onValueChange = { viewModel.updateExpenseDate(it) },
                    label = { Text("Fecha (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newNotes,
                    onValueChange = { viewModel.updateNotes(it) },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.createGasto() },
                enabled = !uiState.isCreating,
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Registrar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.showCreateForm(false) }) {
                Text("Cancelar")
            }
        },
    )
}
