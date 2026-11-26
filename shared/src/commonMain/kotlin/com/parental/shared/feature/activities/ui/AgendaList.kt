package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.swipe.ActivitySwipeBox
import com.parental.shared.feature.activities.ui.swipe.SWIPE_HINT
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Pestaña Agenda — T2 de `odd/tasks/activities-cards-ui.md` (S2, S4) re-trabajada por
 * `odd/tasks/activity-card-stack.md` (S2, S3): una TARJETA por actividad, agrupada por días.
 *
 * 🪓 Sustituye a la fila con columna de fecha + riel (`AgendaTimelineRow`). El modo SEMANA
 * NO se toca: sigue pintando filas con `AgendaTimelineRow` desde `WeekAgenda.kt`, y por
 * eso ese fichero sigue existiendo.
 *
 * ⚠ EL GESTO SIGUE MONTÁNDOSE AQUÍ, EN EL PUNTO DE RENDER (R2-T2-7). La tarjeta declara
 * el hueco `swipe` SIN valor por defecto y esta lista lo monta; si el hueco volviera a
 * tener default, "no monté el gesto" volvería a ser un `c()` silencioso — que es como
 * pasaron 46 pruebas en verde con dos superficies sin gesto. El guard vive en
 * `SwipeMountingTest`.
 *
 * ⚠ EL RIEL SE PINTA UNA SOLA VEZ, COMO FONDO DEL CONTENEDOR DEL FEED (artboard
 * `.feed-inner::before`). Ni medición cruzada entre ítems ni encabezados pegajosos:
 * la línea es un Box de 2dp que baja de punta a punta, y cada tarjeta cae con su punto
 * (dibujado DENTRO de la card, a offset negativo, ver `ActivityCard.kt`) sobre ella.
 * El punto y el dot-contador de la pila viven fuera de la franja de la tarjeta: por eso
 * el padding izquierdo de la lista es `AgendaListStartPadding` y no el `lg` de siempre.
 *
 * ⚠ LA PILA ("Cena con los abuelos", S3): `groupByStartSlot` (PURE, en
 * `TimelineDerivation.kt`) decide qué filas comparten franja de inicio. La card de la
 * primera sale normal, dos fantasmas asoman por su esquina abajo-izquierda y el
 * dot-contador vive EN EL RIEL. Tocar la card despliega las apiladas a ancho completo;
 * cada miembro desplegado conserva su propio hueco de swipe y su destino de foco.
 * El estado del despliegue vive AQUÍ, en la lista: la card sigue función pura de sus datos.
 *
 * EL FONDO DEL LISTADO es `surfaceVariant` y las tarjetas son `surface`: sin ese
 * contraste la tarjeta sería invisible (mismo color que el fondo de la pantalla). Es el
 * fondo que dibuja el arteboard.
 */

// ── Geometría del riel — probada en `AgendaRailTest` ─────────────────────────────

/** Izquierda de la línea del riel (2dp, `outlineVariant`, artboard `.feed-inner`). */
internal val AgendaRailLineOffset: Dp = 14.dp

/** Grosor de la línea del riel: el `collar` de 2dp, el token del anillo/rail de siempre. */
internal val AgendaRailLineWidth: Dp = Spacing.collar

/** Centro del riel: sobre él cae el centro del punto de cada card y del dot-contador. */
internal val AgendaRailCenter = AgendaRailLineOffset + AgendaRailLineWidth / 2

/**
 * Padding izquierdo de la lista: espacio para riel + punto que el artboard reserva
 * (`.feed-inner { padding-left: 18px }`); el resto de la lista conserva `lg`.
 */
internal val AgendaListStartPadding: Dp = 28.dp

/** Inset del primer card de una pila: sitio para que asomen los fantasmas. */
internal val AgendaStackInset: Dp = 8.dp

/** Paso de cada capa fantasma: 4 y 8dp abajo-izquierda del borde de la pila. */
internal val AgendaStackGhostStep: Dp = 4.dp

/** Dot-contador (artboard `.zcount`): pill mínimo de 20dp × 18dp sobre el riel. */
internal val AgendaStackPillMinWidth: Dp = 20.dp
internal val AgendaStackPillHeight: Dp = 18.dp

/**
 * Offset del dot-contador respecto del borde izquierdo de la composición de la pila,
 * para que la COLUMNA del pill caiga sobre el centro del riel (el pill es decorativo:
 * el conteo lo anuncia la etiqueta de la card; su ancho exacto no importa al offset).
 */
internal fun agendaStackPillOffset(): Dp =
    AgendaRailCenter - AgendaListStartPadding - AgendaStackPillMinWidth / 2

@Composable
internal fun AgendaTimeline(
    groups: List<AgendaGroup>,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    /**
     * R2-T2-7 · ÚNICO punto por el que la lista Agenda dispara una acción de swipe.
     * Callback, no singleton: la tarjeta sigue siendo función pura de sus datos.
     */
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    listState: LazyListState,
) {
    val today = remember(zone) { currentDateIn(zone) }
    // Despliegue de pilas POR FRANJA (S3): la clave es el `slotKey` de la entrada, y
    // vive en la lista porque la card no es dueña de estado, ni del de nadie.
    val expandedSlots = remember { mutableStateMapOf<String, Boolean>() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = Spacing.maxContentWidth),
        ) {
            // La línea UNA sola vez, fondo del feed: `outlineVariant`, el mismo papel de
            // las separaciones filas — deliberadamente de bajo contraste (design.md).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = AgendaRailLineOffset)
                    .width(AgendaRailLineWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AgendaListStartPadding,
                    top = Spacing.sm,
                    end = Spacing.lg,
                    bottom = Spacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (groups.isEmpty()) {
                    item { EmptyState(title = "No hay actividades para este filtro", message = "") }
                } else {
                    item(key = "agenda-hint") {
                        Text(
                            text = SWIPE_HINT,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    groups.forEach { group ->
                        item(key = "header-${group.date?.toString() ?: "sin-fecha"}") {
                            AgendaGroupHeader(date = group.date, today = today)
                        }
                        groupByStartSlot(group.rows).forEach { entry ->
                            when (entry) {
                                is AgendaEntry.Single -> item(key = "card-${entry.row.actividad.id}") {
                                    AgendaCardItem(
                                        row = entry.row,
                                        slotRef = null,
                                        expandedSlots = expandedSlots,
                                        resolve = resolve,
                                        zone = zone,
                                        onSelectActividad = onSelectActividad,
                                        onConfirm = onConfirm,
                                        isConfirming = isConfirming,
                                        memberNames = memberNames,
                                        children = children,
                                        onSwipeAction = onSwipeAction,
                                        focusRequesters = focusRequesters,
                                    )
                                }
                                is AgendaEntry.Stack -> item(key = "slot-${entry.slotKey}") {
                                    AgendaCardItem(
                                        row = entry.first,
                                        slotRef = entry.slotKey,
                                        expandedSlots = expandedSlots,
                                        resolve = resolve,
                                        zone = zone,
                                        onSelectActividad = onSelectActividad,
                                        onConfirm = onConfirm,
                                        isConfirming = isConfirming,
                                        memberNames = memberNames,
                                        children = children,
                                        onSwipeAction = onSwipeAction,
                                        focusRequesters = focusRequesters,
                                        rest = entry.rest,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card de la lista: UNA actividad con su hueco de gesto montado en el punto de render
 * (R2-T2-7), o la COMPOSICIÓN de pila si [rest] no está vacía.
 *
 * @param slotRef clave de franja si esta card encabeza una pila; `null` para una card
 *   suelta, que abre el detalle sin tocar el despliegue.
 */
@Composable
private fun AgendaCardItem(
    row: TimelineRow,
    slotRef: String?,
    expandedSlots: SnapshotStateMap<String, Boolean>,
    resolve: (String) -> CategoryInfo?,
    zone: TimeZone,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    rest: List<TimelineRow> = emptyList(),
) {
    val info = resolve(row.actividad.categoryId)
    val stacked = rest.isNotEmpty()
    // La etiqueta la fija el punto de montaje (dentro de `AgendaSwipedCard`); aquí sólo
    // decide el estado de la pila.
    if (stacked) {
        val expanded = expandedSlots[slotRef!!] == true
        if (!expanded) {
            AgendaStackCollapsed(
                row = row,
                info = info,
                stackedCount = rest.size,
                slotKey = slotRef,
                zone = zone,
                memberNames = memberNames,
                children = children,
                isConfirming = isConfirming,
                resolve = resolve,
                onSelectActividad = onSelectActividad,
                onConfirm = onConfirm,
                onSwipeAction = onSwipeAction,
                focusRequesters = focusRequesters,
                onToggle = { expandedSlots[slotRef!!] = !(expandedSlots[slotRef] == true) },
            )
        } else {
            // ANCHO COMPLETO (artboard `.card.stack.expanded`): sin fantasmas ni
            // dot-contador; la primera colapsa al toque y las apiladas descienden como
            // cards normales, cada una con su hueco de swipe y su propio rótulo.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AgendaSwipedCard(
                    row = row,
                    info = info,
                    zone = zone,
                    isConfirming = isConfirming,
                    memberNames = memberNames,
                    children = children,
                    onSelectActividad = { expandedSlots[slotRef] = false },
                    onConfirm = onConfirm,
                    onSwipeAction = onSwipeAction,
                    focusRequesters = focusRequesters,
                )
                rest.forEach { stackedRow ->
                    AgendaSwipedCard(
                        row = stackedRow,
                        info = resolve(stackedRow.actividad.categoryId),
                        zone = zone,
                        isConfirming = isConfirming,
                        memberNames = memberNames,
                        children = children,
                        onSelectActividad = onSelectActividad,
                        onConfirm = onConfirm,
                        onSwipeAction = onSwipeAction,
                        focusRequesters = focusRequesters,
                    )
                }
            }
        }
    } else {
        AgendaSwipedCard(
            row = row,
            info = info,
            zone = zone,
            isConfirming = isConfirming,
            memberNames = memberNames,
            children = children,
            onSelectActividad = onSelectActividad,
            onConfirm = onConfirm,
            onSwipeAction = onSwipeAction,
            focusRequesters = focusRequesters,
        )
    }
}

/**
 * Card de UNA actividad montando el hueco de gesto con [onSwipeAction] —  R2-T2-7.
 *
 * @param labelSuffix cola de la etiqueta combinada; su único uso es el anunciante del
 *   despliegue de la pila, que sólo conoce la composición.
 */
@Composable
private fun AgendaSwipedCard(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    isConfirming: Boolean,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    railDot: Boolean = true,
    labelSuffix: String = "",
) {
    ActivityCard(
        row = row,
        info = info,
        zone = zone,
        semanticLabel = activitySemanticLabel(
            row = row,
            info = info,
            zone = zone,
            memberNames = memberNames,
            children = children,
        ) + labelSuffix,
        // S3/T18 · El permiso llega DEL SERVIDOR en el DTO; no se recalcula aquí. Antes
        // era `canConfirm(currentUserId)`.
        canConfirm = row.actividad.canConfirm,
        isConfirming = isConfirming,
        focusRequesters = focusRequesters,
        memberNames = memberNames,
        children = children,
        onSelectActividad = onSelectActividad,
        onConfirm = onConfirm,
        // R2-T2-7 · El gesto se monta AQUÍ, en el punto de render de la tarjeta. Sin
        // este argumento la tarjeta NI COMPILA (el hueco no tiene valor por defecto).
        swipe = { content ->
            ActivitySwipeBox(
                actividad = row.actividad,
                onSwipe = { action ->
                    onSwipeAction(row.actividad.id, action)
                },
                content = content,
            )
        },
        railDot = railDot,
    )
}

/**
 * Pila COLLAPSADA (artboard `.card.stack` sin clase `expanded`): el primer card normal,
 * DETRÁS dos capas fantasma que asoman por abajo-izquierda, y el dot-contador EN EL
 * RIEL con el color de categoría de la primera actividad.
 *
 * La composición es la caja del `fillMaxWidth`; el card entra `AgendaStackInset` por
 * izquierda y la composición reserva `AgendaStackInset` por abajo — las dos capas de
 * fantasma, con `matchParentSize` + `offset`, quedan CONTENIDAS dentro de la caja (no
 * se cuelgan sobre la card siguiente, como exigía el enunciado).
 */
@Composable
private fun AgendaStackCollapsed(
    row: TimelineRow,
    info: CategoryInfo?,
    stackedCount: Int,
    slotKey: String,
    zone: TimeZone,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    isConfirming: Boolean,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    onToggle: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            // El card entra `AgendaStackInset` en la esquina abajo-izquierda; el hueco
            // que sobra es exactamente el que ocupan los fantasmas por debajo.
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AgendaStackInset, bottom = AgendaStackInset),
        ) {
            AgendaGhost(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = -(AgendaStackGhostStep * 2), y = AgendaStackGhostStep * 2),
                alpha = 0.08f,
            )
            AgendaGhost(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = -AgendaStackGhostStep, y = AgendaStackGhostStep),
                alpha = 0.16f,
            )
            AgendaSwipedCard(
                row = row,
                info = info,
                zone = zone,
                isConfirming = isConfirming,
                memberNames = memberNames,
                children = children,
                // S3 · Tocar la pila DESPLIEGA (el toggle se decide aquí y no se le pasa
                // a ActivityCard, que sigue siendo función pura de sus datos).
                onSelectActividad = { onToggle() },
                onConfirm = onConfirm,
                onSwipeAction = onSwipeAction,
                focusRequesters = focusRequesters,
                railDot = false,
                labelSuffix = stackLabelSuffix(stackedCount),
            )
        }
        // Dot-contador del RIEL (artboard `.zcount`): decorativo; el conteo lo anuncia
        // la etiqueta de la card. Color de la categoría de la PRIMERA actividad. El pill
        // vive en EL frame de la composición (no del card entrado): su columna cae
        // exactamente sobre el riel.
        StackCountPill(
            count = 1 + stackedCount,
            color = categoryTagTriple(info)?.dot ?: MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = agendaStackPillOffset()),
        )
    }
}

/**
 * Capa fantasma de la pila: rectángulo del mismo shape del card, desplazado abajo-
 * izquierda (lo define el `modifier` del llamador, que es quien conoce el BoxScope),
 * con el alfa de `primary` que le toca (0,16/0,08). Decorativo y sin hijos propios: es
 * un fondo, no una card accesible.
 */
@Composable
private fun AgendaGhost(modifier: Modifier = Modifier, alpha: Float) {
    Box(
        modifier = modifier
            .clip(ParentalShapes.medium)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            .border(
                BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
                ParentalShapes.medium,
            ),
    )
}

/**
 * Dot-contador de la pila (artboard `.zcount`): pill del color de categoría sobre el
 * riel con el número. Sin semántica propia (`clearAndSetSemantics`): el conteo se
 * anuncia en la etiqueta combinada de la card, y un nodo "2" suelto sería ruido.
 */
@Composable
private fun StackCountPill(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(AgendaStackPillHeight)
            .defaultMinSize(minWidth = AgendaStackPillMinWidth)
            .clip(CircleShape)
            .background(color)
            .border(
                BorderStroke(Spacing.collar, MaterialTheme.colorScheme.surface),
                CircleShape,
            )
            .clearAndSetSemantics { }
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Sufijo de la etiqueta combinada de la card de la pila — el texto del aria-label del
 * artboard, con la misma forma: «…, y 1 actividad apilada detrás. Tocar para
 * desplegar». Desplegada no hay sufijo: lo apilado ya ES visible como cards propias.
 */
private fun stackLabelSuffix(stackedCount: Int): String {
    if (stackedCount <= 0) return ""
    val n = stackedCount
    val word = if (n == 1) "actividad" else "actividades"
    val adjective = if (n == 1) "apilada" else "apiladas"
    return ", y $n $word $adjective detrás. Tocar para desplegar"
}

/**
 * Encabezado de grupo: "lunes 12 de mayo" y, si es hoy, "· hoy" en `primary`.
 *
 * Un nodo de semántica (fusionado) con la etiqueta completa: la parte de color es
 * decoración visual, la palabra es el contenido.
 */
@Composable
private fun AgendaGroupHeader(date: LocalDate?, today: LocalDate) {
    if (date == null) {
        Text(
            text = NO_START_LABEL,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = formatGroupTitle(date, today)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDayTitle(date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        if (date == today) {
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "· hoy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
