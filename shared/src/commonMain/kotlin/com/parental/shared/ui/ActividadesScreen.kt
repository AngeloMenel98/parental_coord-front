package com.parental.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.model.Actividad
import com.parental.shared.model.ActividadDetalle
import com.parental.shared.ui.components.CategoryBadge
import com.parental.shared.ui.components.StatusTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActividadesScreen(
    viewModel: ActividadesViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedActividad != null) {
        ActividadDetailScreen(
            actividad = uiState.selectedActividad!!,
            isUpdatingStatus = uiState.isUpdatingStatus,
            onBack = viewModel::clearSelection,
            onUpdateStatus = viewModel::updateStatus,
        )
    } else {
        ActividadesListScreen(
            uiState = uiState,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActividadesListScreen(
    uiState: ActividadesUiState,
    viewModel: ActividadesViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actividades") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { viewModel.showCreateForm(true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear actividad",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateForm(true) },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear actividad")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Todos") },
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Eventos") },
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("Obligaciones") },
                )
            }

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
                CreateActividadForm(
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
            } else if (uiState.actividades.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No hay actividades",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.actividades) { actividad ->
                        ActividadCard(
                            actividad = actividad,
                            onClick = { viewModel.selectActividad(actividad.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActividadCard(
    actividad: Actividad,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (actividad.type == "OBLIGATION") "📋" else "📅",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actividad.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    CategoryBadge(category = actividad.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actividad.scheduledDate.take(10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            StatusTag(status = actividad.status)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActividadDetailScreen(
    actividad: ActividadDetalle,
    isUpdatingStatus: Boolean,
    onBack: () -> Unit,
    onUpdateStatus: (String, String, String?) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(actividad.title) },
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
            // Status and type
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusTag(status = actividad.status)
                    CategoryBadge(category = actividad.category)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (actividad.type == "OBLIGATION") "Obligación" else "Evento",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            // Description
            actividad.description?.let { desc ->
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
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
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

                        DetailRow("Prioridad", actividad.priority)
                        DetailRow("Fecha programada", actividad.scheduledDate.take(10))
                        actividad.dueDate?.let {
                            DetailRow("Fecha límite", it.take(10))
                        }
                        actividad.assignedToName?.let {
                            DetailRow("Asignado a", it)
                        }
                        actividad.createdByName?.let {
                            DetailRow("Creado por", it)
                        }
                    }
                }
            }

            // Status actions
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

                        when (actividad.status) {
                            "CREATED", "ASSIGNED" -> {
                                Button(
                                    onClick = {
                                        onUpdateStatus(actividad.id, "IN_PROGRESS", null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isUpdatingStatus,
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Iniciar")
                                }
                            }
                            "IN_PROGRESS" -> {
                                Button(
                                    onClick = {
                                        onUpdateStatus(actividad.id, "PENDING_VERIFICATION", null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isUpdatingStatus,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Marcar para verificación")
                                }
                            }
                            "PENDING_VERIFICATION" -> {
                                Button(
                                    onClick = {
                                        onUpdateStatus(actividad.id, "COMPLETED", null)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isUpdatingStatus,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verificar y completar")
                                }
                            }
                        }
                    }
                }
            }

            // Status history
            if (actividad.statusHistory.isNotEmpty()) {
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

                            actividad.statusHistory.forEach { entry ->
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
private fun CreateActividadForm(
    uiState: ActividadesUiState,
    viewModel: ActividadesViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.showCreateForm(false) },
        title = {
            Text(
                text = "Crear Actividad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.newTitle,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newDescription,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Type selector
                Text("Tipo", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EVENT" to "Evento", "OBLIGATION" to "Obligación").forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newType == value,
                            onClick = { viewModel.updateType(value) },
                            label = { Text(label) },
                        )
                    }
                }
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
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newCategory == value,
                            onClick = { viewModel.updateCategory(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Priority selector
                Text("Prioridad", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "LOW" to "Baja",
                        "MEDIUM" to "Media",
                        "HIGH" to "Alta",
                        "URGENT" to "Urgente",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.newPriority == value,
                            onClick = { viewModel.updatePriority(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newScheduledDate,
                    onValueChange = { viewModel.updateScheduledDate(it) },
                    label = { Text("Fecha programada (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.newDueDate,
                    onValueChange = { viewModel.updateDueDate(it) },
                    label = { Text("Fecha límite (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.createActividad() },
                enabled = !uiState.isCreating,
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Crear")
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
