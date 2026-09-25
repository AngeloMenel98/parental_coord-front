package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.core.ui.theme.parseHexColor
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.canConfirm
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val ES_MONTHS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

private val ES_WEEKDAYS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

private const val NEXT_MSG = "Próximamente"
private const val NO_START_LABEL = "Sin fecha de inicio"
private const val MAX_TIMELINE_WIDTH = 640

private enum class TimelineMode(val label: String) {
    AGENDA("Agenda"),
    SEMANA("Semana"),
}

private data class TimelineRow(
    val actividad: Actividad,
    /** Solo scheduledStart válido; deadline nunca se convierte en inicio. */
    val scheduledStart: Instant?,
    val localStart: LocalDateTime?,
    val deadline: Instant?,
)

private data class AgendaGroup(
    val date: LocalDate?,
    val rows: List<TimelineRow>,
)

private data class AgendaSlot(
    val hour: Int,
    val minute: Int,
    val rows: List<TimelineRow>,
)

private data class DayBucket(
    val date: LocalDate,
    val slots: List<AgendaSlot>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActividadesScreen(
    viewModel: ActividadesViewModel,
    onNavigateBottom: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val initialToday = remember { currentDate() }

    // Estado local de presentación; selectedTab sigue perteneciendo al ViewModel.
    var timelineMode by rememberSaveable { mutableStateOf(TimelineMode.AGENDA) }
    var weekStartIso by rememberSaveable { mutableStateOf(mondayStart(initialToday).toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(initialToday.toString()) }

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
            timelineMode = timelineMode,
            onTimelineModeChange = { timelineMode = it },
            weekStartIso = weekStartIso,
            onWeekStartChange = { weekStartIso = it },
            selectedDateIso = selectedDateIso,
            onSelectedDateChange = { selectedDateIso = it },
        )
    }
}

// ── List / timeline ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    timelineMode: TimelineMode,
    onTimelineModeChange: (TimelineMode) -> Unit,
    weekStartIso: String,
    onWeekStartChange: (String) -> Unit,
    selectedDateIso: String,
    onSelectedDateChange: (String) -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { currentDateIn(zone) }
    val weekStart = parseIsoDate(weekStartIso) ?: mondayStart(today)
    val selectedDate = parseIsoDate(selectedDateIso) ?: today

    val filteredRows = remember(uiState.actividades, uiState.selectedTab, zone) {
        buildTimelineRows(filterActivities(uiState.actividades, uiState.selectedTab), zone)
    }
    val agendaGroups = remember(filteredRows) { buildAgendaGroups(filteredRows) }
    val weekDates = remember(weekStart) { buildWeekDates(weekStart) }
    val dayBuckets = remember(filteredRows, weekDates) {
        weekDates.associateWith { date -> buildDayBucket(date, filteredRows) }
    }

    // La regla de selección se resuelve al cargar, al cambiar de filtro o al
    // navegar de semana; una selección explícita de un día vacío se conserva.
    var selectionInitialized by rememberSaveable { mutableStateOf(false) }
    var previousTab by remember { mutableStateOf(uiState.selectedTab) }
    var previousWeekIso by remember { mutableStateOf(weekStartIso) }
    var previousRowCount by remember { mutableStateOf(-1) }

    LaunchedEffect(
        uiState.selectedTab,
        uiState.actividades,
        weekStartIso,
        uiState.isLoading,
    ) {
        if (uiState.isLoading && uiState.actividades.isEmpty()) return@LaunchedEffect

        val tabChanged = previousTab != uiState.selectedTab
        val weekChanged = previousWeekIso != weekStartIso
        val dataArrived = previousRowCount == 0 && filteredRows.isNotEmpty()
        if (!selectionInitialized || tabChanged || weekChanged || dataArrived) {
            onSelectedDateChange(
                resolveSelectedDate(
                    weekStart = weekStart,
                    today = today,
                    rows = filteredRows,
                ).toString(),
            )
            selectionInitialized = true
        }
        previousTab = uiState.selectedTab
        previousWeekIso = weekStartIso
        previousRowCount = filteredRows.size
    }

    val navigateWeek: (Int) -> Unit = { direction ->
        val nextWeekStart = weekStart.plus(direction * 7, DateTimeUnit.DAY)
        onWeekStartChange(nextWeekStart.toString())
        onSelectedDateChange(
            resolveSelectedDate(
                weekStart = nextWeekStart,
                today = today,
                rows = filteredRows,
            ).toString(),
        )
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimelineControls(
                mode = timelineMode,
                onModeChange = onTimelineModeChange,
                selectedTab = uiState.selectedTab,
                onSelectTab = onSelectTab,
            )

            // Banner de error del detalle (lista permanece visible, con retry).
            uiState.detailError?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .widthIn(max = MAX_TIMELINE_WIDTH.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = ParentalShapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 12.dp,
                            top = 4.dp,
                            bottom = 4.dp,
                            end = 4.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(
                            onClick = onRetryDetail,
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
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
                else -> TimelineContent(
                    mode = timelineMode,
                    rows = filteredRows,
                    agendaGroups = agendaGroups,
                    weekStart = weekStart,
                    weekDates = weekDates,
                    dayBuckets = dayBuckets,
                    selectedDate = selectedDate,
                    zone = zone,
                    resolve = resolve,
                    onSelectActividad = onSelectActividad,
                    onConfirm = onConfirm,
                    isConfirming = isConfirming,
                    currentUserId = currentUserId,
                    onSelectDate = { date -> onSelectedDateChange(date.toString()) },
                    onPreviousWeek = { navigateWeek(-1) },
                    onNextWeek = { navigateWeek(1) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimelineControls(
    mode: TimelineMode,
    onModeChange: (TimelineMode) -> Unit,
    selectedTab: ActivityTab,
    onSelectTab: (ActivityTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = MAX_TIMELINE_WIDTH.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Vista",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TimelineMode.entries.forEachIndexed { index, timelineMode ->
                SegmentedButton(
                    selected = mode == timelineMode,
                    onClick = { onModeChange(timelineMode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TimelineMode.entries.size,
                    ),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text(timelineMode.label)
                }
            }
        }

        Text(
            text = "Filtrar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActivityTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label) },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineContent(
    mode: TimelineMode,
    rows: List<TimelineRow>,
    agendaGroups: List<AgendaGroup>,
    weekStart: LocalDate,
    weekDates: List<LocalDate>,
    dayBuckets: Map<LocalDate, DayBucket>,
    selectedDate: LocalDate,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    when (mode) {
        TimelineMode.AGENDA -> AgendaTimeline(
            groups = agendaGroups,
            zone = zone,
            resolve = resolve,
            onSelectActividad = onSelectActividad,
            onConfirm = onConfirm,
            isConfirming = isConfirming,
            currentUserId = currentUserId,
        )

        TimelineMode.SEMANA -> WeekTimeline(
            rows = rows,
            weekStart = weekStart,
            weekDates = weekDates,
            dayBuckets = dayBuckets,
            selectedDate = selectedDate,
            zone = zone,
            resolve = resolve,
            onSelectActividad = onSelectActividad,
            onConfirm = onConfirm,
            isConfirming = isConfirming,
            currentUserId = currentUserId,
            onSelectDate = onSelectDate,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaTimeline(
    groups: List<AgendaGroup>,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_TIMELINE_WIDTH.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (groups.isEmpty()) {
                item { TimelineEmpty("No hay actividades para este filtro") }
            } else {
                groups.forEach { group ->
                    item(key = "agenda-${group.date?.toString() ?: "sin-fecha"}") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (group.date == null) {
                                Text(
                                    text = NO_START_LABEL,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 88.dp, bottom = 4.dp),
                                )
                            }
                            group.rows.forEachIndexed { index, row ->
                                AgendaTimelineRow(
                                    row = row,
                                    date = group.date,
                                    showDate = group.date != null && index == 0,
                                    zone = zone,
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
            }
        }
    }
}

@Composable
private fun AgendaTimelineRow(
    row: TimelineRow,
    date: LocalDate?,
    showDate: Boolean,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    val info = resolve(row.actividad.categoryId)
    val categoryColor = categoryDotColor(info)
    val canConfirm = row.actividad.canConfirm(currentUserId)
    val semanticLabel = activitySemanticLabel(row, info, zone)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(78.dp)
                .padding(end = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (showDate && date != null) {
                Text(
                    text = formatAgendaDate(date),
                    style = TypeTokens.date,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
                .clearAndSetSemantics { },
        )

        TimelineNode(
            color = categoryColor,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        ActivityBody(
            row = row,
            info = info,
            zone = zone,
            semanticLabel = semanticLabel,
            showTime = true,
            canConfirm = canConfirm,
            onClick = { onSelectActividad(row.actividad.id) },
            modifier = Modifier.weight(1f),
        )

        if (canConfirm) {
            Spacer(modifier = Modifier.width(6.dp))
            ConfirmarChip(
                onClick = { onConfirm(row.actividad.id) },
                enabled = !isConfirming,
                isConfirming = isConfirming,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekTimeline(
    rows: List<TimelineRow>,
    weekStart: LocalDate,
    weekDates: List<LocalDate>,
    dayBuckets: Map<LocalDate, DayBucket>,
    selectedDate: LocalDate,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val selectedBucket = dayBuckets[selectedDate]
    val unscheduledRows = rows.filter { it.localStart == null }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_TIMELINE_WIDTH.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "week-card") {
                WeekOverviewCard(
                    weekStart = weekStart,
                    weekDates = weekDates,
                    dayBuckets = dayBuckets,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    resolve = resolve,
                )
            }

            item(key = "selected-day-label") {
                Text(
                    text = formatAgendaDate(selectedDate),
                    style = TypeTokens.date,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (selectedBucket == null || selectedBucket.slots.isEmpty()) {
                item(key = "empty-day") {
                    TimelineEmpty("No hay actividades para este día")
                }
            } else {
                selectedBucket.slots.forEach { slot ->
                    item(key = "slot-${slot.hour}-${slot.minute}") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatTime(slot.hour, slot.minute),
                                style = TypeTokens.time,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                slot.rows.forEach { row ->
                                    WeekActivityNode(
                                        row = row,
                                        zone = zone,
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
                }
            }

            if (unscheduledRows.isNotEmpty()) {
                item(key = "unscheduled-header") {
                    Text(
                        text = NO_START_LABEL,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(
                    items = unscheduledRows,
                    key = { it.actividad.id },
                ) { row ->
                    AgendaTimelineRow(
                        row = row,
                        date = null,
                        showDate = false,
                        zone = zone,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekOverviewCard(
    weekStart: LocalDate,
    weekDates: List<LocalDate>,
    dayBuckets: Map<LocalDate, DayBucket>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    resolve: (String) -> CategoryInfo?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.large,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onPreviousWeek,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Anterior")
                }
                Text(
                    text = formatMonthYear(weekStart),
                    style = TypeTokens.date,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onNextWeek,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Siguiente")
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weekDates.forEach { date ->
                    val bucketRows = dayBuckets[date]?.slots
                        ?.flatMap { it.rows }
                        .orEmpty()
                    DayCell(
                        date = date,
                        rows = bucketRows,
                        selected = date == selectedDate,
                        resolve = resolve,
                        onClick = { onSelectDate(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    rows: List<TimelineRow>,
    selected: Boolean,
    resolve: (String) -> CategoryInfo?,
    onClick: () -> Unit,
) {
    val uniqueCategories = rows.distinctBy { it.actividad.categoryId }
    val visibleCategories = uniqueCategories.take(3)
    val extraCategories = (uniqueCategories.size - visibleCategories.size).coerceAtLeast(0)
    val countLabel = when (rows.size) {
        0 -> "sin actividades"
        1 -> "1 actividad"
        else -> "${rows.size} actividades"
    }
    val semanticLabel = buildString {
        append(weekdayLabel(date))
        append(", ")
        append(date.dayOfMonth)
        append(" ")
        append(ES_MONTHS[date.monthNumber - 1])
        append(" ")
        append(date.year)
        append(", ")
        append(countLabel)
        if (selected) append(", seleccionado")
    }

    Column(
        modifier = Modifier
            .width(52.dp)
            .heightIn(min = 76.dp)
            .clip(ParentalShapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = weekdayLabel(date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleCategories.forEach { row ->
                CategoryNode(
                    color = categoryDotColor(resolve(row.actividad.categoryId)),
                    modifier = Modifier.size(7.dp),
                )
            }
            if (extraCategories > 0) {
                Text(
                    text = "+$extraCategories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityBody(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    semanticLabel: String,
    showTime: Boolean,
    showStatusDot: Boolean = false,
    canConfirm: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(ParentalShapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = info?.emoji.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        if (info?.emoji == null) {
            CategoryNode(
                color = categoryDotColor(info),
                modifier = Modifier.size(8.dp),
            )
        }
        if (showStatusDot) {
            Spacer(modifier = Modifier.width(6.dp))
            CategoryNode(
                color = statusDotColor(row.actividad.status),
                modifier = Modifier.size(8.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (showTime && row.localStart != null) {
                Text(
                    text = formatTime(row.localStart.hour, row.localStart.minute),
                    style = TypeTokens.time,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = row.actividad.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.deadline?.let { deadline ->
                Text(
                    text = "Límite: ${formatDeadline(deadline, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CategoryBadge(
                    categoryId = row.actividad.categoryId,
                    resolve = { info },
                )
                StatusTag(status = row.actividad.status)
            }
        }
        if (!canConfirm) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekActivityNode(
    row: TimelineRow,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    val info = resolve(row.actividad.categoryId)
    val canConfirm = row.actividad.canConfirm(currentUserId)
    val semanticLabel = activitySemanticLabel(row, info, zone)

    Surface(
        modifier = Modifier.width(190.dp),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            ActivityBody(
                row = row,
                info = info,
                zone = zone,
                semanticLabel = semanticLabel,
                showTime = false,
                showStatusDot = true,
                canConfirm = canConfirm,
                onClick = { onSelectActividad(row.actividad.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (canConfirm) {
                ConfirmarChip(
                    onClick = { onConfirm(row.actividad.id) },
                    enabled = !isConfirming,
                    isConfirming = isConfirming,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun CategoryNode(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun categoryDotColor(info: CategoryInfo?): Color {
    val darkTheme = isSystemInDarkTheme()
    return info?.colorHex?.let(::parseHexColor)
        ?: info?.name?.let { name ->
            CategoryTokens.resolve(name)?.let { category ->
                CategoryTokens.triple(category, darkTheme).dot
            }
        }
        ?: MaterialTheme.colorScheme.outline
}

@Composable
private fun statusDotColor(status: String): Color {
    val darkTheme = isSystemInDarkTheme()
    return StatusTokens.aliasMap[status.uppercase()]
        ?.let { token -> StatusTokens.triple(token, darkTheme).dot }
        ?: MaterialTheme.colorScheme.outline
}

/** Chip inline "Confirmar" — hermano del cuerpo clickeable de la fila. */
@Composable
private fun ConfirmarChip(
    onClick: () -> Unit,
    enabled: Boolean,
    isConfirming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(shape = ParentalShapes.extraLarge) {
        Row(
            modifier = modifier
                .widthIn(min = 44.dp)
                .heightIn(min = 44.dp)
                .clip(ParentalShapes.extraLarge)
                .clickable(enabled = enabled, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = if (isConfirming) {
                        "Confirmando asignación"
                    } else {
                        "Confirmar asignación"
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
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
private fun TimelineEmpty(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { contentDescription = message },
        )
    }
}

@Composable
private fun ListSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = MAX_TIMELINE_WIDTH.dp)
                    .align(Alignment.CenterHorizontally)
                    .clearAndSetSemantics { contentDescription = "Cargando actividades" },
                shape = ParentalShapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(48.dp)
                            .clip(ParentalShapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .clearAndSetSemantics { },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .clip(ParentalShapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(ParentalShapes.extraSmall)
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
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
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

// ── Timeline derivation / formatting helpers ────────────────────────

private fun filterActivities(
    all: List<Actividad>,
    tab: ActivityTab,
): List<Actividad> = when (tab) {
    ActivityTab.TODOS -> all
    ActivityTab.EVENTOS -> all.filter { it.type == "event" }
    ActivityTab.OBLIGACIONES -> all.filter { it.type == "obligation" }
}

private fun buildTimelineRows(
    actividades: List<Actividad>,
    zone: TimeZone,
): List<TimelineRow> = actividades.map { actividad ->
    val start = parseInstant(actividad.scheduledStart)
    TimelineRow(
        actividad = actividad,
        scheduledStart = start,
        localStart = start?.toLocalDateTime(zone),
        deadline = parseInstant(actividad.deadline),
    )
}

private fun parseInstant(raw: String?): Instant? = raw?.let {
    runCatching { Instant.parse(it) }.getOrNull()
}

private fun buildAgendaGroups(rows: List<TimelineRow>): List<AgendaGroup> {
    val timedRows = rows
        .filter { it.localStart != null }
        .sortedWith(::compareTimedRows)
    val timedGroups = timedRows
        .groupBy { row -> row.localStart?.date }
        .mapNotNull { (date, entries) ->
            date?.let { AgendaGroup(it, entries.sortedWith(::compareTimedRows)) }
        }
        .sortedBy { it.date }

    val undatedRows = rows
        .filter { it.localStart == null }
        .sortedWith(::compareUndatedRows)
    val undatedGroup = if (undatedRows.isEmpty()) {
        emptyList()
    } else {
        listOf(AgendaGroup(date = null, rows = undatedRows))
    }

    return timedGroups + undatedGroup
}

private fun buildWeekDates(weekStart: LocalDate): List<LocalDate> =
    (0..6).map { offset -> weekStart.plus(offset, DateTimeUnit.DAY) }

private fun buildDayBucket(date: LocalDate, rows: List<TimelineRow>): DayBucket {
    val dayRows = rows
        .filter { it.localStart?.date == date }
        .sortedWith(::compareTimedRows)
    val slots = dayRows
        .groupBy { row -> row.localStart!!.hour to row.localStart!!.minute }
        .map { (time, slotRows) -> AgendaSlot(time.first, time.second, slotRows) }
        .sortedWith(compareBy<AgendaSlot> { it.hour }.thenBy { it.minute })
    return DayBucket(date = date, slots = slots)
}

private fun resolveSelectedDate(
    weekStart: LocalDate,
    today: LocalDate,
    rows: List<TimelineRow>,
): LocalDate {
    val dates = buildWeekDates(weekStart)
    val todayHasTimedRows = rows.any { it.localStart?.date == today }
    if (today in dates && todayHasTimedRows) return today

    val earliestTimedDay = rows
        .mapNotNull { it.localStart?.date }
        .filter { it in dates }
        .minOrNull()
    if (earliestTimedDay != null) return earliestTimedDay

    // La decisión de producto es "hoy" como último recurso, incluso tras navegar.
    return today
}

private fun compareTimedRows(left: TimelineRow, right: TimelineRow): Int {
    val byStart = compareNullableInstants(left.scheduledStart, right.scheduledStart)
    if (byStart != 0) return byStart
    return compareTextKeys(left.actividad, right.actividad)
}

private fun compareUndatedRows(left: TimelineRow, right: TimelineRow): Int {
    val byDeadline = compareNullableInstants(left.deadline, right.deadline)
    if (byDeadline != 0) return byDeadline
    return compareTextKeys(left.actividad, right.actividad)
}

private fun compareTextKeys(left: Actividad, right: Actividad): Int {
    val byId = left.id.compareTo(right.id)
    if (byId != 0) return byId
    return left.title.compareTo(right.title)
}

private fun compareNullableInstants(left: Instant?, right: Instant?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right)
}

private fun currentDate(): LocalDate = currentDateIn(TimeZone.currentSystemDefault())

private fun currentDateIn(zone: TimeZone): LocalDate =
    Clock.System.now().toLocalDateTime(zone).date

private fun mondayStart(date: LocalDate): LocalDate =
    date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

private fun parseIsoDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw) }.getOrNull()

private fun weekdayLabel(date: LocalDate): String = ES_WEEKDAYS[date.dayOfWeek.ordinal]

private fun formatMonthYear(date: LocalDate): String =
    "${ES_MONTHS[date.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${date.year}"

private fun formatAgendaDate(date: LocalDate): String =
    "${date.dayOfMonth.toString().padStart(2, '0')} ${ES_MONTHS[date.monthNumber - 1]} ${date.year}"

private fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun formatDeadline(deadline: Instant, zone: TimeZone): String =
    formatAgendaDate(deadline.toLocalDateTime(zone).date)

private fun activitySemanticLabel(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
): String {
    val dateTime = row.localStart?.let { localStart ->
        "${formatAgendaDate(localStart.date)} ${formatTime(localStart.hour, localStart.minute)}"
    } ?: NO_START_LABEL
    val category = info?.name?.takeIf { it.isNotBlank() } ?: "desconocida"
    val deadline = row.deadline?.let { "Límite ${formatDeadline(it, zone)}" }
    return listOfNotNull(
        dateTime,
        row.actividad.title,
        "Categoría $category",
        typeLabel(row.actividad.type),
        statusSemanticLabel(row.actividad.status),
        deadline,
    ).joinToString(", ")
}

private fun typeLabel(type: String): String =
    if (type == "obligation") "Obligación" else "Evento"

private fun statusSemanticLabel(status: String): String = when (status.uppercase()) {
    "CREATED" -> "Creada"
    "ASSIGNED" -> "Asignada"
    "IN_PROGRESS" -> "En progreso"
    "VERIFY", "PENDING_VERIFICATION" -> "Por verificar"
    "DONE", "COMPLETED" -> "Cumplida"
    "OVERDUE" -> "Vencida"
    "CANCELLED" -> "Cancelada"
    else -> "Estado ${status.ifBlank { "desconocido" }}"
}

private fun formatDetailDate(raw: String?): String? {
    val instant = parseInstant(raw) ?: return null
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
