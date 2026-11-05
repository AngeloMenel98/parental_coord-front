package com.parental.shared.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import androidx.compose.ui.platform.LocalUriHandler
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminUser


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            // S12 · G-1…G-5 — la misma barra de toda la app. SIN campana a propósito:
            // el admin no tiene vínculo ni endpoint de notificaciones, así que una
            // campana acá sería un control sin destino. La salida SÍ, ahora con
            // confirmación.
            AppTopBar(
                title = "Admin Panel",
                onLogout = onLogout,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Users") },
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Bonds") },
                )
                Tab(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("Reports") },
                )
            }

            // Error banner
            state.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
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

            when (state.selectedTab) {
                0 -> UsersTab(state, viewModel)
                1 -> BondsTab(state, viewModel)
                2 -> ReportsTab(state, viewModel)
            }

            // F1 · S3 — los diálogos viven fuera del `when` para que sobrevivan al
            // cambio de pestaña sin perder la edición en curso.
            ChildActionDialogs(state, viewModel)
        }
    }
}

// ── Users Tab ────────────────────────────────────────────────────

@Composable
private fun UsersTab(state: AdminUiState, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Create user form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        "Create Progenitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    OutlinedTextField(
                        value = state.newUserFirstName,
                        onValueChange = { viewModel.updateUserFirstName(it) },
                        label = { Text("First Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.newUserLastName,
                        onValueChange = { viewModel.updateUserLastName(it) },
                        label = { Text("Last Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.newUserEmail,
                        onValueChange = { viewModel.updateUserEmail(it) },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.newUserPassword,
                        onValueChange = { viewModel.updateUserPassword(it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    Button(
                        onClick = { viewModel.createUser() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isCreatingUser,
                    ) {
                        if (state.isCreatingUser) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Create User")
                        }
                    }
                }
            }
        }

        // Users list header
        item {
            Text(
                "Users (${state.users.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        // Users list
        items(state.users) { user ->
            UserCard(
                user = user,
                isBusy = user.id in state.usersInFlight,
                onToggleActive = { viewModel.toggleUserActive(user) },
            )
        }
    }
}

@Composable
private fun UserCard(
    user: AdminUser,
    isBusy: Boolean,
    onToggleActive: () -> Unit,
) {
    val roleColor = if (user.systemRole == "admin") {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(roleColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (user.firstName?.firstOrNull()?.uppercase() ?: user.email.first().uppercase()),
                    color = roleColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(user.firstName ?: "")
                        if (user.lastName != null) append(" ${user.lastName}")
                        if (isBlank()) append(user.email)
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = roleColor.copy(alpha = 0.1f),
            ) {
                Text(
                    text = user.systemRole,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    color = roleColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // F1 · S1 — activar/desactivar sin recargar la lista.
            Switch(
                checked = user.isActive,
                onCheckedChange = { onToggleActive() },
                enabled = !isBusy,
            )
        }
    }
}

// ── Bonds Tab ────────────────────────────────────────────────────

@Composable
private fun BondsTab(state: AdminUiState, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Create bond form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Create Bond",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.newBondTitle,
                        onValueChange = { viewModel.updateBondTitle(it) },
                        label = { Text("Bond Title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Agreement type selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("formal", "informal").forEach { type ->
                            FilterChip(
                                selected = state.newBondAgreementType == type,
                                onClick = { viewModel.updateBondAgreementType(type) },
                                label = { Text(type.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.newBondUser1Id,
                        onValueChange = { viewModel.updateBondUser1Id(it) },
                        label = { Text("Parent 1 User ID") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newBondUser2Id,
                        onValueChange = { viewModel.updateBondUser2Id(it) },
                        label = { Text("Parent 2 User ID") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.createBond() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isCreatingBond,
                    ) {
                        if (state.isCreatingBond) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Create Bond")
                        }
                    }
                }
            }
        }

        // Bonds list header
        item {
            Text(
                "Bonds (${state.bonds.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Bonds list
        items(state.bonds) { bond ->
            BondCard(
                bond = bond,
                isSelected = state.selectedBondId == bond.id,
                isBusy = bond.id in state.bondsInFlight,
                onSelect = { viewModel.selectBond(bond.id) },
                onToggleActive = { viewModel.toggleBondActive(bond) },
            )
        }

        // Children section for selected bond
        state.selectedBondId?.let { bondId ->
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Children in Bond",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Add child form
            item {
                AddChildForm(state, viewModel)
            }

            items(state.bondChildren) { child ->
                ChildCard(
                    child = child,
                    isEditing = state.editingChildId == child.id,
                    onEdit = { viewModel.startEditingChild(child) },
                    onDelete = { viewModel.requestDeleteChild(child.id) },
                )
            }
        }
    }
}

@Composable
private fun BondCard(
    bond: AdminBond,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onToggleActive: () -> Unit,
) {
    // Tokens Ok/Ko modo-aware (design §9): ACTIVE → Ok.dot, INACTIVE → Ko.dot.
    // El overlay alpha 0.1f se conserva.
    val darkTheme = isSystemInDarkTheme()
    val okDot = StatusTokens.triple(StatusTokens.Status.Ok, darkTheme).dot
    val koDot = StatusTokens.triple(StatusTokens.Status.Ko, darkTheme).dot

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bond.title, fontWeight = FontWeight.Bold)
                    Text(
                        bond.agreementType.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (bond.isActive) okDot.copy(alpha = 0.1f) else koDot.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = if (bond.isActive) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (bond.isActive) okDot else koDot,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // F1 · S2 — el switch no debe disparar la selección de la tarjeta.
                Switch(
                    checked = bond.isActive,
                    onCheckedChange = { onToggleActive() },
                    enabled = !isBusy,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Members
            Text(
                "Members: ${bond.members.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bond.members.forEach { member ->
                Text(
                    "  \u2022 ${member.email ?: member.id} (${member.role})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Children: ${bond.childrenCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChildCard(
    child: AdminChild,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${child.firstName} ${child.lastName}",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "DOB: ${adminFormatIsoDate(child.dateOfBirth)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // F1 · S3 — edición y borrado explícitos, uno por hijo.
            TextButton(onClick = onEdit, enabled = !isEditing) { Text("Edit") }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** F1 · S3/S4 — diálogos de edición y confirmación de borrado. */
@Composable
private fun ChildActionDialogs(state: AdminUiState, viewModel: AdminViewModel) {
    val childId = state.editingChildId
    if (childId != null) {
        ChildEditDialog(state, viewModel)
    }

    val pendingDelete = state.childPendingDeleteId
    if (pendingDelete != null) {
        val child = state.bondChildren.firstOrNull { it.id == pendingDelete }
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteChild() },
            title = { Text("Delete child") },
            text = {
                Text(
                    child?.let { "Delete ${it.firstName} ${it.lastName}? This cannot be undone." }
                        ?: "Delete this child? This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteChild() },
                    enabled = !state.isDeletingChild,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteChild() }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildEditDialog(state: AdminUiState, viewModel: AdminViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { viewModel.cancelEditingChild() },
        title = { Text("Edit child") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.editChildFirstName,
                    onValueChange = { viewModel.updateEditChildFirstName(it) },
                    label = { Text("First Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.editChildLastName,
                    onValueChange = { viewModel.updateEditChildLastName(it) },
                    label = { Text("Last Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Date of birth: ${adminFormatIsoDate(state.editChildDob)}")
                }
                if (state.editChildDob != null) {
                    TextButton(onClick = { viewModel.updateEditChildDob(null) }) {
                        Text("Clear date")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.saveChild() },
                enabled = !state.isSavingChild,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelEditingChild() }) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.editChildDob),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            viewModel.updateEditChildDob(adminPickerMillisToIso(it))
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChildForm(state: AdminUiState, viewModel: AdminViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = state.newChildFirstName,
                onValueChange = { viewModel.updateChildFirstName(it) },
                label = { Text("Child First Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.newChildLastName,
                onValueChange = { viewModel.updateChildLastName(it) },
                label = { Text("Child Last Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // F1 · S4 — nunca texto libre: el backend valida una `date` ISO.
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Date of birth: ${adminFormatIsoDate(state.newChildDob.ifBlank { null })}")
            }
            if (state.newChildDob.isNotBlank()) {
                TextButton(onClick = { viewModel.updateChildDob("") }) {
                    Text("Clear date")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.addChild() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCreatingChild,
            ) {
                if (state.isCreatingChild) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Add Child")
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.newChildDob.ifBlank { null }),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            viewModel.updateChildDob(adminPickerMillisToIso(it))
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ── Reports Tab · F2 ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsTab(state: AdminUiState, viewModel: AdminViewModel) {
    val uriHandler = LocalUriHandler.current
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // S5 · vínculo + período
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("Bond report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    if (state.bonds.isEmpty()) {
                        Text(
                            "No bonds yet. Create one in the Bonds tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.bonds.forEach { bond ->
                        FilterChip(
                            selected = state.reportBondId == bond.id,
                            onClick = { viewModel.selectReportBond(bond.id) },
                            label = { Text(bond.title) },
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { showFromPicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("From ${adminFormatIsoDate(state.reportDateFrom)}") }
                        OutlinedButton(
                            onClick = { showToPicker = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("To ${adminFormatIsoDate(state.reportDateTo)}") }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Button(
                        onClick = { viewModel.loadReport() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoadingReport && state.reportBondId != null,
                    ) {
                        if (state.isLoadingReport) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Load report")
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedButton(
                        onClick = { viewModel.requestReportPdf() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isRequestingReport && state.reportBondId != null,
                    ) {
                        if (state.isRequestingReport) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Generate PDF")
                        }
                    }

                    val requested = state.requestedReport
                    if (requested != null) {
                        Text(
                            "PDF status: ${requested.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        requested.fileUrl?.let { url ->
                            TextButton(onClick = { uriHandler.openUri(url) }) { Text("Open PDF") }
                        }
                    }
                }
            }
        }

        state.report?.let { report ->
            item { ActivitiesSection(report) }
            item { ExpensesSection(report) }
            item { ChildrenSection(report) }
            item { MembersSection(report) }
        }
    }

    if (showFromPicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.reportDateFrom),
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { viewModel.updateReportFrom(adminPickerMillisToIso(it)) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    if (showToPicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.reportDateTo),
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { viewModel.updateReportTo(adminPickerMillisToIso(it)) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun ReportSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Spacing.xs))
            content()
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActivitiesSection(report: AdminBondReport) {
    ReportSection("Activities") {
        ReportRow("Total", report.activities.total.toString())
        ReportRow("Completed", report.activities.completed.toString())
        ReportRow("Overdue", report.activities.overdue.toString())
        ReportRow("Completion", adminFormatPercent(report.activities.completionRate))
        report.activities.byStatus.forEach { (status, count) ->
            ReportRow(status, count.toString())
        }
        report.activities.byCriticality.forEach { (level, count) ->
            ReportRow("criticality: $level", count.toString())
        }
    }
}

@Composable
private fun ExpensesSection(report: AdminBondReport) {
    ReportSection("Expenses") {
        ReportRow("Total", adminFormatMoney(report.expenses.totalAmount))
        ReportRow("Paid", adminFormatMoney(report.expenses.paidAmount))
        ReportRow("Paid (amount)", adminFormatPercent(report.expenses.paidAmountPercentage))
        ReportRow("Paid (count)", adminFormatPercent(report.expenses.paidCountPercentage))
        ReportRow("Pending", adminFormatMoney(report.expenses.pendingAmount))
        if (report.expenses.byCategory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            report.expenses.byCategory.forEach { category ->
                ReportRow(
                    "${category.categoryName ?: "No category"} (${category.count})",
                    adminFormatMoney(category.amount),
                )
            }
        }
    }
}

@Composable
private fun ChildrenSection(report: AdminBondReport) {
    ReportSection("Children") {
        if (report.children.isEmpty()) {
            Text("No children registered.", style = MaterialTheme.typography.bodySmall)
        }
        report.children.forEach { child ->
            val age = child.ageInYears?.let { "$it y" } ?: "no age"
            ReportRow(
                "${child.firstName} ${child.lastName}",
                "$age · ${child.activityCount} activities",
            )
        }
    }
}

@Composable
private fun MembersSection(report: AdminBondReport) {
    ReportSection("Progenitors") {
        if (report.members.isEmpty()) {
            Text("No active members.", style = MaterialTheme.typography.bodySmall)
        }
        report.members.forEach { member ->
            ReportRow(
                "${member.firstName} ${member.lastName} (${member.role})",
                "${member.completed}/${member.assigned} · ${adminFormatPercent(member.completionPercentage)}",
            )
        }
    }
}
