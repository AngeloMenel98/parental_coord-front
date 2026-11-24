package com.parental.shared.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.feature.activities.ui.AgendaTimelineLine
import com.parental.shared.feature.admin.domain.model.AdminBondActivity
import kotlinx.datetime.TimeZone

/**
 * S2/S3 — Estado real del detalle de actividades. Un fetch que falló y un período sin
 * actividades son cosas distintas: el primero ofrece Reintentar, el segundo es un resultado
 * legítimo del reporte cargado.
 */
internal enum class ActivitiesDetailState { LOADING, LOAD_ERROR, EMPTY, CONTENT }

internal fun activitiesDetailState(
    isLoading: Boolean,
    hasLoadError: Boolean,
    hasActivities: Boolean,
): ActivitiesDetailState = when {
    isLoading -> ActivitiesDetailState.LOADING
    hasLoadError && !hasActivities -> ActivitiesDetailState.LOAD_ERROR
    hasActivities -> ActivitiesDetailState.CONTENT
    else -> ActivitiesDetailState.EMPTY
}

/**
 * F7 · S21 — detalle de las actividades del reporte (artboard `admin-ui/04`, tercera pantalla).
 *
 * Es una pantalla de LECTURA: no confirma, no asigna, no swapea. Por eso no reusa
 * `AgendaTimelineRow` — su firma pide un `Actividad` completo, los callbacks de confirmar y el
 * hueco de swipe, y fabricar ese objeto para una vista sin acciones sería mentir sobre lo que la
 * fila puede hacer. Reusa sí lo que define el lenguaje visual: la columna de fecha de 48 dp, la
 * línea de 1 dp, la hora monoespaciada, `StatusTag` y los tokens de color del design system.
 *
 * Los números de arriba salen de [buildActivityCounts], la misma función que alimenta la tarjeta:
 * la barra y el encabezado de esta pantalla no pueden decir algo distinto que el reporte.
 */
@Composable
internal fun AdminActivitiesDetailScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onStatusFilter: (String) -> Unit,
    onAssigneeFilter: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val dark = isSystemInDarkTheme()
    val report = state.report
    val counts = remember(report) { report?.let { buildActivityCounts(it) } }
    val members = remember(report) {
        report?.let { buildMemberCompletions(it.members) } ?: emptyList()
    }
    val visible = remember(
        state.reportActivities,
        state.activityStatusFilter,
        state.activityAssigneeFilter,
    ) {
        filterReportActivities(
            state.reportActivities,
            state.activityStatusFilter,
            state.activityAssigneeFilter,
        )
    }
    val completed = visible.filter { isCompleted(it.status) }
    val pending = visible.filterNot { isCompleted(it.status) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Reporte") }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = report?.bondTitle.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.isLoadingActivities) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (counts != null && counts.total > 0) {
                        StackedBar(counts.buckets.map { activityBucketColor(it.key, dark) to it.count })
                        counts.buckets.forEach { bucket ->
                            ActivityLegendLine(bucket.label, bucket.count, bucket.percentage, activityBucketColor(bucket.key, dark))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Cumplidas / no cumplidas",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${adminFormatPercent(counts.completionRate)} / " +
                                    adminFormatPercent(counts.notCompletedRate),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = state.activityStatusFilter == FILTER_ALL,
                        onClick = { onStatusFilter(FILTER_ALL) },
                        label = { Text("Todas · ${counts?.total ?: state.reportActivitiesTotal}") },
                    )
                    counts?.buckets?.forEach { bucket ->
                        FilterChip(
                            selected = state.activityStatusFilter == bucket.key,
                            onClick = { onStatusFilter(bucket.key) },
                            label = { Text("${bucket.label} · ${bucket.count}") },
                        )
                    }
                }
            }

            if (members.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Progenitor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterChip(
                            selected = state.activityAssigneeFilter == null,
                            onClick = { onAssigneeFilter(null) },
                            label = { Text("Todos") },
                        )
                        members.forEach { member ->
                            FilterChip(
                                selected = state.activityAssigneeFilter == member.userId,
                                onClick = { onAssigneeFilter(member.userId) },
                                label = { Text("${member.name} · ${member.assigned}") },
                            )
                        }
                    }
                }
            }

            when (activitiesDetailState(
                isLoading = state.isLoadingActivities,
                hasLoadError = state.activitiesError != null,
                hasActivities = state.reportActivities.isNotEmpty(),
            )) {
                ActivitiesDetailState.LOAD_ERROR -> item {
                    Column(
                        modifier = Modifier.padding(vertical = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "No se pudo cargar el detalle de actividades.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = state.activitiesError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }
                ActivitiesDetailState.EMPTY -> item {
                    Text(
                        text = "No hay actividades en el período seleccionado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
                else -> {}
            }

            if (visible.isEmpty() && state.reportActivities.isNotEmpty()) {
                item {
                    Text(
                        text = "Sin actividades para este filtro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }

            if (completed.isNotEmpty()) {
                item { GroupHeader("Cumplidas", completed.size, StatusTokens.Status.Done) }
                items(completed, key = { it.id }) { activity ->
                    ReportActivityRow(activity = activity, zone = zone)
                }
            }

            if (pending.isNotEmpty()) {
                item { GroupHeader("No cumplidas", pending.size, StatusTokens.Status.Overdue) }
                items(pending, key = { it.id }) { activity ->
                    ReportActivityRow(activity = activity, zone = zone)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, status: StatusTokens.Status) {
    val color = StatusTokens.triple(status, isSystemInDarkTheme()).dot
    Row(
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(7.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityLegendLine(label: String, count: Int, percentage: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count · ${adminFormatPercent(percentage)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Fila del detalle: columna de fecha de 48 dp, línea de tiempo de 1 dp, hora monoespaciada,
 * título, asignado y categoría, y el `StatusTag` del estado. Sin acciones.
 */
@Composable
private fun ReportActivityRow(activity: AdminBondActivity, zone: TimeZone) {
    val darkTheme = isSystemInDarkTheme()
    val time = activityTimeLabel(activity.scheduledStart, zone)
    val deadline = activityDeadlineLabel(activity, zone)
    val overdue = bucketKeyOf(activity.status) == OVERDUE_KEY
    val meta = listOfNotNull(
        activity.assigneeName?.takeIf { it.isNotBlank() },
        activity.categoryName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    // S1 · mismo lenguaje de agenda: dot de categoría sobre el riel. categoryTagTriple
    // resuelve por nombre con los tokens estándar; sin categoría, outline neutro.
    val categoryColor = categoryTagTriple(adminCategoryInfo(activity.categoryName))?.dot
        ?: MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .padding(end = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = activityDayLabel(activity.scheduledStart, zone),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = activityMonthLabel(activity.scheduledStart, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        // S1 · el riel NEUTRO se pinta con el componente compartido de la agenda
        // (`AgendaTimelineLine`), no con un Box local: mismo 1dp, mismo contraste
        // outline, y un solo dueño del cromo en toda la app.
        AgendaTimelineLine()

        TagDot(
            color = categoryColor,
            modifier = Modifier.padding(horizontal = Spacing.xs),
            size = AgendaGeometry.lineNode,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusTag(activity.status)
            }
            if (deadline != null) {
                Text(
                    text = deadline,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) {
                        StatusTokens.triple(StatusTokens.Status.Overdue, darkTheme).dot
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Text(
            text = "›",
            modifier = Modifier.padding(start = Spacing.xxs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Color de cada tramo: los MISMOS tokens de estado que usa el resto de la app.
 * Un estado desconocido cae en el gris neutro (`Created`), igual que `StatusTag`.
 */
internal fun activityBucketColor(key: String, darkTheme: Boolean): Color {
    val status = when (key) {
        DONE_KEY -> StatusTokens.Status.Done
        OPEN_KEY -> StatusTokens.Status.Assigned
        OVERDUE_KEY -> StatusTokens.Status.Overdue
        else -> StatusTokens.Status.Created
    }
    return StatusTokens.triple(status, darkTheme).dot
}
