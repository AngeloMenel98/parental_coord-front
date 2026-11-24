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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminBondTrendPoint
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminReportExpenseSection
import com.parental.shared.feature.admin.domain.model.AdminUser

/**
 * Panel del rol admin — implementación del artboard `admin-ui/01` (Consola de vínculos).
 *
 * La unidad de trabajo es el vínculo, no el formulario: la pestaña Vínculos muestra
 * tarjetas con salud (hijos, cumplimiento, % pagado) sobre las que se busca por nombre;
 * tocar una abre el detalle con hijos editables, la tendencia de seis meses y el reporte;
 * el PDF se previsualiza como página antes de abrirlo. Los formularios de alta viven en
 * diálogos: en un teléfono, una tarjeta de formulario siempre visible empuja la lista
 * fuera de la pantalla.
 */

private const val USERS_TAB = 0
private const val BONDS_TAB = 1
private const val REPORTS_TAB = 2

private const val CHILDREN_SECTION = 0
private const val MEMBERS_SECTION = 1
// El detalle ya no tiene solapa Reporte: el período y el PDF viven sólo en la pestaña
// Reportes (L21, "sacar la parte de reportes ya que sino esta duplicado").

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    if (state.showBondWizard) {
        BondWizardScreen(state = state, viewModel = viewModel)
        return
    }

    if (state.showPdfPreview) {
        PdfPreviewScreen(
            state = state,
            onClose = { viewModel.closePdfPreview() },
            uriHandler = uriHandler,
        )
        return
    }

    if (state.showReportActivities) {
        // El overlay es un early return ANTES del Scaffold de abajo: sin Scaffold no hay
        // fondo `surface` (en dark mode se ve el fondo claro de la ventana y "pasa a light")
        // ni barra superior. El Scaffold repone ambos; el back «‹ Reporte» vive en el
        // contenido, como en el artboard 04.
        Scaffold(
            topBar = {
                AppTopBar(title = "Actividades", onLogout = onLogout)
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AdminActivitiesDetailScreen(
                    state = state,
                    onBack = { viewModel.closeReportActivities() },
                    onStatusFilter = { viewModel.selectActivityStatusFilter(it) },
                    onAssigneeFilter = { viewModel.selectActivityAssigneeFilter(it) },
                    onRetry = { viewModel.retryReportActivities() },
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            // Misma barra de toda la app. SIN campana a propósito: el admin no tiene
            // vínculo ni endpoint de notificaciones, así que sería un control sin destino.
            AppTopBar(title = "Admin", onLogout = onLogout)
        },
        floatingActionButton = {
            // El alta de vínculo sólo existe en la lista: en el detalle o en el reporte
            // sería un botón fuera de contexto.
            if (state.selectedTab == BONDS_TAB && state.openBondId == null) {
                FloatingActionButton(
                    onClick = { viewModel.openBondWizard() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text("+", fontSize = 28.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == USERS_TAB,
                    onClick = { viewModel.selectTab(USERS_TAB) },
                    text = { Text("Usuarios") },
                )
                Tab(
                    selected = state.selectedTab == BONDS_TAB,
                    onClick = { viewModel.selectTab(BONDS_TAB) },
                    text = { Text("Vínculos") },
                )
                Tab(
                    selected = state.selectedTab == REPORTS_TAB,
                    onClick = {
                        viewModel.selectTab(REPORTS_TAB)
                        viewModel.loadReportIfNeeded()
                    },
                    text = { Text("Reportes") },
                )
            }

            ErrorBanner(state = state, onDismiss = { viewModel.clearError() })

            when (state.selectedTab) {
                USERS_TAB -> UsersTab(state, viewModel)
                BONDS_TAB -> if (state.openBondId != null) {
                    BondDetail(state, viewModel, uriHandler)
                } else {
                    BondsList(state, viewModel)
                }
                else -> ReportsTab(state, viewModel, uriHandler)
            }
        }
    }

    ChildActionDialogs(state, viewModel)
    AddChildDialog(state, viewModel)
    NewProgenitorDialog(state, viewModel)
}

@Composable
private fun ErrorBanner(state: AdminUiState, onDismiss: () -> Unit) {
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
                TextButton(onClick = onDismiss) { Text("Descartar") }
            }
        }
    }
}

// ── Usuarios ────────────────────────────────────────────────────────

@Composable
private fun UsersTab(state: AdminUiState, viewModel: AdminViewModel) {
    val query = state.userSearch.trim()
    val visible = if (query.isEmpty()) {
        state.users
    } else {
        state.users.filter { it.matchesUserQuery(query) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            // F5 · S13: el alta vive en un diálogo con validación; una tarjeta de
            // formulario siempre visible empujaba la lista fuera del teléfono.
            Button(
                onClick = { viewModel.openNewUserDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Nuevo progenitor")
            }
        }

        item {
            OutlinedTextField(
                value = state.userSearch,
                onValueChange = { viewModel.updateUserSearch(it) },
                label = { Text("Buscar por nombre o email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                "Usuarios (${visible.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        if (visible.isEmpty()) {
            item {
                EmptyHint(
                    if (state.users.isEmpty()) {
                        "Todavía no hay usuarios. Creá el primero con el botón de arriba."
                    } else {
                        "Ningún usuario coincide con «$query»."
                    },
                )
            }
        }

        items(visible, key = { it.id }) { user ->
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

            Switch(
                checked = user.isActive,
                onCheckedChange = { onToggleActive() },
                enabled = !isBusy,
            )
        }
    }
}

// ── Vínculos · lista ────────────────────────────────────────────────

@Composable
private fun BondsList(state: AdminUiState, viewModel: AdminViewModel) {
    val query = state.bondSearch.trim()
    val visible = if (query.isEmpty()) {
        state.bonds
    } else {
        state.bonds.filter { it.title.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.lg),
    ) {
        item {
            SummaryStrip(
                users = state.users.size,
                bonds = state.bonds.size,
                active = state.bonds.count { it.isActive },
            )
        }

        item {
            OutlinedTextField(
                value = state.bondSearch,
                onValueChange = { viewModel.updateBondSearch(it) },
                label = { Text("Buscar vínculo por nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (visible.isEmpty()) {
            item {
                EmptyHint(
                    if (state.bonds.isEmpty()) {
                        "Todavía no hay vínculos. Creá el primero con el botón +."
                    } else {
                        "Ningún vínculo coincide con «$query»."
                    },
                )
            }
        }

        items(visible, key = { it.id }) { bond ->
            BondHealthCard(
                bond = bond,
                health = state.healthByBond[bond.id],
                onClick = { viewModel.openBond(bond.id) },
            )
        }
    }
}

@Composable
private fun SummaryStrip(users: Int, bonds: Int, active: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripCell(users.toString(), "Usuarios", Modifier.weight(1f))
            StripDivider()
            StripCell(bonds.toString(), "Vínculos", Modifier.weight(1f))
            StripDivider()
            StripCell(active.toString(), "Activos", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StripDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun StripCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BondHealthCard(
    bond: AdminBond,
    health: com.parental.shared.feature.admin.domain.model.AdminBondHealth?,
    onClick: () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val rate = health?.completionRate
    val chipColor = when {
        rate == null || (health.activitiesTotal == 0) ->
            StatusTokens.triple(StatusTokens.Status.Created, darkTheme).dot
        rate >= 70 -> StatusTokens.triple(StatusTokens.Status.Ok, darkTheme).dot
        else -> StatusTokens.triple(StatusTokens.Status.Ko, darkTheme).dot
    }
    val chipLabel = when {
        rate == null || (health.activitiesTotal == 0) -> "Sin datos"
        rate >= 70 -> "Al día"
        else -> "Con atrasos"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bond.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        bond.agreementType.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(label = chipLabel, color = chipColor)
            }

            if (bond.members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = bond.members.joinToString(" · ") { it.email ?: it.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(modifier = Modifier.fillMaxWidth()) {
                HealthMetric("Hijos", (health?.childrenCount ?: bond.childrenCount).toString(), Modifier.weight(1f))
                HealthMetric(
                    "Cumplido",
                    if (health == null) "—" else adminFormatPercent(health.completionRate),
                    Modifier.weight(1f),
                )
                HealthMetric(
                    "Pagado",
                    if (health == null) "—" else adminFormatPercent(health.paidAmountPercentage),
                    Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(((rate ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(chipColor),
                )
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

/** L21 · salud del vínculo en la cabecera del detalle: lo mismo que dice la tarjeta de la lista. */
@Composable
private fun BondHealthStrip(health: com.parental.shared.feature.admin.domain.model.AdminBondHealth?, childrenCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripCell(health?.let { adminFormatPercent(it.completionRate) } ?: "—", "Cumplido", Modifier.weight(1f))
            StripDivider()
            StripCell(health?.let { adminFormatPercent(it.paidAmountPercentage) } ?: "—", "Pagado", Modifier.weight(1f))
            StripDivider()
            StripCell((health?.childrenCount ?: childrenCount).toString(), "Hijos", Modifier.weight(1f))
        }
    }
}

/** L21 · progenitor del detalle: avatar de iniciales, rol legible y barra de participación. */
@Composable
private fun DetailMemberCard(member: DetailMember, hasReport: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(member.name),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = member.roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (member.assigned > 0) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${member.completed} de ${member.assigned} actividades",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = adminFormatPercent(member.percentage),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (member.percentage / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xxs)
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                } else if (hasReport) {
                    Text(
                        text = "Sin actividades en el período",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Vínculos · detalle ──────────────────────────────────────────────

@Composable
private fun BondDetail(
    state: AdminUiState,
    viewModel: AdminViewModel,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val bond = state.bonds.firstOrNull { it.id == state.openBondId } ?: return
    val darkTheme = isSystemInDarkTheme()
    // L21 · el reporte se carga en background al abrir el detalle (openBond ya setea
    // reportBondId y llama loadReportIfNeeded): alimenta la participación y la actividad
    // por hijo sin renderizar la sección de Reporte acá.
    val report = state.report?.takeIf { it.bondId == bond.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Column {
                TextButton(
                    onClick = { viewModel.backToBondList() },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹ Todos los vínculos")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bond.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Serif,
                        )
                        Text(
                            text = bond.agreementType.replaceFirstChar { it.uppercase() } +
                                " · " + if (bond.isActive) "activo" else "inactivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bond.isActive,
                        onCheckedChange = { viewModel.toggleBondActive(bond) },
                        enabled = bond.id !in state.bondsInFlight,
                    )
                }

                // Salud del vínculo: los mismos números de la tarjeta de la lista, sin abrir Reporte.
                Spacer(modifier = Modifier.height(Spacing.sm))
                BondHealthStrip(health = state.healthByBond[bond.id], childrenCount = bond.childrenCount)

                TextButton(
                    onClick = {
                        viewModel.selectReportBond(bond.id)
                        viewModel.selectTab(REPORTS_TAB)
                        viewModel.loadReportIfNeeded()
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Ver el reporte completo ›")
                }
            }
        }

        item {
            SecondaryTabs(
                selected = state.bondDetailTab,
                labels = listOf("Hijos", "Miembros"),
                onSelect = { viewModel.setBondDetailTab(it) },
            )
        }

        when (state.bondDetailTab) {
            CHILDREN_SECTION -> {
                if (state.bondChildren.isEmpty()) {
                    item { EmptyHint("Este vínculo todavía no tiene hijos.") }
                }
                items(state.bondChildren, key = { it.id }) { child ->
                    ChildCard(
                        child = child,
                        activities = childActivityCount(report, child.id),
                        isEditing = state.editingChildId == child.id,
                        onEdit = { viewModel.startEditingChild(child) },
                        onDelete = { viewModel.requestDeleteChild(child.id) },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.openAddChild() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("+ Agregar hijo")
                    }
                }
            }

            else -> {
                val members = buildDetailMembers(bond, report)
                if (members.isEmpty()) {
                    item { EmptyHint("Sin progenitores cargados.") }
                }
                items(members, key = { it.userId }) { member ->
                    DetailMemberCard(member, hasReport = report != null)
                }
            }
        }

        // La tendencia es del vínculo, no de una solapa: se muestra siempre que haya
        // datos, porque es la respuesta a "¿esto mejora o empeora?".
        if (state.trend.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            "Tendencia · cumplimiento por mes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        TrendChart(state.trend, darkTheme)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        val last = state.trend.lastOrNull()
                        if (last != null) {
                            StatusChip(
                                label = "${monthLabel(last.month)} ${adminFormatPercent(last.completionRate)}",
                                color = StatusTokens.triple(StatusTokens.Status.Ok, darkTheme).dot,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        labels.forEachIndexed { index, label ->
            if (index == selected) {
                Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<AdminBondTrendPoint>, darkTheme: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEachIndexed { index, point ->
            val isCurrent = index == points.lastIndex
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((point.completionRate / 100.0).toFloat().coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isCurrent) primary else muted),
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = monthLabel(point.month),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ── Reportes ────────────────────────────────────────────────────────

@Composable
private fun ReportsTab(
    state: AdminUiState,
    viewModel: AdminViewModel,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.lg),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        "Reporte de vínculo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    if (state.bonds.isEmpty()) {
                        Text(
                            "Todavía no hay vínculos para reportar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // S19 · un solo control para elegir vínculo: con 30 vínculos los chips
                    // apilados empujaban el reporte fuera de la pantalla.
                    val selectedBond = state.bonds.firstOrNull { it.id == state.reportBondId }
                    val filteredBonds = remember(state.bonds, state.reportBondQuery) {
                        val needle = state.reportBondQuery.foldAccents().trim()
                        if (needle.isEmpty()) {
                            state.bonds
                        } else {
                            state.bonds.filter { it.title.foldAccents().contains(needle) }
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { viewModel.setReportBondMenuOpen(true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedBond?.title ?: "Elegí un vínculo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (selectedBond != null) {
                                    val members = selectedBond.members.joinToString(" · ") { it.email ?: it.role }
                                    Text(
                                        text = members.ifBlank { selectedBond.agreementType },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text("▾")
                        }

                        DropdownMenu(
                            expanded = state.reportBondMenuOpen,
                            onDismissRequest = { viewModel.setReportBondMenuOpen(false) },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        ) {
                            OutlinedTextField(
                                value = state.reportBondQuery,
                                onValueChange = viewModel::updateReportBondQuery,
                                singleLine = true,
                                placeholder = { Text("Buscar vínculo") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm),
                            )
                            if (filteredBonds.isEmpty()) {
                                Text(
                                    text = "Ningún vínculo coincide.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Spacing.md),
                                )
                            }
                            filteredBonds.forEach { bond ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = bond.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = { viewModel.selectReportBond(bond.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            ReportContent(
                state = state,
                viewModel = viewModel,
                uriHandler = uriHandler,
                showPeriod = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportContent(
    state: AdminUiState,
    viewModel: AdminViewModel,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    showPeriod: Boolean,
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (showPeriod) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { showFromPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Desde ${adminFormatIsoDate(state.reportDateFrom)}", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showToPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Hasta ${adminFormatIsoDate(state.reportDateTo)}", maxLines = 1)
                }
            }

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
                    Text("Cargar reporte")
                }
            }
        }

        val report = state.report
        if (report == null) {
            EmptyHint("Elegí un vínculo y cargá el reporte del período.")
        } else {
            ActivitiesCard(report) { viewModel.openReportActivities() }
            ExpensesCard(report)
            if (report.members.isNotEmpty()) {
                MembersCard(report)
            }

            OutlinedButton(
                onClick = { viewModel.requestReportPdf() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRequestingReport && state.reportBondId != null,
            ) {
                if (state.isRequestingReport) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Generar PDF del reporte")
                }
            }

            state.requestedReport?.let { requested ->
                Text(
                    text = "PDF: ${requested.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                requested.fileUrl?.let { url ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.openPdfPreview() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Ver página")
                        }
                        Button(
                            onClick = { uriHandler.openUri(url) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Abrir PDF")
                        }
                    }
                }
            }
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
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancelar") } },
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
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            content()
        }
    }
}

@Composable
private fun FigureRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ActivitiesCard(report: AdminBondReport, onOpenDetail: () -> Unit) {
    val counts = remember(report) { buildActivityCounts(report) }
    val members = remember(report) { buildMemberCompletions(report.members) }
    val dark = isSystemInDarkTheme()

    SectionCard(
        title = "Actividades",
        subtitle = if (counts.total > 0) "${counts.completed} de ${counts.total} cumplidas" else null,
    ) {
        if (counts.total == 0) {
            Text(
                "Sin actividades en el período.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // S20 · un solo juego de contadores: la barra, la leyenda y el porcentaje salen de
        // `byStatus`. El campo derivado `overdue` del backend no se lee acá.
        StackedBar(counts.buckets.map { activityBucketColor(it.key, dark) to it.count })
        Spacer(modifier = Modifier.height(Spacing.xs))
        counts.buckets.forEach { bucket ->
            LegendRow(bucket.label, bucket.count, counts.total, activityBucketColor(bucket.key, dark))
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        FigureRow(
            "Cumplidas / no cumplidas",
            "${adminFormatPercent(counts.completionRate)} / ${adminFormatPercent(counts.notCompletedRate)}",
        )
        Text(
            text = "El porcentaje divide siempre por el total del período.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (members.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Cumplimiento por progenitor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            members.forEach { member ->
                val fraction = (member.percentage / 100.0).coerceIn(0.0, 1.0)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(
                            text = member.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${member.completed} de ${member.assigned}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = adminFormatPercent(member.percentage),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // S1 · artboard 04: el cumplimiento por progenitor es un track, no solo texto.
                    LinearProgressIndicator(
                        progress = { fraction.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
            // S1 · copy del artboard 04, con la cuenta del período.
            Text(adminActivityDetailCta(counts.total))
        }
    }
}

/** F3 · el artboard pide porcentajes, no importes: el reparto se lee de un vistazo. */
@Composable
private fun ExpensesCard(report: AdminBondReport) {
    val darkTheme = isSystemInDarkTheme()
    val section: AdminReportExpenseSection = report.expenses

    val paid = section.byStatus["paid"]
    val approved = section.byStatus["approved"]
    val pending = section.byStatus["pending"]
    val rejected = section.byStatus["rejected"]

    val paidCount = paid?.count ?: 0
    val approvedCount = approved?.count ?: 0
    val pendingCount = pending?.count ?: 0
    val rejectedCount = rejected?.count ?: 0
    val totalCount = paidCount + approvedCount + pendingCount + rejectedCount

    // S22 · `Status.Paid` y `Status.Assigned` son el MISMO #1E88E5: usarlos para pagados y
    // aprobados pintaba dos tramos idénticos. Pagados toma `Done` y los otros dos conservan
    // su token, así los cuatro estados del enum se distinguen de un vistazo.
    val segments = listOf(
        StatusTokens.triple(StatusTokens.Status.Done, darkTheme).dot to paidCount,
        StatusTokens.triple(StatusTokens.Status.Paid, darkTheme).dot to approvedCount,
        StatusTokens.triple(StatusTokens.Status.Verify, darkTheme).dot to pendingCount,
        StatusTokens.triple(StatusTokens.Status.Rejected, darkTheme).dot to rejectedCount,
    )

    SectionCard(title = "Gastos", subtitle = "$totalCount gastos") {
        if (totalCount == 0) {
            Text(
                "Sin gastos en el período.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
        Text(
            "Por cantidad de gastos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        StackedBar(segments)
        Spacer(modifier = Modifier.height(Spacing.sm))

        LegendRow("Pagados", paidCount, totalCount, segments[0].first)
        LegendRow("Aprobados sin pagar", approvedCount, totalCount, segments[1].first)
        LegendRow("Pendientes de aprobación", pendingCount, totalCount, segments[2].first)
        LegendRow("Rechazados", rejectedCount, totalCount, segments[3].first)

        Spacer(modifier = Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(Spacing.xs))
        FigureRow("Pagado por monto", adminFormatPercent(section.paidAmountPercentage))
        FigureRow(
            "Pendiente por monto",
            adminFormatPercent(100.0 - section.paidAmountPercentage),
        )
        FigureRow("Total", adminFormatMoney(section.totalAmount))
        }
    }
}

@Composable
internal fun StackedBar(segments: List<Pair<Color, Int>>) {
    val total = segments.sumOf { it.second }.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        segments.forEach { (color, value) ->
            if (value > 0) {
                Box(
                    modifier = Modifier
                        .weight(value.toFloat() / total)
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, count: Int, total: Int, color: Color) {
    val percentage = if (total == 0) 0.0 else (count * 10000.0 / total) / 100.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(Spacing.xxs))
        Text(
            text = "$label $count · ${adminFormatPercent(percentage)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MembersCard(report: AdminBondReport) {
    SectionCard(title = "Progenitores") {
        report.members.forEach { member ->
            FigureRow(
                "${member.firstName} ${member.lastName} · ${member.completed}/${member.assigned}",
                adminFormatPercent(member.completionPercentage),
            )
        }
    }
}

// ── Vista previa del PDF (página del artboard) ──────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPreviewScreen(
    state: AdminUiState,
    onClose: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val report = state.report

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte · PDF") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("‹ Volver") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (report == null) {
                EmptyHint("Cargá el reporte antes de ver la página.")
            } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = RoundedCornerShape(4.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "COORDINACIÓN PARENTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Reporte de vínculo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF191C1D),
                    )
                    Text(
                        text = "${report.bondTitle} · ${adminFormatIsoDate(report.dateFrom)} → " +
                            adminFormatIsoDate(report.dateTo),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F4849),
                    )

                    PdfSection("Actividades") {
                        PdfRow("Total", report.activities.total.toString())
                        PdfRow("Cumplidas", report.activities.completed.toString())
                        PdfRow("Vencidas", report.activities.overdue.toString())
                        PdfRow("Cumplimiento", adminFormatPercent(report.activities.completionRate))
                    }

                    PdfSection("Gastos") {
                        PdfRow("Pagado por monto", adminFormatPercent(report.expenses.paidAmountPercentage))
                        PdfRow("Pagado por cantidad", adminFormatPercent(report.expenses.paidCountPercentage))
                        PdfRow("Total", adminFormatMoney(report.expenses.totalAmount))
                    }

                    if (report.children.isNotEmpty()) {
                        PdfSection("Hijos") {
                            report.children.forEach { child ->
                                PdfRow(
                                    "${child.firstName} ${child.lastName}",
                                    "${child.activityCount} act.",
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Generado ${report.generatedAt} · pág. 1 / 2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    state.requestedReport?.fileUrl?.let { uriHandler.openUri(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.requestedReport?.fileUrl != null,
            ) {
                Text("Abrir PDF")
            }
            if (state.requestedReport?.fileUrl == null) {
                Text(
                    "El PDF todavía no está generado. Generálo desde la solapa Reporte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}

@Composable
private fun PdfSection(title: String, content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(Spacing.sm))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(Spacing.xs))
    content()
}

@Composable
private fun PdfRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF3F4849),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF191C1D),
        )
    }
}

// ── Piezas compartidas ──────────────────────────────────────────────

@Composable
private fun EmptyHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.lg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChildCard(
    child: AdminChild,
    activities: Int?,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val today = remember { kotlinx.datetime.LocalDate.parse(adminTodayIso()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${child.firstName} ${child.lastName}",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = listOfNotNull(
                        "Nacimiento: ${adminFormatIsoDate(child.dateOfBirth)}",
                        childAgeLabel(child.dateOfBirth, today),
                        activities?.let { "$it actividades" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onEdit, enabled = !isEditing) { Text("Editar") }
            TextButton(onClick = onDelete) {
                Text("Borrar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** `2026-06` → `Jun`. Serie del backend en ISO; etiqueta corta para el eje. */
private fun monthLabel(month: String): String {
    val index = month.substringAfter('-', "").toIntOrNull() ?: return month
    val names = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    return names.getOrElse(index - 1) { month }
}

// ── Diálogos ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChildDialog(state: AdminUiState, viewModel: AdminViewModel) {
    if (!state.showAddChild) return
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { viewModel.closeAddChild() },
        title = { Text("Agregar hijo") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.newChildFirstName,
                    onValueChange = { viewModel.updateChildFirstName(it) },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = state.newChildLastName,
                    onValueChange = { viewModel.updateChildLastName(it) },
                    label = { Text("Apellido") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nacimiento: ${adminFormatIsoDate(state.newChildDob.ifBlank { null })}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.addChild() },
                enabled = !state.isCreatingChild,
            ) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeAddChild() }) { Text("Cancelar") }
        },
    )

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.newChildDob.ifBlank { null }),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        viewModel.updateChildDob(adminPickerMillisToIso(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun ChildActionDialogs(state: AdminUiState, viewModel: AdminViewModel) {
    if (state.editingChildId != null) {
        ChildEditDialog(state, viewModel)
    }

    val pendingDelete = state.childPendingDeleteId
    if (pendingDelete != null) {
        val child = state.bondChildren.firstOrNull { it.id == pendingDelete }
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteChild() },
            title = { Text("Borrar hijo") },
            text = {
                Text(
                    child?.let { "¿Borrar a ${it.firstName} ${it.lastName}? No se puede deshacer." }
                        ?: "¿Borrar este hijo? No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteChild() },
                    enabled = !state.isDeletingChild,
                ) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteChild() }) { Text("Cancelar") }
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
        title = { Text("Editar hijo") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.editChildFirstName,
                    onValueChange = { viewModel.updateEditChildFirstName(it) },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = state.editChildLastName,
                    onValueChange = { viewModel.updateEditChildLastName(it) },
                    label = { Text("Apellido") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nacimiento: ${adminFormatIsoDate(state.editChildDob)}")
                }
                if (state.editChildDob != null) {
                    TextButton(onClick = { viewModel.updateEditChildDob(null) }) {
                        Text("Borrar fecha")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.saveChild() },
                enabled = !state.isSavingChild,
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelEditingChild() }) { Text("Cancelar") }
        },
    )

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.editChildDob),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        viewModel.updateEditChildDob(adminPickerMillisToIso(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = picker) }
    }
}
