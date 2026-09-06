package com.parental.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.model.Tercero
import com.parental.shared.model.TerceroDetalle
import com.parental.shared.ui.components.StatusTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TercerosScreen(
    viewModel: TercerosViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedTercero != null) {
        TerceroDetailScreen(
            tercero = uiState.selectedTercero!!,
            isEditing = uiState.isEditing,
            showCreateForm = uiState.showCreateForm,
            uiState = uiState,
            viewModel = viewModel,
            onBack = viewModel::clearSelection,
            onStartEdit = { viewModel.startEdit(uiState.selectedTercero!!) },
        )
    } else {
        TercerosListScreen(
            uiState = uiState,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TercerosListScreen(
    uiState: TercerosUiState,
    viewModel: TercerosViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terceros") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { viewModel.showCreateForm(true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar tercero",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateForm(true) },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar tercero")
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

            // Create form dialog
            if (uiState.showCreateForm) {
                CreateTerceroForm(
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
            } else if (uiState.terceros.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No hay terceros registrados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.terceros) { tercero ->
                        TerceroCard(
                            tercero = tercero,
                            onClick = { viewModel.selectTercero(tercero.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerceroCard(
    tercero: Tercero,
    onClick: () -> Unit,
) {
    val roleLabel = when (tercero.role) {
        "COORDINATOR" -> "Coordinador"
        "THERAPIST" -> "Terapeuta"
        "TUTOR" -> "Tutor"
        "LEGAL" -> "Legal"
        else -> tercero.role
    }

    val scopeLabel = when (tercero.scope) {
        "CHILD" -> "Menor"
        "BOTH_PARENTS" -> "Ambos progenitores"
        "ONE_PARENT" -> "Un progenitor"
        else -> tercero.scope
    }

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
            // Avatar circle
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tercero.nombre.first().uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tercero.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = scopeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StatusTag(status = tercero.status)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerceroDetailScreen(
    tercero: TerceroDetalle,
    isEditing: Boolean,
    showCreateForm: Boolean,
    uiState: TercerosUiState,
    viewModel: TercerosViewModel,
    onBack: () -> Unit,
    onStartEdit: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tercero.nombre) },
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
                actions = {
                    IconButton(onClick = onStartEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                        )
                    }
                    IconButton(onClick = { viewModel.showRevokeDialog(true) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Revocar acceso",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Create/Edit form dialog
        if (showCreateForm) {
            CreateTerceroForm(
                uiState = uiState,
                viewModel = viewModel,
            )
        }

        // Revoke dialog
        if (uiState.showRevokeDialog) {
            RevokeDialog(
                reason = uiState.revokeReason,
                isRevoking = uiState.isRevoking,
                onUpdateReason = viewModel::updateRevokeReason,
                onConfirm = { viewModel.revokeTercero(tercero.id) },
                onDismiss = { viewModel.showRevokeDialog(false) },
            )
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status and role
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusTag(status = tercero.status)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = when (tercero.role) {
                                    "COORDINATOR" -> "Coordinador"
                                    "THERAPIST" -> "Terapeuta"
                                    "TUTOR" -> "Tutor"
                                    "LEGAL" -> "Legal"
                                    else -> tercero.role
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = when (tercero.scope) {
                                    "CHILD" -> "Menor"
                                    "BOTH_PARENTS" -> "Ambos progenitores"
                                    "ONE_PARENT" -> "Un progenitor"
                                    else -> tercero.scope
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            // Contact info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Información de contacto",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        tercero.email?.let {
                            DetailRow("Email", it)
                        }
                        tercero.phone?.let {
                            DetailRow("Teléfono", it)
                        }
                        tercero.createdByName?.let {
                            DetailRow("Creado por", it)
                        }
                    }
                }
            }

            // Notes
            tercero.notes?.let { notes ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Notas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Access permissions
            if (tercero.accessPermissions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Permisos de acceso",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            tercero.accessPermissions.forEach { permission ->
                                Text(
                                    text = "• $permission",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
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
private fun CreateTerceroForm(
    uiState: TercerosUiState,
    viewModel: TercerosViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.showCreateForm(false) },
        title = {
            Text(
                text = if (uiState.isEditing) "Editar Tercero" else "Agregar Tercero",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.newName,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newEmail,
                    onValueChange = { viewModel.updateEmail(it) },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newPhone,
                    onValueChange = { viewModel.updatePhone(it) },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Role selector
                Text("Rol", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "COORDINATOR" to "Coordinador",
                        "THERAPIST" to "Terapeuta",
                        "TUTOR" to "Tutor",
                        "LEGAL" to "Legal",
                        "OTHER" to "Otro",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newRole == value,
                            onClick = { viewModel.updateRole(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Scope selector
                Text("Alcance", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "CHILD" to "Menor",
                        "BOTH_PARENTS" to "Ambos",
                        "ONE_PARENT" to "Un progenitor",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newScope == value,
                            onClick = { viewModel.updateScope(value) },
                            label = { Text(label) },
                        )
                    }
                }
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
                onClick = { viewModel.saveTercero() },
                enabled = !uiState.isCreating,
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (uiState.isEditing) "Guardar" else "Agregar")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevokeDialog(
    reason: String,
    isRevoking: Boolean,
    onUpdateReason: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Revocar Acceso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "¿Estás seguro de que deseas revocar el acceso de este tercero?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { onUpdateReason(it) },
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRevoking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (isRevoking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Revocar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
