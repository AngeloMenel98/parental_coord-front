package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.canConfirm
import com.parental.shared.feature.activities.ui.swipe.ActivitySwipeBox
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.activities.ui.swipe.SWIPE_HINT
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Pestaña Agenda — T2 de `odd/tasks/activities-cards-ui.md` (S2, S4): una TARJETA por
 * actividad, agrupada por día.
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
 * EL FONDO DEL LISTADO es `surfaceVariant` y las tarjetas son `surface`: sin ese
 * contraste la tarjeta sería invisible (mismo color que el fondo de la pantalla). Es el
 * fondo que dibuja el arteboard.
 */

@Composable
internal fun AgendaTimeline(
    groups: List<AgendaGroup>,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Spacing.maxContentWidth),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
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
                    group.rows.forEach { row ->
                        item(key = "card-${row.actividad.id}") {
                            val info = resolve(row.actividad.categoryId)
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
                                ),
                                canConfirm = row.actividad.canConfirm(currentUserId),
                                isConfirming = isConfirming,
                                focusRequesters = focusRequesters,
                                memberNames = memberNames,
                                children = children,
                                onSelectActividad = onSelectActividad,
                                onConfirm = onConfirm,
                                // R2-T2-7 · El gesto se monta AQUÍ, en el punto de render
                                // de la tarjeta. Sin este argumento la tarjeta NI
                                // COMPILA (el hueco no tiene valor por defecto).
                                swipe = { content ->
                                    ActivitySwipeBox(
                                        actividad = row.actividad,
                                        onSwipe = { action ->
                                            onSwipeAction(row.actividad.id, action)
                                        },
                                        content = content,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
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
