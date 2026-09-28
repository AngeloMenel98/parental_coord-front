package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import kotlinx.datetime.LocalDate

/*
 * Tarjeta de semana + celda de día — `calendar-design-redesign`, T4 (paso 5). `R7-T1-7`.
 *
 * Aislar la tarjeta es lo que hace T1 revisable contra este archivo y no contra el
 * monolito de 1.587 líneas. `WeekOverviewCard` conserva su
 * `@OptIn(ExperimentalLayoutApi::class)` de origen; `DayCell` no lo tiene y no lo
 * recibe porque no usa API experimental (DD-2).
 *
 * ⚠ Los tokens nombran valores, NO los cambian: `dayCellWidth` 52dp y
 * `dayCellMinHeight` 76dp (dueños A2 y T1), `dotLegacy` 7dp (T1-6 → 8), `dotTray`
 * 12dp declarado y sin conectar.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekOverviewCard(
    weekStart: LocalDate,
    weekDates: List<LocalDate>,
    dayBuckets: Map<LocalDate, DayBucket>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    resolve: (String) -> CategoryInfo?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.large,
        // R7-T5-7 · T5-7. Sin `colors`, el contenedor caía en `surfaceContainer`
        // (rol que este esquema NO define — `Color.kt:15`, ADR-1); esto para la
        // dependencia, no añade el rol (H12). ⚠ `surfaceVariant` ELIMINADO por
        // aritmética: `R4-T1-4` fija la celda en `surfaceVariant.copy(alpha =
        // 0.45f)` y se compondría sobre sí misma (celda a 1.00:1). No la
        // "simplifiques"; `Color.kt` no se toca.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(AgendaGeometry.dotTray)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onPreviousWeek,
                    modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
                ) {
                    Text("Anterior")
                }
                Text(
                    text = formatMonthYear(weekStart),
                    style = TypeTokens.date,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onNextWeek,
                    modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
                ) {
                    Text("Siguiente")
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                weekDates.forEach { date ->
                    val bucketRows = dayBuckets[date]?.slots
                        ?.flatMap { it.rows }
                        .orEmpty()
                    DayCell(
                        date = date,
                        rows = bucketRows,
                        selected = date == selectedDate,
                        resolve = resolve,
                        onClick = { onSelectDate(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    rows: List<TimelineRow>,
    selected: Boolean,
    resolve: (String) -> CategoryInfo?,
    onClick: () -> Unit,
) {
    val uniqueCategories = rows.distinctBy { it.actividad.categoryId }
    val visibleCategories = uniqueCategories.take(3)
    val extraCategories = (uniqueCategories.size - visibleCategories.size).coerceAtLeast(0)
    val countLabel = when (rows.size) {
        0 -> "sin actividades"
        1 -> "1 actividad"
        else -> "${rows.size} actividades"
    }
    val semanticLabel = buildString {
        append(weekdayLabel(date))
        append(", ")
        append(date.dayOfMonth)
        append(" ")
        append(ES_MONTHS[date.monthNumber - 1])
        append(" ")
        append(date.year)
        append(", ")
        append(countLabel)
        if (selected) append(", seleccionado")
    }

    Column(
        modifier = Modifier
            .width(AgendaGeometry.dayCellWidth)
            .heightIn(min = AgendaGeometry.dayCellMinHeight)
            .clip(ParentalShapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.collar),
    ) {
        Text(
            text = weekdayLabel(date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.collar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleCategories.forEach { row ->
                TagDot(
                    color = categoryTagTriple(resolve(row.actividad.categoryId))?.dot
                        ?: MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(AgendaGeometry.dotLegacy),
                )
            }
            if (extraCategories > 0) {
                Text(
                    text = "+$extraCategories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
