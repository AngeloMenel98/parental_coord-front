package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * Hoja de calendario para saltar de día — T4 de `odd/tasks/activities-cards-ui.md` (S3).
 *
 * ES LA AFIRMACIÓN DEL USUARIO: "open a calendar with the days that has activities on
 * there". Cada día con actividades lleva un punto POR CATEGORÍA (máximo 3, arteboard), y
 * al tocar un día la lista salta a ese día —o al grupo con actividades más cercano hacia
 * adelante—.
 *
 * CERO COLORES NUEVOS: los puntos usan `categoryTagTriple` (el `dot` de la categoría, y
 * `outline` para la desconocida), el "hoy" usa `primary`/`onPrimary` del esquema.
 *
 * LA REJILLA ES DE LUNES A DOMINGO, igual que la agenda (`buildMonthGrid`), y los días
 * de OTRO MES son atenuados y NO pulsables: son relleno, no destinos. Saltar a junio
 * desde mayo se hace con las flechas del mes.
 */

/**
 * Hoja modal con el mes y los puntos de las categorías de cada día con actividades.
 *
 * @param dayDots días → categorías presentes (ya filtradas por la pestaña activa).
 * @param onSelectDate el día elegido. Quien la llama decide qué hacer: saltar la lista
 *   (modo Agenda) o seleccionar la fecha (modo Semana).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarJumpSheet(
    sheetState: SheetState,
    today: LocalDate,
    visibleMonth: LocalDate,
    onMonthChange: (LocalDate) -> Unit,
    dayDots: Map<LocalDate, List<CategoryInfo?>>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val grid = remember(visibleMonth) {
        buildMonthGrid(visibleMonth.year, visibleMonth.monthNumber)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Spacing.maxContentWidth)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md),
        ) {
            // ── Cabecera del mes ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMonthYear(visibleMonth),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onMonthChange(visibleMonth.minus(1, DateTimeUnit.MONTH))
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Mes anterior",
                    )
                }
                IconButton(
                    onClick = {
                        onMonthChange(visibleMonth.plus(1, DateTimeUnit.MONTH))
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Mes siguiente",
                    )
                }
            }

            // ── Encabezado de los días de la semana ─────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                ES_WEEKDAYS_CAPS.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))

            // ── Rejilla del mes ─────────────────────────────────────────────
            grid.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            today = today,
                            inMonth = date.monthNumber == visibleMonth.monthNumber &&
                                date.year == visibleMonth.year,
                            isToday = date == today,
                            categories = dayDots[date].orEmpty(),
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Pie ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onSelectDate(today) }) {
                    Text("Hoy")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    }
}

/**
 * Celda de un día: el número, y los puntos de las categorías que tienen actividad ese
 * día.
 *
 * UN nodo de semántica por celda, con la frase completa (`dayCellSemanticLabel`): los
 * puntos son decorativos y el día se anuncia con su fecha y sus actividades.
 *
 * Los días de otro mes son texto atenuado, sin interacción: son relleno de la rejilla.
 */
@Composable
private fun CalendarDayCell(
    date: LocalDate,
    today: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    categories: List<CategoryInfo?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!inMonth) {
        Box(
            modifier = modifier
                .height(AgendaGeometry.touchTargetLegacy)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        return
    }

    val label = dayCellSemanticLabel(date = date, today = today, categories = categories)
    Column(
        modifier = modifier
            .height(AgendaGeometry.touchTargetLegacy)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            // `mergeDescendants`: el número del día y los puntos son parte de la MISMA
            // celda. Sin fusionar, TalkBack anunciaría la celda ("martes 12 de mayo, 3
            // actividades…") y ADEMÁS el número suelto ("12") en dos nodos distintos.
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                // El círculo del arteboard mide 28dp; se rounds al valor de la escala más
                // cercano (24dp) para no inventar una medida fuera del sistema de espaciado.
                .size(Spacing.xl)
                .clip(CircleShape)
                .background(
                    if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            categories.take(MAX_DAY_DOTS).forEach { category ->
                Box(
                    modifier = Modifier
                        .size(Spacing.xs)
                        .clip(CircleShape)
                        .background(
                            category?.let { categoryDotColor(it) }
                                ?: MaterialTheme.colorScheme.outline,
                        )
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * Color del punto de una categoría. La tríada se resuelve FUERA del punto: el punto es
 * decorativo y la categoría la anuncia el rótulo de la celda.
 */
@Composable
private fun categoryDotColor(category: CategoryInfo): Color =
    categoryTagTriple(category)?.dot ?: MaterialTheme.colorScheme.outline
