package com.parental.shared.feature.activities.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.detail.ActividadDetailSheet
import com.parental.shared.feature.activities.ui.create.CreateActivitySheet
import com.parental.shared.feature.activities.ui.openedId
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.activities.ui.swipe.SwipeReasonDialog
import com.parental.shared.feature.activities.ui.swipe.SWIPE_UNDO_LABEL
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * T6 (S3, S6, S7) · Constantes del cromo del riel.
 *
 * `MINUTES_PER_HOUR` es la unidad del eje que ya usan `RailDerivation.kt` y `RailCanvas.kt`
 * (allí privadas): aquí sólo convierte la lectura del reloj en minuto de día. No se importa
 * de ninguno de los dos porque en los dos es `private`.
 *
 * `NOW_TICK_MILLIS` es el período del marcador de "ahora": un minuto, que es la resolución
 * con la que el eje dibuja la línea.
 */
private const val MINUTES_PER_HOUR = 60
private const val NOW_TICK_MILLIS = 60_000L

/**
 * Hueco reservado bajo el riel para los dos controles flotantes — el `.fabgap` del artboard
 * (`03-riel-del-tiempo.html:152`: "keeps the floating create button clear of every pill").
 *
 * Derivado, no medido a ojo: el FAB de Material mide 56dp y se apoya con 16dp de margen, así
 * que el objetivo táctil (48) más el peldaño grande de la escala (24) deja el contenido del
 * último día POR ENCIMA del FAB y del pill "Hoy" (S6 no admite un control que tape el riel).
 */
private val RAIL_FLOATING_CLEARANCE = AgendaGeometry.touchTargetLegacy + Spacing.xl

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
    // T6 · El riel todavía NO registra sus filas (`RailTimeline` no recibe este mapa), así que
    // hoy el retorno de foco cae siempre en el contenedor de la lista. El mecanismo se conserva
    // entero —`retainAll` por conjunto de datos y las dos caídas de `returnFocus`— para que
    // T7/T8 lo restablezcan sin volver a escribir la aritmética.
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }

    // T6 (L6) · Estado local de presentación. El riel es la ÚNICA vista, así que ya no hay un
    // modo que recordar; lo que se recuerda es el día marcado en el mapa de densidad, que es
    // el mismo que el mapa pinta como seleccionado. `selectedTab` sigue siendo del ViewModel.
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
            snackbarHostState = snackbarHostState,
            onNavigateBottom = onNavigateBottom,
            unreadCount = unreadCount,
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
            onOpenCreate = viewModel::openCreate,
            selectedDateIso = selectedDateIso,
            onSelectedDateChange = { selectedDateIso = it },
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
    snackbarHostState: SnackbarHostState,
    onNavigateBottom: (String) -> Unit,
    unreadCount: Int,
    onOpenNotifications: (() -> Unit)?,
    onLogout: () -> Unit,
    /**
     * T-FE-17 · Abre el modal de alta. Con T6 es el ÚNICO camino de alta de la pantalla: el
     * "+" de la barra se fue con S7 (un solo FAB, circular y abajo).
     */
    onOpenCreate: () -> Unit,
    selectedDateIso: String,
    onSelectedDateChange: (String) -> Unit,
    listFocusRequester: FocusRequester,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { currentDateIn(zone) }
    val selectedDate = parseIsoDate(selectedDateIso) ?: today

    // T6 (S4) · El mapa de densidad se pliega desde el pill de SU encabezado —el único
    // control (S4)— y el mes visible se recuerda como FECHA ISO, no como `LocalDate`: el
    // `LocalDate` no es `Saveable` y el mapa debe reabrirse en el mismo mes tras rotar.
    // Plegado por defecto: en un móvil el mapa abierto ocupa ~280dp de alto y el riel es el
    // contenido; S4 sólo pide que se muestre u oculte desde su pill, no que arranque abierto.
    var densityExpanded by rememberSaveable { mutableStateOf(false) }
    var visibleMonthIso by rememberSaveable { mutableStateOf(today.toString()) }
    // T6 (S5) · Días PLEGADOS, como lista de cadenas ISO. Es la forma más simple que un
    // `Saver` escribe en el bundle y la que no depende de que `LocalDate` sea serializable.
    // Se guarda la lista de días PLEGADOS (y no la de desplegados) para que el estado por
    // defecto sea "todos desplegados" con una lista vacía.
    var collapsedDaysIso by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it }),
    ) { mutableStateOf(emptyList()) }
    val collapsedDays = remember(collapsedDaysIso) { collapsedDaysIso.toSet() }

    val listState = rememberLazyListState()
    // El destino del salto se guarda un instante y lo consume un `LaunchedEffect`: el índice
    // del encabezado depende de las secciones, que cambian con los datos y con el plegado.
    var pendingJumpDate by remember { mutableStateOf<LocalDate?>(null) }

    // T6 (S3) · El reloj del riel se lee FUERA de composición, una vez por minuto. Arranca en
    // `null` —no en una lectura en el `remember`— y la primera lectura ocurre al lanzarse el
    // efecto. El `LaunchedEffect` se cancela solo cuando la pantalla deja la composición, así
    // que no queda un bucle vivo detrás.
    var nowMinute by remember(zone) { mutableStateOf<Int?>(null) }
    LaunchedEffect(zone) {
        while (true) {
            val now = Clock.System.now().toLocalDateTime(zone)
            nowMinute = now.hour * MINUTES_PER_HOUR + now.minute
            delay(NOW_TICK_MILLIS)
        }
    }

    val filteredRows = remember(uiState.actividades, uiState.selectedTab, uiState.selectedChildren, zone) {
        buildTimelineRows(
            filterActivities(uiState.actividades, uiState.selectedTab, uiState.selectedChildren),
            zone,
        )
    }
    // El agrupador es el MISMO de la agenda: días ascendentes y "sin fecha de inicio" al
    // final. El riel no ordena, sólo traduce cada grupo a su sección (T4).
    val dayGroups = remember(filteredRows) { buildAgendaGroups(filteredRows) }
    val sections = remember(dayGroups, zone, collapsedDays) {
        dayGroups.map { group ->
            val date = group.date
            if (date == null) {
                // La sección sin fecha no se pliega y no tiene eje horario.
                RailSection(date = null, railDay = null, undatedRows = group.rows)
            } else {
                RailSection(
                    date = date,
                    // Las filas del grupo son exactamente las de ese día, así que alimentan
                    // el eje sin volver a filtrar la lista entera por día.
                    railDay = buildRailDay(group.rows, date, zone),
                    isExpanded = date.toString() !in collapsedDays,
                )
            }
        }
    }
    val visibleMonth = parseIsoDate(visibleMonthIso) ?: today
    // La densidad sale de las filas FILTRADAS: si el usuario está en "Eventos", el mapa
    // cuenta eventos. Un mapa sobre `uiState.actividades` ignoraría la pestaña y el hijo.
    val density = remember(filteredRows, visibleMonth.year, visibleMonth.monthNumber, zone) {
        buildMonthDensity(filteredRows, visibleMonth.year, visibleMonth.monthNumber, zone)
    }
    // La línea de "ahora" sólo se pasa si HOY es una sección del riel: en un riel sin hoy
    // sería una hora sin eje que la pinte.
    val railNowMinute = if (sections.any { it.date == today }) nowMinute else null

    // S5 · Un día se pliega desde el chevron de SU encabezado y desde ningún otro sitio.
    val toggleDay: (LocalDate) -> Unit = { date ->
        val iso = date.toString()
        collapsedDaysIso = if (iso in collapsedDays) {
            collapsedDaysIso - iso
        } else {
            collapsedDaysIso + iso
        }
    }

    // S6 · "Hoy": despliega el día de hoy si estaba plegado y lleva el riel a su encabezado.
    // Las dos cosas ocurren en el MISMO toque: el desplegado cambia las secciones y el índice
    // se calcula después, en el efecto, sobre las secciones ya desplegadas.
    val goToToday: () -> Unit = {
        val iso = today.toString()
        if (iso in collapsedDays) collapsedDaysIso = collapsedDaysIso - iso
        pendingJumpDate = today
    }

    // El mes visible se mueve desde las flechas del mapa. Se navega desde el día 1 para que
    // el 31 de enero + 1 mes no herede el recorte de febrero.
    val shiftMonth: (Int) -> Unit = { delta ->
        val firstOfMonth = LocalDate(visibleMonth.year, visibleMonth.monthNumber, 1)
        visibleMonthIso = firstOfMonth.plus(delta, DateTimeUnit.MONTH).toString()
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
                        // T6 (S7, L6) · La barra se queda SIN acciones propias. El "+" se fue
                        // —el alta es el FAB ÚNICO de abajo— y el ícono de calendario se fue con la
                        // hoja modal: el mapa de densidad es el calendario de esta vista y el salto
                        // de día lo dan sus celdas y el pill "Hoy".
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
        // S7 + S6 · Dos controles flotantes en el mismo sitio: el pill "Hoy" a la izquierda y el
        // FAB de alta a la derecha, que es donde el artboard los pone. El FAB es CIRCULAR y
        // `primary` —"Poneme el boton de agregar en un circulo abajo"— y es el ÚNICO camino de
        // alta de la pantalla. El pill NO es un FAB (S7 pide uno solo) sino un botón elevado con
        // su palabra. Si hoy no tiene sección, el toque de "Hoy" no hace nada: ver `goToToday`,
        floatingActionButton = {
            Row(
                modifier = Modifier.padding(end = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RailTodayButton(today = today, onClick = goToToday)
                FloatingActionButton(
                    onClick = onOpenCreate,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva actividad",
                    )
                }
            }
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
            // T6 (S4) · El mapa de densidad va ARRIBA de los filtros y se pliega desde el pill
            // de su PROPIO encabezado: no hay un segundo control en ninguna otra parte ("que se
            // muestra u oculta desde el pill del encabezado del calendario").
            MonthDensityMap(
                year = visibleMonth.year,
                monthNumber = visibleMonth.monthNumber,
                density = density,
                selected = selectedDate,
                today = today,
                expanded = densityExpanded,
                onToggleExpanded = { densityExpanded = !densityExpanded },
                onSelectDay = { date ->
                    // Elegir un día en el mapa es un SALTO en el riel, no una vista nueva: si
                    // ese día no tiene actividades, se va al día con actividades más cercano
                    // —el mismo criterio que usaba la hoja modal de calendario—.
                    onSelectedDateChange(date.toString())
                    pendingJumpDate = jumpTargetDate(date, dayGroups)
                },
                onPrevMonth = { shiftMonth(-1) },
                onNextMonth = { shiftMonth(1) },
            )
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
            // T6 · El contenido ocupa el alto que queda y deja libre el hueco de los dos
            // controles flotantes (el `.fabgap` del artboard): así ni el FAB ni el pill "Hoy"
            // tapan el final del riel. La `Column` no scrollea —lo que scrollea es el riel—, así
            // que el pill queda siempre alcanzable sin taparlo todo.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                        } else RailTimeline(
                            // T6 (S3) · El riel es la ÚNICA vista de Actividades (L6). Las
                            // secciones ya vienen derivadas y en orden: el riel no agrupa ni
                            // ordena, sólo pinta encabezados pegajosos y cuerpos de día.
                            sections = sections,
                            categories = uiState.categories,
                            memberNames = uiState.memberNames,
                            children = uiState.children,
                            zone = zone,
                            today = today,
                            nowMinute = railNowMinute,
                            // El estado de la lista lo posee la pantalla: es lo que mueve el
                            // salto a un día con el índice de `railItemIndexOf`.
                            listState = listState,
                            onOpen = { row -> onSelectActividad(row.actividad.id) },
                            onToggleDay = toggleDay,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(RAIL_FLOATING_CLEARANCE))
        }
        }

        // T6 (S6) · El salto se APLICA cuando ya se sabe el índice del encabezado: las
        // secciones dependen de los datos y del plegado, que pueden cambiar después del toque.
        // `railItemIndexOf` es la MISMA cuenta que monta el riel (`RailIndex.kt`), así que el
        // destino no puede desalinearse de la lista.
        LaunchedEffect(pendingJumpDate, sections) {
            val target = pendingJumpDate ?: return@LaunchedEffect
            val index = railItemIndexOf(sections, target)
            if (index != null) listState.scrollToItem(index)
            pendingJumpDate = null
        }
    }
}

/**
 * T6 (S6) · El pill "Hoy": un botón ELEVADO, no un FAB (S7 pide uno solo).
 *
 * Hace las dos cosas que el artboard le pide a su `#hoyBtn`
 * (`03-riel-del-tiempo.html:219`, `aria-label="Ir a hoy, miércoles 13 de mayo"`): despliega el
 * día de hoy si estaba plegado y lleva el riel a su encabezado. La etiqueta accesible nombra
 * el DÍA —no sólo repite la palabra del botón— y sale de `formatDayTitle`, el mismo
 * formateador del encabezado del día.
 *
 * ⚠ `ElevatedButton` y no una `Surface` con `clickable`: trae el rol de botón, el estado de
 * pulsado y la elevación del tema sin que este archivo declare un dp de sombra. El
 * `heightIn` con el objetivo táctil mantiene el piso de S11 aunque un tema encoja el botón.
 */
@Composable
private fun RailTodayButton(today: LocalDate, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = Modifier
            .heightIn(min = AgendaGeometry.touchTargetLegacy)
            .semantics { contentDescription = "Ir a hoy, ${formatDayTitle(today)}" },
    ) {
        Icon(
            imageVector = Icons.Filled.Today,
            contentDescription = null,
            modifier = Modifier.size(Spacing.lg),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = "Hoy",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
