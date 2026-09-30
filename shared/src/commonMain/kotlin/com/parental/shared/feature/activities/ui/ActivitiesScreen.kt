package com.parental.shared.feature.activities.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.activities.domain.model.canConfirm
import com.parental.shared.feature.activities.ui.detail.ActividadDetailSheet
import com.parental.shared.feature.activities.ui.openedId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

// T4-4: internal (no private) porque `TimelineControls`, que se mudó a
// CalendarChrome.kt, lo recibe como tipo de parámetro. Sigue viviendo en este
// archivo (F1 lo retiene); un símbolo top-level private es invisible entre
// archivos, así que la mudanza y su rewire son un solo commit.
internal enum class TimelineMode(val label: String) {
    AGENDA("Agenda"),
    SEMANA("Semana"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActividadesScreen(
    viewModel: ActividadesViewModel,
    onNavigateBottom: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val initialToday = remember { currentDate() }
    // R4-T1-2 · id → `FocusRequester` de la fila que la pintó; T2-2 lo lee al cerrar.
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }

    // Estado local de presentación; selectedTab sigue perteneciendo al ViewModel.
    var timelineMode by rememberSaveable { mutableStateOf(TimelineMode.AGENDA) }
    var weekStartIso by rememberSaveable { mutableStateOf(mondayStart(initialToday).toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(initialToday.toString()) }

    // D-B-1 · El `SheetState` lo posee el host. `show()` se vuelve a PEDIR en cada apertura, así
    // que un fallo de estado se autocura en lugar de dejar la hoja incapaz de reabrir.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listFallback = remember { FocusRequester() }
    var originId by rememberSaveable { mutableStateOf<String?>(null) }

    // R4 · Destino del foco al cerrar: la fila que abrió la hoja, o el contenedor de la lista
    // si esa fila ya no está compuesta. ⚠ La ausencia se comprueba EXPRESAMENTE: con `?.` el
    // acceso cortocircuitaba devolviendo `null` SIN lanzar, y `recoverCatching` —que sólo corre
    // ante excepción— no se invocaba nunca, dejando el respaldo como código muerto. `runCatching`
    // cubre además el caso en que la fila existe pero `requestFocus()` falla.
    val returnFocus: (String) -> Unit = { id ->
        val row = focusRequesters[id]
        if (row == null) {
            runCatching { listFallback.requestFocus() }
        } else {
            runCatching { row.requestFocus() }
                .onFailure { runCatching { listFallback.requestFocus() } }
        }
    }

    // El pedido es SIEMPRE posterior al clear y va guardado: pedido antes, `closeDetail` podría
    // haber borrado ya la fila y la petición caería al contenedor, o fallaría sin red de seguridad.
    LaunchedEffect(uiState.detail) {
        if (uiState.detail is DetailUiState.Closed) {
            val returning = originId
            originId = null
            if (returning != null) returnFocus(returning)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    val resolve: (String) -> CategoryInfo? = { id -> uiState.categories[id] }

    // FR-1 · Composición hermana: la lista se compone SIEMPRE y la hoja se superpone, así que
    // sobreviven al ciclo abrir/cerrar sus `rememberSaveable`, `remember` y `Crossfade`.
    // ⚠ `R4-T5-4` es la regla de "condición de vacío como `targetState`" de T5-4, de OTRO
    // cambio; NO es el R-4 (foco) de `activity-detail-modal`, que es el de arriba.
    Box(modifier = Modifier.fillMaxSize()) {
        ActividadesListScreen(
            uiState = uiState,
            onSelectTab = viewModel::selectTab,
            onSelectActividad = { id -> originId = id; viewModel.selectActividad(id) },
            onRetryList = viewModel::retry,
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
            focusRequesters = focusRequesters,
            // Hogar de foco: SIN nodo anunciable nuevo, sólo un destino al que caer si la fila
            // que abrió la hoja ya no existe.
            listFocusRequester = listFallback,
        )
        if (uiState.detail !is DetailUiState.Closed) {
            LaunchedEffect(uiState.detail) { sheetState.show() }
            ActividadDetailSheet(
                state = uiState.detail,
                sheetState = sheetState,
                resolve = resolve,
                memberNames = uiState.memberNames,
                snackbarHostState = snackbarHostState,
                onDismissRequest = { uiState.detail.openedId?.let { viewModel.closeDetail(it) } },
                onRetry = viewModel::retryDetail,
                onInertAction = viewModel::onInertAction,
                onConfirm = viewModel::confirmAssignment,
                isConfirming = uiState.isConfirming,
                currentUserId = viewModel.currentUserId,
            )
        }
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
    focusRequesters: MutableMap<String, FocusRequester>,
    listFocusRequester: FocusRequester,
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

    // R4 · Hogar de foco de la lista: `focusable()` da destino a `requestFocus()` cuando la
    // fila que abrió la hoja ya no está compuesta.
    //
    // ⚠ SIN `clearAndSetSemantics { }` (retirado en el pase de remediación). Ese modificador
    // no vacía sólo SU propio nodo: limpia además la semántica de TODOS sus descendientes
    // (`SemanticsModifier.kt:156-162` en compose-ui 1.7.1), de modo que las filas, el
    // `TopAppBar`, las pestañas, el `BottomNavBar` y el `SnackbarHost` de la lista se
    // colapsaban en un único nodo vacío para TalkBack/VoiceOver — de forma PERMANENTE, hoja
    // abierta o cerrada, que es justo lo que R-4 necesita para poder devolver el foco a una
    // fila. Con la hoja CERRADA la lista debe ser plenamente legible (FR-11).
    //
    // El fondo NO necesita supresión propia para ser inerte (FR-4): con la hoja abierta el
    // contenido se compone en OTRA ventana de plataforma —`ModalBottomSheetDialog` es un
    // `Dialog` real (`AndroidDialog.android.kt:287-294`, `ComponentDialog`, sin
    // `FLAG_NOT_TOUCH_MODAL`)— y el `Scrim` de la biblioteca la cubre entera
    // (`ModalBottomSheet.kt:408-431`, `fillMaxSize` + `detectTapGestures`), así que ningún
    // toque llega a la lista. Esa misma ventana modal es la que acota el árbol accesible.
    Box(
        modifier = Modifier.fillMaxSize().focusRequester(listFocusRequester).focusable(),
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
        // R-8 / G-1 · Un ÚNICO `SnackbarHost` en toda la pantalla. La lista lo hospeda sólo
        // cuando NO hay hoja; mientras hay hoja lo hospeda la hoja. Dos hosts siempre activos
        // serían doble pintada y doble anuncio.
        snackbarHost = { if (uiState.detail is DetailUiState.Closed) SnackbarHost(snackbarHostState) },
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

            when {
                uiState.isLoading && uiState.actividades.isEmpty() -> ListSkeleton()
                uiState.listError != null -> EmptyState(
                    title = "No pudimos cargar",
                    message = uiState.listError!!,
                    actionLabel = "Reintentar",
                    onAction = onRetryList,
                )
                // R4-T5-4 · SITIO 2 de 2. La condición de vacío es ahora el
                // `targetState`, no una rama: es la única forma de que la
                // transición ocurra REALMENTE — dos `Crossfade` separados, cada
                // uno con un target constante, no animarían nunca y serían dos
                // sitios de motion que no motion. La precedencia de 4 ramas y el
                // guard `isLoading && isEmpty` de la rama 1 quedan intactos: el
                // mismo test, en el mismo orden, después de los mismos dos.
                //
                // ⚠ DESVIACIÓN DE RC-1, declarada: la línea de predicado
                // `uiState.actividades.isEmpty() ->` desaparece de la `when`. Es
                // la única línea de predicado que T5-4 mueve y no se puede
                // tener ambas cosas — ver el informe de apply. Sin cambio de
                // comportamiento.
                else -> Crossfade(
                    targetState = uiState.actividades.isEmpty(),
                    animationSpec = tween(MotionTokens.durationStandard),
                    label = "listEmptyToContent",
                ) { isEmpty ->
                    if (isEmpty) {
                        EmptyState(
                            title = "No hay actividades",
                            message = "",
                        )
                    } else TimelineContent(
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
                        focusRequesters = focusRequesters,
                    )
                }
            }
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
    focusRequesters: MutableMap<String, FocusRequester>,
) {
    // R4-T5-4 · SITIO 1 de 2. `Crossfade` (no `AnimatedContent`: es experimental
    // en CMP 1.7.1 y añadiría un `@OptIn`, contra la disciplina de DD-2). El
    // `when` conserva sus dos ramas y su orden; lo único que envuelve es el
    // contenido, así que la precedencia es idéntica.
    //
    // `selectedDate` y `weekStart` NO se tocan: viven en `rememberSaveable` de la
    // pantalla (:102-103), un nivel por encima de aquí, así que un crossfade no
    // puede reiniciarlos. Ese es el guard de T5-4.
    Crossfade(
        targetState = mode,
        animationSpec = tween(MotionTokens.durationStandard),
        label = "timelineMode",
    ) { targetMode ->
        when (targetMode) {
            TimelineMode.AGENDA -> AgendaTimeline(
                groups = agendaGroups,
                zone = zone,
                resolve = resolve,
                onSelectActividad = onSelectActividad,
                onConfirm = onConfirm,
                isConfirming = isConfirming,
                currentUserId = currentUserId,
                focusRequesters = focusRequesters,
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
                focusRequesters = focusRequesters,
            )
        }
    }
}

