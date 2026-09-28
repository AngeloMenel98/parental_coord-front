package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.activities.domain.model.canConfirm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Timeline de la semana — `calendar-design-redesign`, T4 (paso 7). F5. **EXIT GATE DE T4.**
 *
 * Último por una razón concreta: es el único con aristas entrantes desde **dos** archivos ya extraídos
 * — la celda de día (F6) y el cuerpo de fila (archivo 10); los pasos 4/4b/5/6 deben estar verdes antes.
 * `@OptIn(ExperimentalLayoutApi::class)` viaja desde el origen (`FlowRow`, `items`).
 *
 * ⚠ **OD-1, SETTLED BY USER — T4 NO aplica 190→40.** `nodeCardLegacy` conserva su píxel (190dp) y
 * sólo recibe el nombre legacy; T2-6 reescribe el valor en la declaración nombrada. Eso es lo que
 * hace T4 demostrablemente delta-cero y le da a T2 una casa con nombre. Mismo trato para
 * `touchTargetLegacy` (44dp, T3-8 → 48) y `chevronPad` (10dp, T3-8 → 8).
 *
 * ⚠ **H4 respetado**: la línea que era `HEAD:608` (`spacedBy(4)`, valor propiedad de A5) se nombró
 * `Spacing.xs`, que **vale 4dp**. Nombrar no es cambiar: el píxel sigue siendo 4 y A5 sigue siendo su
 * dueño. `RC-6` exigía cero literales, y un literal sin nombre también roba una decisión a su dueño.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekTimeline(
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
                .widthIn(max = Spacing.maxContentWidth),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
            }

            if (selectedBucket == null || selectedBucket.slots.isEmpty()) {
                item(key = "empty-day") {
                    EmptyState(title = "No hay actividades para este día", message = "")
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
                                modifier = Modifier.padding(vertical = Spacing.xxs),
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                        modifier = Modifier.padding(start = AgendaGeometry.undatedGutter, top = Spacing.lg, bottom = Spacing.xs),
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
@Composable
internal fun WeekActivityNode(
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
        modifier = Modifier.width(AgendaGeometry.nodeCardLegacy),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.hairline,
    ) {
        Column(modifier = Modifier.padding(Spacing.xs)) {
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
                        .padding(end = Spacing.xs, bottom = Spacing.xs),
                )
            }
        }
    }
}
/** Chip inline "Confirmar" — hermano del cuerpo clickeable de la fila. */
@Composable
internal fun ConfirmarChip(
    onClick: () -> Unit,
    enabled: Boolean,
    isConfirming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(shape = ParentalShapes.extraLarge) {
        Row(
            modifier = modifier
                .widthIn(min = AgendaGeometry.touchTargetLegacy)
                .heightIn(min = AgendaGeometry.touchTargetLegacy)
                .clip(ParentalShapes.extraLarge)
                .clickable(enabled = enabled, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = if (isConfirming) {
                        "Confirmando asignación"
                    } else {
                        "Confirmar asignación"
                    }
                }
                .padding(horizontal = Spacing.md, vertical = AgendaGeometry.chevronPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isConfirming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.lg),
                    strokeWidth = ProgressStroke.thin,
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
