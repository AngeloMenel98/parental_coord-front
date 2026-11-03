package com.parental.shared.feature.activities.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.parental.shared.core.files.FileData
import com.parental.shared.core.files.FilePicker
import com.parental.shared.core.files.rememberFilePicker
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.DetailGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.activities.domain.model.isAttachmentsEditable
import com.parental.shared.feature.activities.ui.attachments.AttachmentsSection
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.SheetGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.resolveChildNames
import com.parental.shared.feature.activities.domain.model.visibleDeclinedReason
import com.parental.shared.feature.activities.ui.*
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Hoja de detalle — `activity-detail-modal`, T2-1. Sustituye a `ActividadDetailScreen` (T3-1).
 * Decisiones que no se deducen del código:
 *
 * ⚠ D-A, DELTA VISUAL CERO. `sheetMaxWidth = Spacing.maxContentWidth` (640dp) ES el valor por
 * defecto de la biblioteca, así que el ancho no cambia; por eso NO se fuerza el centrado (la
 * biblioteca ya lo hace) ni se pasa un ancho sin especificar (eso sí rompería el diseño).
 *
 * ⚠ D-B, EL `SheetState` LO POSEe EL HOST. `onDismissRequest` es su `finishDismiss` idempotente
 * y la "X" NO llama a `hide()` a pelo: pasa por [requestDismiss], que replica el `animateToDismiss`
 * de la biblioteca —con su comprobación de visibilidad al final— pero no es un espejo LITERAL: el
 * guard va dentro del cuerpo de la corutina, no en `invokeOnCompletion` (igual o más segura: una
 * suspensión cancelada nunca alcanza la sentencia siguiente), y la biblioteca lo antecede de
 * `confirmValueChange(Hidden)`, que aquí es no-op con el `rememberModalBottomSheetState` por defecto.
 * Un `hide()` sin observador deja una ventana `Dialog` invisible tragando la entrada (§1.6).
 *
 * ⚠ Sin animación explícita (la diapositiva la lleva `SheetState`) y sin `Scaffold`, `TopAppBar`,
 * `BottomNavBar`, `onNavigateBottom` ni el rótulo "Detalle": la lista conserva su chrome (R11).
 */

/**
 * Estados en los que la actividad ya no admite "No asistir".
 */
private val TERMINAL_STATUSES = setOf("done", "cancelled", "not_assisting")

/** T21 · Selector de miembro para "Asignar" o "Reasignar". */
private sealed interface AssignPickerState {
    data class Assign(val activityId: String) : AssignPickerState
    data class Reassign(val activityId: String) : AssignPickerState
}

/**
 * Raíz de la hoja — `activity-detail-modal`, T2-1.
 *
 * ⚠ R-13 (tope de altura): el `0.6f` vive AHORA en el token [SheetGeometry] (`create-activity`
 * T-FE-15), compartido con la hoja de alta. La explicación completa —por qué es una relación y
 * no `Dp`, y por qué NO se arregla con `skipPartiallyExpanded = false`— está junto al token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActividadDetailSheet(
    state: DetailUiState,
    sheetState: SheetState,
    resolve: (String) -> CategoryInfo?,
    memberNames: Map<String, String>,
    snackbarHostState: SnackbarHostState,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onInertAction: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    onComplete: (String) -> Unit,
    isCompleting: Boolean,
    onDecline: (String) -> Unit = {},
    /** T21 · `POST /:id/assign` en vuelo. */
    isAssigning: Boolean = false,
    /** T21 · `POST /:id/reassign` en vuelo. */
    isReassigning: Boolean = false,
    attachments: List<ActivityAttachmentDto> = emptyList(),
    attachmentsLoading: Boolean = false,
    onLoadAttachments: (String) -> Unit = {},
    onAddAttachment: (String, String, String, ByteArray, (ActivityAttachmentDto) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onCreateExpenseFromAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onRenameAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onReplaceAttachment: (String, String, String, String, ByteArray) -> Unit = { _, _, _, _, _ -> },
    onDeleteAttachment: (String, String) -> Unit = { _, _ -> },
    /** S9 · D-5 — hijos del vínculo, para resolver `childrenIds` a nombres. */
    children: List<VinculoChild> = emptyList(),
    /** T21 · candidatos a "Asignar a" / "Reasignar a" — el MISMO que usa el alta. */
    memberCandidates: List<MemberCandidate> = emptyList(),
    onAssign: (String, String?) -> Unit = { _, _ -> },
    onReassign: (String, String?) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
    // El selector de archivos se crea ACÁ, en la raíz de la hoja, y no dentro de
    // `DetailContent`/`DetailFooter`: allí vivía dentro de un `item {}` del
    // `LazyColumn`, y si ese item salía de composición el launcher se
    // desregistraba con la selección en curso. Un solo picker para las dos CTAs.
    val picker = rememberFilePicker()
    var pendingExpenseChoice by remember { mutableStateOf<Pair<String, FileData>?>(null) }
    val pickAttachment: (String, FileData) -> Unit = { activityId, file ->
        pendingExpenseChoice = activityId to file
    }
    // T21 · Selector de miembro para "Asignar" / "Reasignar". `null` = cerrado.
    var assignPicker by remember { mutableStateOf<AssignPickerState?>(null) }
    fun savePickedAttachment(asExpense: Boolean) {
        val (activityId, file) = pendingExpenseChoice ?: return
        pendingExpenseChoice = null
        onAddAttachment(activityId, file.name, file.mimeType, file.bytes) { attachment ->
            if (asExpense) onCreateExpenseFromAttachment(activityId, attachment.id, file.name)
        }
    }
    // El `onDismissRequest` del host se recrea en cada recomposición (captura el `id` vivo):
    // sin esto el lambda de la "X" podría invocar un descarte ya obsoleto.
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val requestDismiss: () -> Unit = {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) currentDismiss()
        }
    }
    // R4 · El foco ENTRA en la hoja al abrir, sobre "Cerrar". Guardado: si el nodo no existe no
    // debe propagar la excepción.
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = Spacing.maxContentWidth,
        dragHandle = null,
    ) {
        // Concha SIEMPRE compuesta: la fila de cierre sobrevive a la transición entre estados,
        // que es donde se nota un salto si sólo se compone el contenido.
        //
        // R-13 · `BoxWithConstraints` sólo aporta el `maxHeight` de la ventana VIGENTE: es lo
        // que hace que el tope escale. El `Box` no aporta layout —`propagateMinConstraints` es
        // `false` por defecto y su único hijo es esta columna, luego mide lo que mide—.
        BoxWithConstraints {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * SheetGeometry.maxHeightRatio),
            ) {
                DetailHeader(
                    onClose = requestDismiss,
                    closeFocus = closeFocus,
                )
                when (state) {
                    DetailUiState.Closed -> Spacer(Modifier.height(Spacing.lg))
                    // R-13 · `fill = false`: el esqueleto ENVUELVE sus ~116 dp de barras en vez
                    // de tensar el hueco, así que `Loading` abre compacto en vez de dejar 0.6 de
                    // ventana muerta debajo. `Content` y `Error` no cambian: el `LazyColumn` y el
                    // `EmptyState` se llenan ellos. El peso sigue siendo 1f, luego la transición
                    // `Loading`→`Content` no salta —el hueco existe igual—.
                    is DetailUiState.Loading -> DetailSkeleton(Modifier.weight(1f, fill = false))
                    // ⚠ `EmptyState` aplica `fillMaxHeight()` (`CalendarChrome.kt:112`) y aquí se
                    // emite SIN `weight`: estira hasta el máximo de la columna, que con el tope
                    // R-13 es 0.6·ventana y NO la ventana entera. "Reintentar" vuelve a verse —
                    // a verificar en dispositivo (G-27), no a suponer. No se le pasa un alto
                    // compacto: ese `fillMaxHeight()` se usa en 5 sitios y quitarlo regresa la lista.
                    is DetailUiState.Error -> EmptyState(
                        title = "No pudimos cargar el detalle",
                        message = state.message,
                        actionLabel = "Reintentar",
                        onAction = onRetry,
                    )
                    // `weight` es extensión de `ColumnScope`: sólo resuelve dentro del lambda de
                    // `Column`, así que se pasa ya aplicado desde este sitio de llamada.
                    is DetailUiState.Content -> {
                        LaunchedEffect(state.id) { onLoadAttachments(state.id) }
                        DetailContent(
                            state = state,
                            resolve = resolve,
                            memberNames = memberNames,
                            children = children,
                            onInertAction = onInertAction,
                            attachments = attachments,
                            onAddAttachment = pickAttachment,
                            onRenameAttachment = onRenameAttachment,
                            onReplaceAttachment = onReplaceAttachment,
                            onDeleteAttachment = onDeleteAttachment,
                            picker = picker,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Un único `SnackbarHost` VISIBLE a la vez (R-8): la lista lo hospeda cuando no hay
                // hoja y la hoja mientras está abierta. Dos activos = doble pintada + doble anuncio.
                SnackbarHost(snackbarHostState)
                if (state is DetailUiState.Content) {
                    DetailFooter(
                        actividad = state.actividad,
                        onInertAction = onInertAction,
                        onConfirm = onConfirm,
                        isConfirming = isConfirming,
                        onComplete = onComplete,
                        isCompleting = isCompleting,
                        onDecline = onDecline,
                        // Sin este cable el CTA caía en el lambda por defecto
                        // (no-op) y el botón no hacía NADA aunque el picker
                        // devolviera un archivo.
                        onPickAttachment = pickAttachment,
                        picker = picker,
                        memberCandidates = memberCandidates,
                        onOpenAssignPicker = { assignPicker = AssignPickerState.Assign(state.actividad.id) },
                        onOpenReassignPicker = { assignPicker = AssignPickerState.Reassign(state.actividad.id) },
                        isAssigning = isAssigning,
                        isReassigning = isReassigning,
                    )
                }
            }
        }
        if (pendingExpenseChoice != null) {
            AlertDialog(
                onDismissRequest = { savePickedAttachment(asExpense = false) },
                title = { Text("¿Es un gasto?") },
                text = { Text("Podés crear el gasto con este comprobante. El archivo también quedará en la actividad.") },
                confirmButton = {
                    TextButton(onClick = { savePickedAttachment(asExpense = true) }) { Text("Sí") }
                },
                dismissButton = {
                    TextButton(onClick = { savePickedAttachment(asExpense = false) }) { Text("No") }
                },
            )
        }
        val pickerTarget = assignPicker
        if (pickerTarget != null) {
            val title = when (pickerTarget) {
                is AssignPickerState.Assign -> "Asignar a"
                is AssignPickerState.Reassign -> "Reasignar a"
            }
            AlertDialog(
                onDismissRequest = { assignPicker = null },
                title = { Text(title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Elegí quién se hace cargo de esta actividad.")
                        memberCandidates.forEach { candidate ->
                            TextButton(
                                onClick = {
                                    when (pickerTarget) {
                                        is AssignPickerState.Assign -> onAssign(pickerTarget.activityId, candidate.id)
                                        is AssignPickerState.Reassign -> onReassign(pickerTarget.activityId, candidate.id)
                                    }
                                    assignPicker = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(candidate.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { assignPicker = null }) { Text("Cancelar") }
                },
            )
        }
    }
}

/** Fila de cierre: espacio flexible + la "X" al borde. ⚠ SIN rótulo de actividad aquí: el título
 *  se pinta y se ANUNCA una sola vez, en el cuerpo (`headlineSmall`), que es donde lo lleva el
 *  diseño. Repetirlo en la cabecera lo duplicaba en pantalla y lo hacía anunciar dos veces al
 *  abrir la hoja. */
@Composable
private fun DetailHeader(onClose: () -> Unit, closeFocus: FocusRequester) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onClose,
            // 48dp heredado del token: G-3, sin literal.
            modifier = Modifier.focusRequester(closeFocus).heightIn(min = AgendaGeometry.touchTargetLegacy),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
        }
    }
}

/** Cuerpo desplazable del `Content` — la anatomía de la hoja, no una copia de la lista. */
@Composable
private fun DetailContent(
    state: DetailUiState.Content,
    resolve: (String) -> CategoryInfo?,
    memberNames: Map<String, String>,
    onInertAction: (String) -> Unit,
    children: List<VinculoChild> = emptyList(),
    attachments: List<ActivityAttachmentDto> = emptyList(),
    onAddAttachment: (String, FileData) -> Unit = { _, _ -> },
    onRenameAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onReplaceAttachment: (String, String, String, String, ByteArray) -> Unit = { _, _, _, _, _ -> },
    onDeleteAttachment: (String, String) -> Unit = { _, _ -> },
    picker: FilePicker,
    modifier: Modifier = Modifier,
) {
    val a = state.actividad
    val isSalud = resolve(a.categoryId)?.name.equals("Salud", ignoreCase = true)
    val pairs = detailPairs(a, memberNames, children)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.lg, Spacing.md, Spacing.lg, Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(
                text = "${a.code} · " + if (a.type == "obligation") "Obligación" else "Evento",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        item {
            Text(a.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusTag(status = a.status)
                CategoryBadge(categoryId = a.categoryId, resolve = resolve)
            }
        }
        // S3/T18 · LA VENTANA DE VERIFICACIÓN. Es la MISMA fuente que la tarjeta —`deadline`,
        // que el servidor reancla a `now + 36 h` al entrar en `verify` (R14)—, no un segundo
        // reloj: el cliente no calcula cuándo vence, sólo lee cuándo vence. Si el backend no
        // manda `deadline`, la tarjeta no se pinta en vez de inventar un vencimiento.
        if (a.status.equals("verify", ignoreCase = true)) {
            parseInstant(a.deadline)?.let { windowEnd ->
                item { VerifyWindowCard(deadline = windowEnd) }
            }
        }
        if (a.description.isNotBlank()) item { DetailCard(title = "Descripción", body = a.description) }
        // A3 · EL MOTIVO DE "NO ASISTIR". Es la mitad de A3 que faltaba: el backend ya lo
        // exponía a toda la actividad del vínculo y el cliente no lo pintaba en ninguna parte.
        //
        // 🔴 NO SE FILTRA POR AUTOR. Se lee con [visibleDeclinedReason], que no admite
        // parámetro de visor — la firma ES la aplicación de A3 (ver el KDoc). Volver a
        // gatear esto por `createdBy` para "no molestar" reintroduce el defecto anterior.
        //
        // ⚠ Sin `remember` ni caché: se relee de la actividad cargada en cada composición.
        // El `DetailUiState.Content` que la envuelve es el único sitio donde vive, y al
        // cerrar la hoja vuelve a `Closed` — el motivo no sobrevive a la actividad.
        // A4: `deletedAt` es la ÚNICA marca de borrado; aquí no hay nada que purgar ni que
        // preguntar, y esta tarjeta desaparece sola cuando el servidor deja de mandar el
        // motivo.
        a.visibleDeclinedReason()?.let { motivo ->
            item { DetailCard(title = "Motivo de no asistencia", body = motivo) }
        }
        item { DetailBlockCard(pairs) }
        item {
            var renameTarget by remember { mutableStateOf<ActivityAttachmentDto?>(null) }
            var renameText by remember { mutableStateOf("") }
            var replaceTarget by remember { mutableStateOf<ActivityAttachmentDto?>(null) }
            var deleteTarget by remember { mutableStateOf<ActivityAttachmentDto?>(null) }
            val editable = isAttachmentsEditable(a.status, a.assignedTo)
            val activityId = a.id
            AttachmentsSection(
                attachments = attachments,
                editable = editable,
                onAdd = {
                    picker.launch { f ->
                        if (f != null) {
                            onAddAttachment(activityId, f)
                        }
                    }
                },
                onRename = { att ->
                    renameTarget = att
                    renameText = att.fileName
                },
                onReplace = { att ->
                    replaceTarget = att
                    picker.launch { f ->
                        if (f != null) {
                            onReplaceAttachment(activityId, att.id, f.name, f.mimeType, f.bytes)
                        }
                        replaceTarget = null
                    }
                },
                onDelete = { att ->
                    deleteTarget = att
                },
            )
            if (renameTarget != null) {
                AlertDialog(
                    onDismissRequest = { renameTarget = null },
                    title = { Text("Renombrar comprobante") },
                    text = {
                        OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            renameTarget?.let { onRenameAttachment(a.id, it.id, renameText) }
                            renameTarget = null
                        }) { Text("Guardar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
                    },
                )
            }
            if (deleteTarget != null) {
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text("Eliminar comprobante") },
                    text = { Text("¿Seguro que querés eliminar este comprobante?") },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteTarget?.let { onDeleteAttachment(a.id, it.id) }
                            deleteTarget = null
                        }) { Text("Eliminar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
                    },
                )
            }
        }
    }
}

/**
 * S3/T18 · La ventana de verificación, con el formato de fecha que ya usan la tarjeta y el
 * bloque de datos ([formatDeadline]). El texto dice QUÉ pasa si el plazo vence — el estado
 * `overdue` — porque un vencimiento sin consecuencia es sólo una fecha.
 */
@Composable
private fun VerifyWindowCard(deadline: Instant) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val local = deadline.toLocalDateTime(zone)
    DetailCard(
        title = "Por verificar",
        body = "Finaliza antes del ${formatDeadline(deadline, zone)} a las " +
            "${formatTime(local.hour, local.minute)}. Si no, la actividad pasa a vencida.",
    )
}

/** Pie FIJO, fuera del scroll (FR-9): el CTA de comprobante no baja con el contenido. */
@Composable
private fun DetailFooter(
    actividad: ActividadDetalle,
    onInertAction: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    onComplete: (String) -> Unit,
    isCompleting: Boolean,
    onDecline: (String) -> Unit,
    onPickAttachment: (String, FileData) -> Unit,
    picker: FilePicker,
    memberCandidates: List<MemberCandidate>,
    onOpenAssignPicker: () -> Unit,
    onOpenReassignPicker: () -> Unit,
    isAssigning: Boolean,
    isReassigning: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // T21 · "Asignar" se pinta SOLO cuando el SERVIDOR dice que esta transición existe:
        // `canAssign` es `true` iff `status == "created"`. El cliente NO deriva el flag del
        // estado: si lo hiciera, habría dos copias de la política y divergirían.
        if (actividad.canAssign) {
            Button(
                onClick = onOpenAssignPicker,
                enabled = !isAssigning,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                if (isAssigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DetailGeometry.progressSize),
                        strokeWidth = ProgressStroke.thin,
                    )
                } else {
                    Text("Asignar")
                }
            }
        }
        // T21 · "Reasignar" se pinta SOLO cuando el SERVIDOR dice que esta transición existe:
        // `canReassign` es `true` iff `status == "not_assisting"`. Misma regla: cliente no
        // deriva, sólo pinta lo que manda el DTO.
        if (actividad.canReassign) {
            Button(
                onClick = onOpenReassignPicker,
                enabled = !isReassigning,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                if (isReassigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DetailGeometry.progressSize),
                        strokeWidth = ProgressStroke.thin,
                    )
                } else {
                    Text("Reasignar")
                }
            }
        }
        // S3/T18 · "Finalizar" se pinta SOLO cuando el SERVIDOR dice que esta transición
        // existe para este usuario: `canComplete` es `in_progress` **y** `verify` (R3).
        // Antes el gate era `status != "done"`, así que el botón aparecía en `created`,
        // `assigned`, `overdue`… y el POST respondía 409. El label sigue siendo "Finalizar"
        // (el wording del usuario); la matriz lo llama "Completado".
        // ⚠ `isCompleting` es un FLAG PROPIO del ViewModel, no un estado de la hoja: la hoja NO
        //   se desmonta al finalizar, así que un `Loading(id)` la vaciaría y el usuario perdería
        //   el contexto justo tras completar.
        if (actividad.canComplete) {
            OutlinedButton(
                onClick = { onComplete(actividad.id) },
                enabled = !isCompleting,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DetailGeometry.progressSize),
                        strokeWidth = ProgressStroke.thin,
                    )
                } else {
                    Text("Finalizar")
                }
            }
        }
        // Estados 2/1/0: confirmado → chip inerte; el servidor autoriza confirmar → botón;
        // ninguno → nada. El chip de `assignedConfirmed` se queda (R4): es el sitio donde el
        // dato sí significa algo aunque la tarjeta ya no lo pinte.
        when {
            actividad.assignedConfirmed -> AssistChip(
                onClick = {},
                label = { Text("Asignación confirmada") },
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            )
            actividad.canConfirm -> Button(
                onClick = { onConfirm(actividad.id) },
                enabled = !isConfirming,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DetailGeometry.progressSize),
                        strokeWidth = ProgressStroke.thin,
                    )
                } else {
                    Text("Confirmar asignación")
                }
            }
            else -> {}
        }
        // 🔴 "NO ASISTIR" VIVE AQUÍ, NO SÓLO EN EL SWIPE. El gesto se cierra cuando la
        // asignación está confirmada (lo pidió quien usa la app), así que la acción tiene
        // que existir en un sitio donde se lea como una DECISIÓN y no como un gesto
        // accidental: la hoja de detalle, donde ya viven "Finalizar" y "Confirmar
        // asignación". El flag `canDecline` lo manda el SERVIDOR (misma regla que el
        // swipe) y la acción abre el diálogo de motivo obligatorio.
        if (actividad.canDecline && actividad.status !in TERMINAL_STATUSES) {
            TextButton(
                onClick = { onDecline(actividad.id) },
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            ) {
                Text(
                    text = "No asistir",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        val editable = isAttachmentsEditable(actividad.status, actividad.assignedTo)
        if (editable) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = AgendaGeometry.touchTargetLegacy),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "Cargar comprobante",
                    modifier = Modifier.fillMaxWidth().clickable {
                        picker.launch { fd ->
                            if (fd != null) onPickAttachment(actividad.id, fd)
                        }
                    }
                        .padding(Spacing.lg),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Bloque de 1 o 2 pares. Dos se reparten con `weight(1f)`; un superviviente toma el ancho. */
@Composable
private fun DetailBlockCard(pairs: List<Pair<String, String>>) {
    if (pairs.isEmpty()) return // R-7: lista vacía ⇒ no se emite el item.
    DetailCard {
        Row {
            pairs.forEach { (label, value) ->
                Column(
                    modifier = if (pairs.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Tarjeta con título y/o cuerpo opcionales. */
@Composable
internal fun DetailCard(title: String? = null, body: String? = null, content: @Composable () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth(), shape = ParentalShapes.medium) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

/**
 * Esqueleto PROPIO de la hoja (C3): el esqueleto de la lista es la forma de LISTA (columna de
 * fecha y riel) y aquí se anuncia UNA actividad. El rótulo vive en el CONTENEDOR — un nodo — y
 * `clearAndSetSemantics` limpia los hijos, el mismo truco que la lista usa para su rótulo.
 */
@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) { contentDescription = "Cargando actividad" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(Spacing.lg)
        SkeletonBar(Spacing.xl)
        SkeletonBar(Spacing.lg)
        SkeletonBar(Spacing.xl)
    }
}

@Composable
private fun SkeletonBar(height: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, ParentalShapes.small)
            .clearAndSetSemantics { },
    )
}
