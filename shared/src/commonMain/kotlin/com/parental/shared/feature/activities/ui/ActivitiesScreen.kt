package com.parental.shared.feature.activities.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.detail.ActividadDetailSheet
import com.parental.shared.feature.activities.ui.create.CreateActivitySheet
import com.parental.shared.feature.activities.ui.openedId
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.activities.ui.swipe.SwipeReasonDialog
import com.parental.shared.feature.activities.ui.swipe.SWIPE_UNDO_LABEL
import com.parental.shared.feature.bonds.domain.model.VinculoChild
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
    onCreateExpenseFromAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    /** S12 · G-5 — contador de no leídas para la campana de la barra global. */
    unreadCount: Int = 0,
    /** S12 · G-2 — abre la lista de notificaciones. `null` esconde la campana. */
    onOpenNotifications: (() -> Unit)? = null,
    /** S12 · G-3 — salida con confirmación, igual que en el resto de las vistas. */
    onLogout: () -> Unit = {},
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
    // si esa fila ya no está. ⚠ HAY DOS CAÍDAS DISTINTAS y aquí están las dos: la fila no está en
    // `focusRequesters` (su id salió del conjunto de datos en una recarga, o el `originId`
    // sobrevivió a la muerte del proceso) ⇒ rama `row == null`; o la fila está registrada pero
    // su nodo ya no está adjunto (se destruyó el ítem al salir del viewport) ⇒ `requestFocus()`
    // LANZA y lo cubre `onFailure`. Sin esa segunda rama el respaldo es código muerto: `?.`
    // cortocircuitaba devolviendo `null` sin lanzar, y `recoverCatching` —que sólo corre ante
    // excepción— no se invocaba nunca (así lo halló verify en el pase 2, C-5).
    val returnFocus: (String) -> Unit = { id ->
        val row = focusRequesters[id]
        if (row == null) {
            runCatching { listFallback.requestFocus() }
        } else {
            runCatching { row.requestFocus() }
                .onFailure { runCatching { listFallback.requestFocus() } }
        }
    }

    // R4 · Poda por CONJUNTO DE DATOS, no por descarte. Podar al cerrar borraría la entrada de
    // una fila que sigue compuesta y que no volvería a registrarse —`SideEffect` sólo corre al
    // recomponer el ítem—, así que el retorno caería al contenedor por un motivo inventado. Una
    // recarga, en cambio, cambia los datos: todo ítem superviviente recompone y se reinscribe por
    // su `SideEffect`, y un id que ya no existe no puede volver a pedir foco. Queda acotado.
    LaunchedEffect(uiState.actividades) {
        focusRequesters.keys.retainAll(uiState.actividades.map { it.id }.toSet())
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

    LaunchedEffect(uiState.snackbarMessage, uiState.undoOffer) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val offer = uiState.undoOffer
        val result = if (offer != null) {
            // R2 · El snackbar de una acción reversible LLEVA "Deshacer". No es
            // adorno: la fila ya salió de la lista de forma optimista, así que sin
            // esta acción el usuario perdería la actividad sin haber visto nada.
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = SWIPE_UNDO_LABEL,
                duration = SnackbarDuration.Short,
            )
        } else {
            snackbarHostState.showSnackbar(message)
        }
        if (offer != null && result == SnackbarResult.ActionPerformed) {
            // ⇦ NO se consume aquí: `undoLastSwipe()` publica su propio mensaje
            // ("Deshecho"). Consumirlo en este punto borraría ese mensaje en el mismo
            // frame en que se escribe.
            viewModel.undoLastSwipe()
        } else {
            // El snackbar se tragó solo (o había otra oferta): la ventana se cierra
            // para no dejar un botón que ya no serviría.
            viewModel.consumeSnackbar()
            viewModel.consumeUndoOffer()
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
            onToggleChild = viewModel::toggleChildFilter,
            onClearChildren = viewModel::clearChildFilter,
            onSelectActividad = { id -> originId = id; viewModel.selectActividad(id) },
            onRetryList = viewModel::retry,
            resolve = resolve,
            snackbarHostState = snackbarHostState,
            onNavigateBottom = onNavigateBottom,
            unreadCount = unreadCount,
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
            onConfirm = viewModel::confirmAssignment,
            isConfirming = uiState.isConfirming,
            onOpenCreate = viewModel::openCreate,
            onSwipeAction = viewModel::startSwipeAction,
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
                children = uiState.children,
                snackbarHostState = snackbarHostState,
                onDismissRequest = { uiState.detail.openedId?.let { viewModel.closeDetail(it) } },
                onRetry = viewModel::retryDetail,
                onInertAction = viewModel::onInertAction,
                onConfirm = viewModel::confirmAssignment,
                isConfirming = uiState.isConfirming,
                onComplete = viewModel::completeActivity,
                isCompleting = uiState.isCompleting,
                // "No asistir" desde la hoja: el gesto de swipe se cierra cuando la
                // asignación está confirmada, así que esta acción vive aquí (abajo se
                // abre el diálogo de motivo obligatorio, el mismo que usa el swipe).
                onDecline = { id -> viewModel.startSwipeAction(id, SwipeAction.DECLINE) },
                memberCandidates = uiState.memberCandidates,
                onAssign = viewModel::assignActivity,
                onReassign = viewModel::reassignActivity,
                isAssigning = uiState.isAssigning,
                isReassigning = uiState.isReassigning,
                attachments = viewModel.attachments.collectAsState().value,
                attachmentsLoading = viewModel.attachmentsLoading.collectAsState().value,
                onLoadAttachments = viewModel::loadAttachments,
                onAddAttachment = viewModel::addAttachment,
                onCreateExpenseFromAttachment = onCreateExpenseFromAttachment,
                onRenameAttachment = viewModel::renameAttachment,
                onReplaceAttachment = viewModel::replaceAttachment,
                onDeleteAttachment = viewModel::deleteAttachment,
            )
        }
        // T-FE-17 · El "+" abre el modal de alta. Se compone SOBRE la lista (composición hermana,
        // igual que el detalle), así que sobrevive al ciclo abrir/cerrar sus `remember`.
        if (uiState.swipeReason !is SwipeReasonUiState.Closed) {
            val reason = uiState.swipeReason as SwipeReasonUiState.Content
            SwipeReasonDialog(
                state = reason,
                onReasonChange = viewModel::setSwipeReason,
                onSubmit = viewModel::submitSwipeReason,
                onDismiss = viewModel::dismissSwipeReason,
            )
        }

        if (uiState.create !is CreateUiState.Closed) {
            CreateActivitySheet(
                state = uiState.create,
                form = uiState.createForm,
                isDirty = uiState.createDirty,
                categories = uiState.categories,
                children = uiState.children,
                memberCandidates = uiState.memberCandidates,
                snackbarHostState = snackbarHostState,
                onTitleChange = viewModel::setCreateTitle,
                onDescriptionChange = viewModel::setCreateDescription,
                onCategoryChange = viewModel::setCreateCategory,
                onToggleChild = viewModel::toggleCreateChild,
                onAssigneeChange = viewModel::setCreateAssignee,
                onDateChange = viewModel::setCreateDate,
                onTimeChange = viewModel::setCreateTime,
                onEndDateChange = viewModel::setCreateEndDate,
                onEndTimeChange = viewModel::setCreateEndTime,
                onClearEnd = viewModel::clearCreateEnd,
                onSubmit = viewModel::submitCreate,
                // ⚠ Reintentar = `submitCreate` tal cual: reutiliza el MISMO `createForm` intacto
                //   (D19), así que el reintento no pierde ni un carácter de lo escrito.
                onRetry = viewModel::submitCreate,
                onDismissRequest = viewModel::closeCreate,
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
    onToggleChild: (String) -> Unit,
    onClearChildren: () -> Unit,
    onSelectActividad: (String) -> Unit,
    onRetryList: () -> Unit,
    resolve: (String) -> CategoryInfo?,
    snackbarHostState: SnackbarHostState,
    onNavigateBottom: (String) -> Unit,
    unreadCount: Int,
    onOpenNotifications: (() -> Unit)?,
    onLogout: () -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    /**
     * T-FE-17 · Abre el modal de alta. Sustituye a `onInertAction` en esta firma: el "+" era su
     * ÚNICO consumidor aquí y era un placeholder inerte, así que al dejar de serlo, mantener el
     * parámetro sería dejar un cable muerto. La hoja de detalle sigue usando `onInertAction`
     * por su propio canal.
     */
    onOpenCreate: () -> Unit,
    /**
     * R2-T2-7 · Reenvía una acción de swipe desde la agenda. Se declara aquí y NO dentro
     * de `TimelineView`: el cable tiene que existir aunque la semana no esté visible, o
     * la firma de `TimelineView` cambiaría cada vez que se añadiera una acción.
     */
    onSwipeAction: (String, SwipeAction) -> Unit,
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

    // T4 (S3) · Estado de la hoja de calendario y del salto de día.
    // `showCalendar` es `rememberSaveable`: sobrevive a la rotación con la hoja abierta.
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    // El mes visible es una FECHA (cadena ISO), no un `LocalDate`: `LocalDate` no es
    // `Saveable` y la hoja debe reabrirse en el mismo mes tras rotar.
    var visibleMonthIso by rememberSaveable { mutableStateOf(today.toString()) }
    val calendarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // El destino del salto se guarda un instante y lo consume un `LaunchedEffect`: el
    // índice del encabezado depende de los grupos, que pueden cambiar con los datos.
    var pendingJumpDate by remember { mutableStateOf<LocalDate?>(null) }
    // En modo Semana, el día elegido en la hoja es una SELECCIÓN EXPRESA: no la puede
    // pisar la regla que re-resuelve el día al cambiar de semana. Se guarda un instante
    // y se aplica DESPUÉS de esa regla (los dos `LaunchedEffect` corren en orden de
    // declaración), así que la última palabra es la del usuario.
    var pendingWeekSelection by remember { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()

    val filteredRows = remember(uiState.actividades, uiState.selectedTab, uiState.selectedChildren, zone) {
        buildTimelineRows(
            filterActivities(uiState.actividades, uiState.selectedTab, uiState.selectedChildren),
            zone,
        )
    }
    val agendaGroups = remember(filteredRows) { buildAgendaGroups(filteredRows) }
    // Los puntos se calculan una vez por lista Y por catálogo de categorías: si las
    // actividades llegan antes que las categorías, un `remember` con sólo la lista
    // dejaría los puntos en `outline` para siempre.
    val dayDots = remember(filteredRows, uiState.categories) {
        buildDayDots(filteredRows, resolve)
    }
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
            // T3 (S5) · La barra va en `surface` con un hairline `outlineVariant`
            // debajo —la del arteboard— en vez del `primaryContainer` de antes. El
            // contenedor `primaryContainer` queda para los chips seleccionados.
            Column {
                AppTopBar(
                    title = "Actividades",
                    onLogout = onLogout,
                    unreadCount = unreadCount,
                    onOpenNotifications = onOpenNotifications,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    actions = {
                        // T4 (S3) · El ícono de calendario abre la hoja con los días que
                        // tienen actividades. Se abre SIEMPRE en el mes de la fecha
                        // seleccionada, para que "Hoy" y el mes no se contradigan.
                        IconButton(
                            onClick = {
                                visibleMonthIso = selectedDate.toString()
                                showCalendar = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = "Abrir calendario para saltar a un día",
                            )
                        }
                        // El control segmentado de "Vista" se fue: ahora es un ícono.
                        // En modo Agenda ofrece la semana; en modo Semana, la lista.
                        IconButton(
                            onClick = {
                                onTimelineModeChange(
                                    if (timelineMode == TimelineMode.AGENDA) {
                                        TimelineMode.SEMANA
                                    } else {
                                        TimelineMode.AGENDA
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (timelineMode == TimelineMode.AGENDA) {
                                    Icons.Filled.CalendarViewWeek
                                } else {
                                    Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = if (timelineMode == TimelineMode.AGENDA) {
                                    "Vista: lista. Cambiar a semana"
                                } else {
                                    "Vista: semana. Cambiar a lista"
                                },
                            )
                        }
                        // T-FE-17 · El "+" abre el modal de alta (`openCreate`). Antes era un
                        // placeholder inerte (PRD §8); ya no lo es: el backend de `create` existe.
                        IconButton(onClick = onOpenCreate) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Nueva actividad",
                            )
                        }
                    },
                )
                HorizontalDivider(
                    thickness = Spacing.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
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
            // T3 (S5) · Los filtros son UNA línea de chips con contador. Los rótulos
            // "Vista" y "Filtrar" y el control segmentado se fueron con el nuevo cromo.
            ActivityFiltersRow(
                selectedTab = uiState.selectedTab,
                onSelectTab = onSelectTab,
                counts = tabCounts(uiState.actividades),
            )
            if (uiState.children.size > 1) {
                val childCounts = remember(uiState.actividades, uiState.selectedTab) {
                    childFilterCounts(uiState.actividades, uiState.selectedTab)
                }
                ChildrenFiltersRow(
                    children = uiState.children,
                    selectedChildren = uiState.selectedChildren,
                    counts = childCounts,
                    onSelectAll = onClearChildren,
                    onToggleChild = onToggleChild,
                )
            }
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
                        memberNames = uiState.memberNames,
                        children = uiState.children,
                        onSwipeAction = onSwipeAction,
                        onSelectDate = { date -> onSelectedDateChange(date.toString()) },
                        onPreviousWeek = { navigateWeek(-1) },
                        onNextWeek = { navigateWeek(1) },
                        focusRequesters = focusRequesters,
                        listState = listState,
                    )
                }
            }
        }
        }

        // T4 (S3) · La hoja de calendario se compone como HERMANA del Scaffold, dentro
        // de la misma `Box` que la lista. Es una ventana modal real: su scrim cubre el
        // feed y el árbol accesible queda acotado por ella (mismo patrón que la hoja de
        // detalle, que vive un nivel más arriba).
        if (showCalendar) {
            LaunchedEffect(Unit) { calendarSheetState.show() }
            CalendarJumpSheet(
                sheetState = calendarSheetState,
                today = today,
                visibleMonth = parseIsoDate(visibleMonthIso) ?: today,
                onMonthChange = { visibleMonthIso = it.toString() },
                // Los puntos son de los días con actividades DEL FILTRO ACTIVO: si el
                // usuario está en "Eventos", la hoja muestra los días con eventos.
                dayDots = dayDots,
                onSelectDate = { date ->
                    showCalendar = false
                    if (timelineMode == TimelineMode.SEMANA) {
                        // En modo Semana el día elegido SE SELECCIONA: la semana se
                        // reancla a ese día y la vista muestra su agenda.
                        onWeekStartChange(mondayStart(date).toString())
                        pendingWeekSelection = date
                    } else {
                        // En modo Agenda el día elegido es un SALTO: la lista se
                        // desplaza al encabezado de ese día (o al grupo más cercano
                        // hacia adelante, si ese día no tiene actividades).
                        pendingJumpDate = jumpTargetDate(date, agendaGroups)
                    }
                },
                onDismiss = { showCalendar = false },
            )
        }

        // El salto se APLICA cuando ya se sabe el índice del encabezado: los grupos
        // dependen de los datos, que pueden llegar después del toque.
        LaunchedEffect(pendingJumpDate, agendaGroups) {
            val target = pendingJumpDate ?: return@LaunchedEffect
            val index = agendaHeaderIndex(agendaGroups, target)
            if (index != null) listState.scrollToItem(index)
            pendingJumpDate = null
        }

        // La selección expresa de la hoja se aplica DESPUÉS de la regla que re-resuelve
        // el día al cambiar de semana. Este efecto está declarado después de aquél a
        // propósito: en el mismo frame, el orden de ejecución es el orden de declaración.
        LaunchedEffect(pendingWeekSelection, weekStartIso) {
            val picked = pendingWeekSelection ?: return@LaunchedEffect
            onSelectedDateChange(picked.toString())
            pendingWeekSelection = null
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
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onSwipeAction: (String, SwipeAction) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
                memberNames = memberNames,
                children = children,
                // R2-T2-7 · La LISTA es la superficie principal de la acción y hasta ahora
                // no tenía gesto: sólo lo tenía la agenda de día. Sin este argumento
                // `AgendaTimeline` ni siquiera compila.
                onSwipeAction = onSwipeAction,
                focusRequesters = focusRequesters,
                listState = listState,
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
                onSwipeAction = onSwipeAction,
                onSelectDate = onSelectDate,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                focusRequesters = focusRequesters,
            )
        }
    }
}

