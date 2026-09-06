package com.parental.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.shared.model.AdminBond
import com.parental.shared.model.AdminUser
import com.parental.shared.model.BondMemberInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
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
            }

            // Error banner
            state.error?.let { error ->
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

            when (state.selectedTab) {
                0 -> UsersTab(state, viewModel)
                1 -> BondsTab(state, viewModel)
            }
        }
    }
}

// ── Users Tab ────────────────────────────────────────────────────

@Composable
private fun UsersTab(state: AdminUiState, viewModel: AdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Create user form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Create Progenitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.newUserFirstName,
                        onValueChange = { viewModel.updateUserFirstName(it) },
                        label = { Text("First Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newUserLastName,
                        onValueChange = { viewModel.updateUserLastName(it) },
                        label = { Text("Last Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newUserEmail,
                        onValueChange = { viewModel.updateUserEmail(it) },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newUserPassword,
                        onValueChange = { viewModel.updateUserPassword(it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

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
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Users list
        items(state.users) { user ->
            UserCard(user)
        }
    }
}

@Composable
private fun UserCard(user: AdminUser) {
    val roleColor = if (user.systemRole == "admin") {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                    fontSize = 18.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                shape = RoundedCornerShape(12.dp),
                color = roleColor.copy(alpha = 0.1f),
            ) {
                Text(
                    text = user.systemRole,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = roleColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
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
            BondCard(bond, viewModel)
        }

        // Children section for selected bond
        state.selectedBondId?.let { bondId ->
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
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
                            child.dateOfBirth?.let {
                                Text(
                                    "DOB: $it",
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

@Composable
private fun BondCard(bond: AdminBond, viewModel: AdminViewModel) {
    val isSelected = viewModel.uiState.collectAsState().value.selectedBondId == bond.id

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.selectBond(bond.id) },
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
                    color = if (bond.isActive) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f),
                ) {
                    Text(
                        text = if (bond.isActive) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (bond.isActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
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
                    "  • ${member.email ?: member.id} (${member.role})",
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
private fun AddChildForm(state: AdminUiState, viewModel: AdminViewModel) {
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
            OutlinedTextField(
                value = state.newChildDob,
                onValueChange = { viewModel.updateChildDob(it) },
                label = { Text("Date of Birth (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
            )
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
}
