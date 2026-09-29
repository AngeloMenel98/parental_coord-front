package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import kotlinx.datetime.LocalDate

/*
 * Tarjeta de semana + celda de día — `calendar-design-redesign`, T4 (paso 5). `R7-T1-7`.
 * Aislarla es lo que hace T1 revisable contra este archivo y no contra el monolito de
 * 1.587 líneas; `DayCell` no usa API experimental (DD-2).
 *
 * A2 · `calendar-a11y-and-fit-fixes`: la fila es un `Row` de siete
 * `Box(Modifier.weight(1f))`; la celda NO fija ancho —función del viewport, luego no
 * puede ser token— ni mínimo de alto. La banda uniforme la da `statusSlotHeight`, cuyo
 * mecanismo está en su sitio. `+N` va en su propia línea, a todo ancho (D-1): a 320dp
 * la caja es 26.286dp. ⚠ `dotTray` 12dp está CONECTADO desde T4-7 como `padding`
 * interior de esta tarjeta (2.º término de la cadena de 56dp) y NO es la bandeja del
 * punto. Los tokens nombran valores, NO los cambian.
 */

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
                WeekNavButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = "Semana anterior",
                    onClick = onPreviousWeek,
                )
                Text(
                    text = formatMonthYear(weekStart),
                    // R1-T1-1: `TypeTokens.date` (24sp serif) → `titleMedium` (16sp).
                    // Acaba el sobreuso 24sp/16sp de la etiqueta; el peso SemiBold de
                    // aquí es de A12, no de T1, y se conserva.
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                WeekNavButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = "Semana siguiente",
                    onClick = onNextWeek,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                weekDates.forEach { date ->
                    val bucketRows = dayBuckets[date]?.slots
                        ?.flatMap { it.rows }
                        .orEmpty()
                    Box(modifier = Modifier.weight(1f)) {
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
}

/**
 * Navegación de semana — `R3-T1-3`. Un composable para los dos lados: el contrato
 * de 48dp y la convención de `contentDescription` viven aquí y no pueden divergir.
 * 48dp = [AgendaGeometry.touchTargetLegacy] (lo que T3-8 puso en la escala);
 * [Layout.touchMin], que citan los registros, NO existe en este árbol. `IconButton`
 * de M3 ya es circular. Los `TextButton` de ~70dp se comían ~35% del ancho.
 */
@Composable
private fun WeekNavButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(AgendaGeometry.touchTargetLegacy),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        // R2-T1-2: la etiqueta HABLA el día entero; la celda lo MUESTRA en dos
        // letras. Mismo día, dos tablas — no un intercambio ciego.
        append(ES_WEEKDAYS_SPOKEN[date.dayOfWeek.ordinal])
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

    // A2 · R-A2-4 (OQ-A2-1)
    val statusSlotHeight: Dp = with(LocalDensity.current) {
        AgendaGeometry.dotLegacy + Spacing.collar +
            MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }

    // R4-T1-4 — UNA regla, TRES foregrounds. `onSurfaceVariant` sobre `primary`
    // mide 0.69:1: un solo foreground sin intercambiar es ilegible, y por eso
    // la regla se declara una vez y no como tres ediciones que puedan olvidarse.
    // Sin seleccionar no cambia nada: `onSurface` / `onSurfaceVariant`.
    val onSelected = MaterialTheme.colorScheme.onPrimary
    val cellForeground =
        if (selected) onSelected else MaterialTheme.colorScheme.onSurfaceVariant
    val numberForeground =
        if (selected) onSelected else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ParentalShapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.collar),
    ) {
        Text(
            // R2-T1-2: visual en dos letras; `labelSmall` ya trae `smcp`.
            text = weekdayLabel(date),
            style = MaterialTheme.typography.labelSmall,
            color = cellForeground,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = numberForeground,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Column(
            modifier = Modifier.height(statusSlotHeight),
            verticalArrangement = Arrangement.spacedBy(Spacing.collar),
        ) {
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
            }
            if (extraCategories > 0) {
                Text(
                    text = "+$extraCategories",
                    style = MaterialTheme.typography.labelSmall,
                    color = cellForeground,
                )
            }
        }
    }
}
