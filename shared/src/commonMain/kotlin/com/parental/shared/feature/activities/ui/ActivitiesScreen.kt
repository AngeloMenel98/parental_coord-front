package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.canConfirm
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val ES_MONTHS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

private const val NEXT_MSG = "Próximamente"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActividadesScreen(
    viewModel: ActividadesViewModel,
    onNavigateBottom: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    val resolve: (String) -> CategoryInfo? = { id -> uiState.categories[id] }

    if (uiState.selectedActividad != null && !uiState.isDetailLoading) {
        ActividadDetailScreen(
            actividad = uiState.selectedActividad!!,
            resolve = resolve,
            memberNames = uiState.memberNames,
            snackbarHostState = snackbarHostState,
            onBack = viewModel::backToList,
            onInertAction = viewModel::onInertAction,
            onNavigateBottom = onNavigateBottom,
            onConfirm = viewModel::confirmAssignment,
            isConfirming = uiState.isConfirming,
            currentUserId = viewModel.currentUserId,
        )
    } else {
        ActividadesListScreen(
            uiState = uiState,
            onSelectTab = viewModel::selectTab,
            onSelectActividad = viewModel::selectActividad,
            onRetryList = viewModel::retry,
            onRetryDetail = viewModel::retryDetail,
            resolve = resolve,
            snackbarHostState = snackbarHostState,
            onInertAction = viewModel::onInertAction,
            onNavigateBottom = onNavigateBottom,
            onConfirm = viewModel::confirmAssignment,
            isConfirming = uiState.isConfirming,
            currentUserId = viewModel.currentUserId,
        )
    }
}

// ── List ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActividadesListScreen(
    uiState: ActivitiesUiState,
    onSelectTab: (ActivityTab) -> Unit,
    onSelectActividad: (String) -> Unit,
    onRetryList: () -> Unit,
    onRetryDetail: () -> Unit,
    resolve: (String) -> CategoryInfo?,
    snackbarHostState: SnackbarHostState,
    onInertAction: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    val groups = remember(uiState.actividades, uiState.selectedTab) {
        buildGroups(uiState.actividades, uiState.selectedTab)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actividades") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    // "+ Nuevo" placeholder — sin llamada API (PRD §8).
                    IconButton(onClick = { onInertAction(NEXT_MSG) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva actividad",
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedRoute = "activities",
                onNavigate = onNavigateBottom,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                ActivityTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            // Banner de error del detalle (lista permanece visible, con retry).
            uiState.detailError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRetryDetail) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            when {
                uiState.isLoading && uiState.actividades.isEmpty() -> ListSkeleton()
                uiState.listError != null -> ListErrorState(
                    error = uiState.listError!!,
                    onRetry = onRetryList,
                )
                uiState.actividades.isEmpty() -> EmptyListState()
                else -> GroupedList(
                    groups = groups,
                    resolve = resolve,
                    onSelectActividad = onSelectActividad,
                    onConfirm = onConfirm,
                    isConfirming = isConfirming,
                    currentUserId = currentUserId,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedList(
    groups: List<ActivityGroup>,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            items(group.sortedItems, key = { it.id }) { actividad ->
                ActividadRow(
                    actividad = actividad,
                    resolve = resolve,
                    onClick = { onSelectActividad(actividad.id) },
                    onConfirm = { onConfirm(actividad.id) },
                    canConfirm = actividad.canConfirm(currentUserId),
                    isConfirming = isConfirming,
                )
            }
        }
    }
}

@Composable
private fun ActividadRow(
    actividad: Actividad,
    resolve: (String) -> CategoryInfo?,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    canConfirm: Boolean,
    isConfirming: Boolean,
    modifier: Modifier = Modifier,
) {
    val info = resolve(actividad.categoryId)
    val icon = info?.emoji ?: if (actividad.type == "obligation") "\uD83D\uDCCB" else "\uD83D\uDCC5"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Cuerpo CLICKEABLE → navega a detalle (S19). El chip "Confirmar" es un
        // SIBLING de esta superficie: un tap en el chip jamás llega al cuerpo
        // (no hay clickables anidados) y un tap en el cuerpo siempre navega.
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actividad.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(categoryId = actividad.categoryId, resolve = resolve)
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusTag(status = actividad.status)
                }
            }
            if (!canConfirm) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canConfirm) {
            Spacer(modifier = Modifier.width(8.dp))
            ConfirmarChip(
                onClick = onConfirm,
                enabled = !isConfirming,
                isConfirming = isConfirming,
            )
        }
    }
}

/** Chip inline "Confirmar" — hermano del cuerpo clickeable de la fila (S18/S19). */
@Composable
private fun ConfirmarChip(
    onClick: () -> Unit,
    enabled: Boolean,
    isConfirming: Boolean,
) {
    Surface(shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isConfirming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Confirmar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ListSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListErrorState(
    error: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun EmptyListState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\uD83D\uDCC5",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay actividades",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Detail ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActividadDetailScreen(
    actividad: ActividadDetalle,
    resolve: (String) -> CategoryInfo?,
    memberNames: Map<String, String>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onInertAction: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    val isSalud = resolve(actividad.categoryId)?.name.equals("Salud", ignoreCase = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        bottomBar = {
            Column {
                // CTA de confirmación — encima del Cargar comprobante inerte (R12/AD-6):
                // estado 2 confirmado → chip inerte; estado 1 canConfirm → botón full-width;
                // estado 0 (no asignado / no asignatario) → nada.
                when {
                    actividad.assignedConfirmed -> AssistChip(
                        onClick = {},
                        label = { Text("Asignación confirmada") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    actividad.canConfirm(currentUserId) -> Button(
                        onClick = { onConfirm(actividad.id) },
                        enabled = !isConfirming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        if (isConfirming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Confirmar asignación")
                        }
                    }
                    else -> {}
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "Cargar comprobante",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInertAction(NEXT_MSG) }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                BottomNavBar(
                    selectedRoute = "activities",
                    onNavigate = onNavigateBottom,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "${actividad.code} · " +
                        if (actividad.type == "obligation") "Obligación" else "Evento",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            item {
                Text(
                    text = actividad.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusTag(status = actividad.status)
                    CategoryBadge(categoryId = actividad.categoryId, resolve = resolve)
                }
            }
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
                            text = actividad.description.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                DetailBlockCard(
                    label1 = "Fecha",
                    value1 = formatDetailDate(actividad.scheduledStart) ?: "—",
                    label2 = "Límite evidencia",
                    value2 = formatDetailDate(actividad.deadline) ?: "—",
                )
            }
            item {
                DetailBlockCard(
                    label1 = "Asignado a",
                    value1 = actividad.assignedTo?.let { memberNames[it] } ?: "Sin asignar",
                    label2 = "Prioridad",
                    value2 = criticalityLabel(actividad.criticality),
                )
            }
            item {
                // Chip contextual inerte — sin llamada API.
                AssistChip(
                    onClick = { onInertAction(NEXT_MSG) },
                    label = {
                        Text(
                            text = if (isSalud) "Adjuntar resumen médico" else "Adjuntar archivo",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailBlockCard(
    label1: String,
    value1: String,
    label2: String,
    value2: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label2,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Grouping / formatting helpers ───────────────────────────────────

private data class ActivityGroup(
    val label: String,
    val date: LocalDate?,
    val sortedItems: List<Actividad>,
)

private fun buildGroups(all: List<Actividad>, tab: ActivityTab): List<ActivityGroup> {
    val filtered = when (tab) {
        ActivityTab.TODOS -> all
        ActivityTab.EVENTOS -> all.filter { it.type == "event" }
        ActivityTab.OBLIGACIONES -> all.filter { it.type == "obligation" }
    }

    val dated = filtered.mapNotNull { actividad ->
        val instant = effectiveInstant(actividad)
        if (instant == null) {
            null
        } else {
            val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            Triple(actividad, date, instant)
        }
    }
    val undated = filtered.filter { actividad -> effectiveInstant(actividad) == null }

    val datedGroups = dated
        .groupBy { (_, date, _) -> date }
        .map { (date, entries) ->
            val sortedItems = entries
                .sortedWith(compareBy<Triple<Actividad, LocalDate, Instant>> { it.third }
                    .thenBy { it.first.title })
                .map { it.first }
            ActivityGroup(
                label = shortDate(date),
                date = date,
                sortedItems = sortedItems,
            )
        }
        .sortedBy { it.date }

    val noDateGroup = if (undated.isNotEmpty()) {
        listOf(ActivityGroup("Sin fecha", null, undated))
    } else {
        emptyList()
    }

    return datedGroups + noDateGroup
}

/** scheduledStart → deadline; si ambos null la actividad va al grupo "Sin fecha". */
private fun effectiveInstant(actividad: Actividad): Instant? {
    val raw = actividad.scheduledStart ?: actividad.deadline ?: return null
    return runCatching { Instant.parse(raw) }.getOrNull()
}

private fun shortDate(date: LocalDate): String =
    "${date.dayOfMonth} ${ES_MONTHS[date.monthNumber - 1]}"

private fun formatDetailDate(raw: String?): String? {
    if (raw == null) return null
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.dayOfMonth} ${ES_MONTHS[ldt.monthNumber - 1]} ${ldt.year}"
}

private fun criticalityLabel(criticality: String): String = when (criticality) {
    "critical" -> "Urgente"
    "high" -> "Alta"
    "medium" -> "Media"
    "low" -> "Baja"
    else -> criticality
}