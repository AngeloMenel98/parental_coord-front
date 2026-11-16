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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.RailGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.detail.ActividadDetailSheet
import com.parental.shared.feature.activities.ui.create.CreateActivitySheet
import com.parental.shared.feature.activities.ui.openedId
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.activities.ui.swipe.SwipeReasonDialog
import com.parental.shared.feature.activities.ui.swipe.SWIPE_UNDO_LABEL
import com.parental.shared.feature.bonds.domain.model.VinculoChild
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
 * Fracción del alto del riel donde se posa la línea del anclaje inicial — 0.45, el
 * `Math.round(host.clientHeight*0.45)` del `landOnNow()` del artboard (`03-riel-del-tiempo.html`).
 *
 * Deja contexto ARRIBA de la línea: cuando hay algo en curso, lo que importa es la actividad y
 * sus vecinas, no el borde superior del eje.
 */
private const val RAIL_INITIAL_ANCHOR_VIEWPORT_FRACTION = 0.45f

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
    // R4-T1-2 · id → `FocusRequester` del bloque del riel que lo pintó; T7 lo lee al cerrar la
    // hoja. El mapa lo crea ESTA pantalla y lo llena cada bloque de `RailDayCanvas` / la fila sin
    // fecha de `RailTimeline` con el MISMO patrón que `ActivityCard` (`remember` + `SideEffect`),
    // así que escribir no recompone a nadie. La poda vive en `ActividadesListScreen`, donde se
    // conocen las secciones: es el conjunto de datos que de verdad se renderiza.
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
    //
    // T7 · La poda se mudó a `ActividadesListScreen`, donde viven las SECCIONES: antes el mapa se
    // podaba contra `uiState.actividades` (el conjunto sin filtrar), así que un id que el filtro
    // de pestaña o de hijo acababa de esconder conservaba un requester de un nodo que ya no está.
    // La regla es la misma; cambia el conjunto de datos contra el que se mide, y ahora es el que
    // el riel dibuja.

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
            // R2-T2-7 · ÚNICO punto por el que el riel dispara una acción de swipe. Callback, no
            // singleton: el bloque sigue siendo función pura de sus datos (T7).
            onSwipeAction = viewModel::startSwipeAction,
            // T7 (R4) · El mapa de foco de la pantalla, para que cada bloque del riel pueda
            // registrar su `FocusRequester`: es lo que hace que el retorno desde la hoja de
            // detalle vuelva al bloque que la abrió en vez de caer en el contenedor.
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
    /**
     * R2-T2-7 · Callback del gesto del riel (T7). Se pasa como callback y no se lee de un
     * singleton: el bloque sigue siendo función pura de sus datos y este archivo no sabe qué
     * acciones existen, sólo las reenvía. El tipo es [SwipeAction], así que añadir una acción
     * obliga a tocar aquí a propósito y no por olvido.
     */
    onSwipeAction: (String, SwipeAction) -> Unit,
    /**
     * R4 · Destino de foco por actividad, llenado por los bloques del riel. El mapa lo posee la
     * pantalla exterior (es quien lo lee al cerrar la hoja) y aquí sólo se pasa y se poda.
     */
    focusRequesters: MutableMap<String, FocusRequester>,
    listFocusRequester: FocusRequester,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { currentDateIn(zone) }
    val selectedDate = parseIsoDate(selectedDateIso) ?: today

    // T6 (S4) · El mapa de densidad se pliega desde el chevron de SU encabezado —el único
    // control (S4/S13)— y el mes visible se recuerda como FECHA ISO, no como `LocalDate`: el
    // `LocalDate` no es `Saveable` y el mapa debe reabrirse en el mismo mes tras rotar.
    //
    // T10 · PLEGADO por defecto, y la razón es una medida: a 384dp de ancho el panel desplegado
    // mide ~513dp (encabezado + días + hasta seis semanas de celdas de 44dp + leyenda) y el riel
    // —el contenido de la pantalla— se queda en ~130dp. El mapa es una LENTE OCASIONAL, no el
    // estado de reposo. Los DOS filtros, que sí son permanentes, se montan dentro del panel pero
    // FUERA del plegado (`MonthDensityMap.filters`), así que plegar no los esconde.
    var densityExpanded by rememberSaveable { mutableStateOf(false) }
    var visibleMonthIso by rememberSaveable { mutableStateOf(today.toString()) }
    // T10 (S12) · El usuario tocó "Hoy" con el día vacío: el riel tiene que DECIRLO, no saltar en
    // silencio. Se guarda la INTENCIÓN y se cruza con los bloques de hoy al pintar, así que el
    // aviso desaparece solo si hoy pasa a tener actividades y no queda pegado al riel.
    var todayEmptyNotice by rememberSaveable { mutableStateOf(false) }
    // T6 (S5) · Días PLEGADOS, como lista de cadenas ISO. Es la forma más simple que un
    // `Saver` escribe en el bundle y la que no depende de que `LocalDate` sea serializable.
    // Se guarda la lista de días PLEGADOS (y no la de desplegados) para que el estado por
    // defecto sea "todos desplegados" con una lista vacía.
    var collapsedDaysIso by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it }),
    ) { mutableStateOf(emptyList()) }
    val collapsedDays = remember(collapsedDaysIso) { collapsedDaysIso.toSet() }

    // El destino del salto se guarda un instante y lo consume un `LaunchedEffect` DENTRO del
    // riel (allí vive el `LazyListState`, ver `RailAnchoredTimeline`): el índice del encabezado
    // depende de las secciones, que cambian con los datos y con el plegado.
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
    // T7 (R4) · Poda del mapa de foco por CONJUNTO DE DATOS, y ahora por el conjunto que el riel
    // RENDERIZA. Podar al cerrar borraría la entrada de un bloque que sigue compuesto y que no
    // volvería a registrarse —`SideEffect` sólo corre al recomponer—, así que el retorno caería al
    // contenedor por un motivo inventado. Con las secciones como medida, un id que sale del riel
    // (recarga, filtro de pestaña o de hijo) pierde su requester y no puede volver a pedir foco;
    // lo que queda vivo se reinscribe solo. Un día PLEGADO conserva las suyas: su `railDay` sigue
    // en la sección, y si el bloque no está compuesto el segundo respaldo de `returnFocus`
    // (`onFailure`) cubre el nodo desprendido. Queda acotado.
    val railVisibleIds = remember(sections) {
        sections.flatMap { section ->
            section.railDay?.blocks?.map { block -> block.row.actividad.id }
                ?: section.undatedRows.map { row -> row.actividad.id }
        }.toSet()
    }
    LaunchedEffect(railVisibleIds) {
        focusRequesters.keys.retainAll(railVisibleIds)
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

    // T10 (S12) · Si hoy tiene siquiera un bloque. Una sección de hoy EXISTE aunque esté vacía,
    // así que "hay sección" no sirve: lo que decide es que tenga bloques.
    val todayHasBlocks = sections.any { section ->
        section.date == today && !section.railDay?.blocks.isNullOrEmpty()
    }
    // El aviso se pinta sólo si se pidió Y hoy sigue vacío: si una recarga o un filtro le trae
    // actividades a hoy, el aviso se reemplaza solo.
    val showTodayEmptyNotice = todayEmptyNotice && !todayHasBlocks
    // T10 (S15) · La DECISIÓN de anclaje es pura (`railInitialAnchor`); aquí sólo se toma cuando
    // el reloj ya dio su primera lectura. Se calcula en composición pero NO se consume hasta
    // montar el riel: `RailAnchoredTimeline` la usa como posición INICIAL del `LazyListState`, así
    // que el primer frame con contenido ya está anclado y no hay scroll visible después.
    val initialRailAnchor = nowMinute?.let { minute -> railInitialAnchor(sections, today, minute) }

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
    //
    // T10 (S12) · Si hoy no tiene ni un bloque, el toque además deja el aviso: con el anclaje de
    // S15 el riel muchas veces ya está en hoy, así que un salto mudo parecía un botón roto.
    val goToToday: () -> Unit = {
        val iso = today.toString()
        if (iso in collapsedDays) collapsedDaysIso = collapsedDaysIso - iso
        todayEmptyNotice = !todayHasBlocks
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
        // S7 + S6 · El FAB de alta vive en el slot del `Scaffold`: es CIRCULAR y `primary`
        // —"Poneme el boton de agregar en un circulo abajo"— y es el ÚNICO camino de alta de la
        // pantalla. El pill "Hoy" NO va aquí: en el artboard aprobado está abajo a la IZQUIERDA,
        // espejado respecto del FAB, y el slot `floatingActionButton` alinea a la derecha — con
        // los dos dentro de un `Row` el pill quedaba pegado al FAB. Ahora el pill se monta en el
        // contenido, anclado a `BottomStart` (ver el `Box` de abajo).
        floatingActionButton = {
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
        },
        // R-8 / G-1 · Un ÚNICO `SnackbarHost` en toda la pantalla. La lista lo hospeda sólo
        // cuando NO hay hoja; mientras hay hoja lo hospeda la hoja. Dos hosts siempre activos
        // serían doble pintada y doble anuncio.
        snackbarHost = { if (uiState.detail is DetailUiState.Closed) SnackbarHost(snackbarHostState) },
    ) { padding ->
        // T10 (S6) · El pill "Hoy" va abajo a la IZQUIERDA, espejado respecto del FAB, como en
        // el artboard aprobado. NO va en el slot `floatingActionButton`: ese slot alinea a la
        // derecha y con los dos dentro de un `Row` el pill quedaba pegado al FAB. Vive en la
        // ÚLTIMA franja del contenido —el mismo hueco de `RAIL_FLOATING_CLEARANCE` que ya reserva
        // el sitio de los flotantes—, así que no tapa ni un renglón del riel.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // T6 (S4) · El mapa de densidad va ARRIBA del riel y se pliega desde el chevron
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
                    // T10 (S12) · Elegir día REEMPLAZA el aviso de "hoy vacío": el usuario ya
                    // está mirando otra fecha y el aviso de hoy sólo estorbaría.
                    todayEmptyNotice = false
                    pendingJumpDate = jumpTargetDate(date, dayGroups)
                },
                onPrevMonth = { shiftMonth(-1) },
                onNextMonth = { shiftMonth(1) },
                // T10 (S14) · Los DOS filtros viven DENTRO del panel del calendario —debajo del
                // encabezado y arriba de la zona plegable—, así que NO desaparecen al plegar el
                // mapa: son permanentes, el mapa es la lente ocasional. Se reutilizan las filas
                // existentes TAL CUAL: misma selección, mismos contadores y mismos callbacks; lo
                // único que cambia es el sitio donde se montan.
                filters = {
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
                },
            )
            // T6 · El contenido ocupa el alto que queda y deja libre el hueco de los dos
            // controles flotantes (el `.fabgap` del artboard): así ni el FAB ni el pill "Hoy"
            // tapan el final del riel. La `Column` no scrollea —lo que scrollea es el riel—, así
            // que el pill queda siempre alcanzable sin taparlo todo.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // T7 (S11) · LOS TRES ESTADOS SE EXCLUYEN. Una sola `when` con cuatro ramas
                // —carga, error, vacío, contenido— y ningún estado montado como HERMANO del
                // riel: si el esqueleto o el vacío se compusieran junto al riel, el usuario
                // vería la lista debajo de (o encima de) un cartel que dice que todavía no hay
                // lista. El orden de precedencia es el de siempre y el `guard` de la rama 1
                // (`isLoading && isEmpty`) impide que una recarga con datos tape lo que ya se ve.
                when {
                    uiState.isLoading && uiState.actividades.isEmpty() -> RailSkeleton()
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
                    //
                    // T7 · El vacío deja de ser un cartel muerto: ofrece el alta con el
                    // MISMO callback que el FAB (S7: un solo camino de alta, y este es su
                    // atajo cuando el FAB está lejos del pulgar).
                    else -> Crossfade(
                        targetState = uiState.actividades.isEmpty(),
                        animationSpec = tween(MotionTokens.durationStandard),
                        label = "listEmptyToContent",
                    ) { isEmpty ->
                        if (isEmpty) {
                            EmptyState(
                                title = "No hay actividades",
                                message = "",
                                actionLabel = "Crear actividad",
                                onAction = onOpenCreate,
                            )
                        } else {
                            // T10 (S15) · SIN RELOJ TODAVÍA NO HAY ANCLAJE, y montar el riel sin
                            // anclarlo para corregirlo después sería exactamente el salto visible
                            // que S15 prohíbe. El reloj llega en el primer tick del
                            // `LaunchedEffect` de arriba, así que esta rama dura un frame como
                            // mucho y no pinta nada (el esqueleto del estado de carga ya cumplió).
                            val minute = nowMinute
                            if (minute == null) {
                                Spacer(modifier = Modifier.fillMaxSize())
                            } else {
                                // T6 (S3) · El riel es la ÚNICA vista de Actividades (L6). Las
                                // secciones ya vienen derivadas y en orden: el riel no agrupa ni
                                // ordena, sólo pinta encabezados pegajosos y cuerpos de día.
                                RailAnchoredTimeline(
                                    sections = sections,
                                    initialAnchor = initialRailAnchor,
                                    pendingJumpDate = pendingJumpDate,
                                    onJumpApplied = { pendingJumpDate = null },
                                    categories = uiState.categories,
                                    memberNames = uiState.memberNames,
                                    children = uiState.children,
                                    zone = zone,
                                    today = today,
                                    nowMinute = railNowMinute,
                                    // T10 (S12) · El aviso de "hoy sin actividades" y su acción,
                                    // que es el MISMO alta que el FAB (S7).
                                    todayEmptyNotice = showTodayEmptyNotice,
                                    onCreateFromEmptyToday = onOpenCreate,
                                    onOpen = { row -> onSelectActividad(row.actividad.id) },
                                    onToggleDay = toggleDay,
                                    // T7 (R2-T2-7, L11) · El gesto vuelve a tener superficie: la
                                    // agenda lo llevaba en sus filas y el riel lo monta en sus
                                    // bloques, con el motivo obligatorio y la oferta de deshacer
                                    // que la pantalla ya tenía cableadas.
                                    onSwipeAction = onSwipeAction,
                                    // T7 (R4) · El mapa de foco, para que el retorno desde la
                                    // hoja vuelva al bloque que la abrió.
                                    focusRequesters = focusRequesters,
                                )
                            }
                        }
                    }
                }
            }
            // T10 (S6) · El pill "Hoy" cierra la columna, abajo a la IZQUIERDA y espejado respecto
            // del FAB (que el `Scaffold` ancla a la derecha). Vive en la franja que ya reservaba
            // `RAIL_FLOATING_CLEARANCE` —72dp de alto y sin contenido—, así que no tapa texto del
            // último bloque: el riel termina POR ENCIMA de esa franja.
            Box(
                modifier = Modifier.fillMaxWidth().height(RAIL_FLOATING_CLEARANCE),
            ) {
                RailTodayButton(
                    today = today,
                    onClick = goToToday,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.lg, bottom = Spacing.lg),
                )
            }
        }
        }
    }
}

/**
 * El riel montado con su `LazyListState` ANCLADO — T10 (S15).
 *
 * POR QUÉ UN COMPOSABLE APARTE Y NO UN `LaunchedEffect` EN LA PANTALLA: la posición inicial se
 * pasa a `rememberLazyListState` como posición de CREACIÓN, así que el primer layout del riel ya
 * está en la línea de "ahora" (o en el primer bloque) y no hay un scroll visible después. Un
 * `scrollToItem` desde un efecto llegaría un frame tarde: el usuario vería el tope de la lista y
 * un salto. Esto exige que el anclaje ya esté decidido al montar, y por eso la pantalla no monta
 * el riel hasta que el reloj dio su primera lectura.
 *
 * El estado vive aquí, no en la pantalla, por la misma razón: `rememberLazyListState` sólo lee sus
 * inicializadores al crearse. Se re-crea si el riel deja la composición (lista vacía o error), que
 * es justo cuando un contenido nuevo merece volver a abrir anclado.
 *
 * @param initialAnchor decisión PURA de `railInitialAnchor`; `null` deja la lista en el tope.
 * @param pendingJumpDate salto pedido desde el mapa o desde "Hoy"; lo consume el efecto y lo
 *   limpia con [onJumpApplied].
 */
@Composable
private fun RailAnchoredTimeline(
    sections: List<RailSection>,
    initialAnchor: RailAnchor?,
    pendingJumpDate: LocalDate?,
    onJumpApplied: () -> Unit,
    categories: Map<String, CategoryInfo>,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    zone: TimeZone,
    today: LocalDate,
    nowMinute: Int?,
    todayEmptyNotice: Boolean,
    onCreateFromEmptyToday: () -> Unit,
    onOpen: (TimelineRow) -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
) {
    // `maxHeight` es el alto REAL del riel en esta pantalla (el `weight(1f)` de arriba): es el
    // único sitio donde el anclaje puede saber cuánto mide su viewport antes del primer layout.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val initialIndex = initialAnchor?.let { anchor -> railItemIndexOf(sections, anchor.date) } ?: 0
        val initialOffsetPx = railAnchorOffsetPx(
            anchor = initialAnchor,
            sections = sections,
            viewportHeightPx = viewportHeightPx,
            pxPerDp = density.density,
        )
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffsetPx,
        )
        RailTimeline(
            sections = sections,
            categories = categories,
            memberNames = memberNames,
            children = children,
            zone = zone,
            today = today,
            nowMinute = nowMinute,
            // Este estado es lo que mueve el salto a un día con el índice de `railItemIndexOf`.
            listState = listState,
            onOpen = onOpen,
            onToggleDay = onToggleDay,
            onSwipeAction = onSwipeAction,
            focusRequesters = focusRequesters,
            todayEmptyNotice = todayEmptyNotice,
            onCreateFromEmptyToday = onCreateFromEmptyToday,
        )

        // T6 (S6) · El salto se APLICA cuando ya se sabe el índice del encabezado: las secciones
        // dependen de los datos y del plegado, que pueden cambiar después del toque.
        // `railItemIndexOf` es la MISMA cuenta que monta el riel (`RailIndex.kt`), así que el
        // destino no puede desalinearse de la lista.
        LaunchedEffect(pendingJumpDate, sections) {
            val target = pendingJumpDate ?: return@LaunchedEffect
            val index = railItemIndexOf(sections, target)
            if (index != null) listState.scrollToItem(index)
            onJumpApplied()
        }
    }
}

/**
 * Desplazamiento en píxeles para que la línea del anclaje caiga a
 * [RAIL_INITIAL_ANCHOR_VIEWPORT_FRACTION] del alto del riel — el `landOnNow()` del artboard.
 *
 * Devuelve 0 cuando no hay anclaje, cuando el día está PLEGADO (su contenido no existe, así que
 * desplazar en píxeles pondría el encabezado de otro día bajo el borde) o cuando el minuto cae
 * antes de la franja que deja aire arriba. Nada de píxeles no enteros: `scrollOffset` es `Int`.
 */
private fun railAnchorOffsetPx(
    anchor: RailAnchor?,
    sections: List<RailSection>,
    viewportHeightPx: Float,
    pxPerDp: Float,
): Int {
    if (anchor == null) return 0
    val section = sections.firstOrNull { candidate -> candidate.date == anchor.date } ?: return 0
    if (!section.isExpanded) return 0
    val axis = section.railDay?.axis ?: return 0
    val linePx = railOffsetDp(anchor.minuteOfDay, axis, RailGeometry.DpPerHour) * pxPerDp
    return (linePx - viewportHeightPx * RAIL_INITIAL_ANCHOR_VIEWPORT_FRACTION)
        .coerceAtLeast(0f)
        .toInt()
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
 *
 * T10 · El `modifier` existe para que la pantalla lo ancle abajo a la IZQUIERDA: el artboard lo
 * espeja respecto del FAB, y el slot `floatingActionButton` sólo alinea a la derecha.
 */
@Composable
private fun RailTodayButton(today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier
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
