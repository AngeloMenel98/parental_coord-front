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
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.DetailGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.canConfirm
import com.parental.shared.feature.activities.ui.*
import kotlinx.coroutines.launch

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

/** Raíz de la hoja — `activity-detail-modal`, T2-1. */
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
    currentUserId: String,
) {
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
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
        Column(modifier = Modifier.fillMaxWidth()) {
            DetailHeader(
                onClose = requestDismiss,
                closeFocus = closeFocus,
            )
            when (state) {
                DetailUiState.Closed -> Spacer(Modifier.height(Spacing.lg))
                is DetailUiState.Loading -> DetailSkeleton(Modifier.weight(1f))
                // ⚠ `EmptyState` aplica `fillMaxHeight()` (`CalendarChrome.kt:110`) y aquí se
                // emite sin `weight`: la hoja de error mide a altura completa. Es INTENCIONAL
                // (riesgo R8 del diseño, que lo declara y lo manda a medir en G-24), no un
                // descuido — no se le pasa un alto compacto.
                is DetailUiState.Error -> EmptyState(
                    title = "No pudimos cargar el detalle",
                    message = state.message,
                    actionLabel = "Reintentar",
                    onAction = onRetry,
                )
                // `weight` es extensión de `ColumnScope`: sólo resuelve dentro del lambda de
                // `Column`, así que se pasa ya aplicado desde este sitio de llamada.
                is DetailUiState.Content -> DetailContent(
                    state = state,
                    resolve = resolve,
                    memberNames = memberNames,
                    onInertAction = onInertAction,
                    modifier = Modifier.weight(1f),
                )
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
                    currentUserId = currentUserId,
                )
            }
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
    modifier: Modifier = Modifier,
) {
    val a = state.actividad
    val isSalud = resolve(a.categoryId)?.name.equals("Salud", ignoreCase = true)
    // R-7 · Los pares opcionales se OMITEN si no hay valor; sólo "Asignado a" tiene valor por
    // defecto y "Prioridad" nunca se oculta. Cero guiones suspensivos.
    val pairs = buildList {
        formatDetailDate(a.scheduledStart)?.let { add("Fecha" to it) }
        formatDetailDate(a.deadline)?.let { add("Límite evidencia" to it) }
        add("Asignado a" to (a.assignedTo?.let { memberNames[it] } ?: "Sin asignar"))
        add("Prioridad" to criticalityLabel(a.criticality))
    }
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
        if (a.description.isNotBlank()) item { DetailCard(title = "Descripción", body = a.description) }
        item { DetailBlockCard(pairs) }
        item {
            // FR-7 **NO ENTREGADA** — bloqueada: no existe API de adjuntos (`ActAttachmentEntity`
            // sólo está en la entidad y en `activities.module.ts`; sin servicio, ruta ni campo en
            // el DTO). El chip pinta el rótulo y NO abre nada —ni selector, ni cámara, ni red—:
            // sólo emite `NEXT_MSG`. Mismo caso inerte que ya traía la pantalla antigua.
            AssistChip(
                onClick = { onInertAction(NEXT_MSG) },
                label = { Text(if (isSalud) "Adjuntar resumen médico" else "Adjuntar archivo") },
            )
        }
    }
}

/** Pie FIJO, fuera del scroll (FR-9): el CTA de comprobante no baja con el contenido. */
@Composable
private fun DetailFooter(
    actividad: ActividadDetalle,
    onInertAction: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Estados 2/1/0: confirmado → chip inerte; asignable → botón; no asignado → nada.
        when {
            actividad.assignedConfirmed -> AssistChip(
                onClick = {},
                label = { Text("Asignación confirmada") },
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            )
            actividad.canConfirm(currentUserId) -> Button(
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
        // FR-8 con salvedad declarada: la acción primaria se Pinta y se Ancla al fondo, pero no
        // abre nada — no hay flujo de subida. Tampoco es un token nuevo: `primary` del esquema.
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgendaGeometry.touchTargetLegacy),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = "Cargar comprobante",
                modifier = Modifier.fillMaxWidth().clickable { onInertAction(NEXT_MSG) }
                    .padding(Spacing.lg),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
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
private fun DetailCard(title: String? = null, body: String? = null, content: @Composable () -> Unit = {}) {
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
