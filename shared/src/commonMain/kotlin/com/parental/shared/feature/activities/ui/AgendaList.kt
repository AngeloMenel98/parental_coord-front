package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.canConfirm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Pestaña Agenda — `calendar-design-redesign`, T4 (paso 6). F4.
 *
 * `AgendaTimeline` + `AgendaTimelineRow`. `ActivityBody` NO vive aquí: al medir
 * el archivo con su bloque de imports, F4 pasaba de 250, así que se disparó la
 * válvula de escape que el propio registro autoriza — `ActivityBody` a su propio
 * `ui/ActivityBody.kt` (archivo 10). Sin replanificar nada.
 *
 * `ConfirmarChip` tampoco vive aquí: se queda en F5 (`WeekAgenda.kt`), que es su
 * casa final, y la fila ya lo llamaba a través de ese límite. Hoy se llama al
 * símbolo del monolito, `internal`; T4-9 lo mueve a F5 sin cambiar su visibilidad.
 *
 * ⚠ Tokens ya resueltos por T3: `dateColumn` 78 → 48dp (T3-1) y `undatedGutter`
 * 88dp → derivado 87dp (T3-7). `touchTargetLegacy` sigue en 44dp (T3-8 → 48).
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
    focusRequesters: MutableMap<String, FocusRequester>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Spacing.maxContentWidth),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            if (groups.isEmpty()) {
                item { EmptyState(title = "No hay actividades para este filtro", message = "") }
            } else {
                groups.forEachIndexed { groupIndex, group ->
                    item(key = "agenda-${group.date?.toString() ?: "sin-fecha"}") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (group.date == null) {
                                Text(
                                    text = NO_START_LABEL,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = AgendaGeometry.undatedGutter, bottom = Spacing.xs),
                                )
                            }
                            group.rows.forEachIndexed { index, row ->
                                // R4-T3-4: separador de 1dp encima de cada fila
                                // menos la primera de TODA la lista. La regla es
                                // mecánica, y por eso no puede duplicarse en una
                                // frontera de grupo: la fila anterior no lleva
                                // borde inferior, así que hay exactamente una
                                // línea, no dos (S4-T3-4b). La regla original —sin
                                // supresión— es la que se envía.
                                if (groupIndex != 0 || index != 0) {
                                    AgendaRowSeparator()
                                }
                                AgendaTimelineRow(
                                    row = row,
                                    date = group.date,
                                    showDate = group.date != null && index == 0,
                                    zone = zone,
                                    resolve = resolve,
                                    onSelectActividad = onSelectActividad,
                                    onConfirm = onConfirm,
                                    isConfirming = isConfirming,
                                    currentUserId = currentUserId,
                                    focusRequesters = focusRequesters,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Separador horizontal de 1dp entre filas de la lista Agenda — `R4-T3-4`.
 *
 * Cromo decorativo: `clearAndSetSemantics {}` y nada más. Los límites de fila los
 * lleva el **único** rótulo fusionado de cada fila (AC-5); un nodo de semántica
 * para la línea sería duplicación.
 *
 * **Sólo el bloque de contenido.** El `start` salta la columna de fecha (que si
 * no partiría el día por la mitad) y la línea del timeline (que se leería como una
 * rotura de la cronología, contra el espíritu de FR-2). El ancho es por tanto
 * `anchoDeFila − dateColumn − 1dp` (`S4-T3-4c`).
 *
 * ⚠ Su token (`Color.kt:40` / `:73`) mide 1.67:1 claro / 1.98:1 oscuro y **eso es
 * correcto**: `prd-v4:80` lo pide gris muy claro. El portador de *"cronología
 * visual clara"* (`prd-v4:54`) es la LÍNEA — T3-5 la mueve a `outline`, 4.37:1 /
 * 5.83:1. Contraste bajo en el separador no es un defecto; en la línea sí lo sería.
 */
@Composable
private fun AgendaRowSeparator() {
    Box(
        modifier = Modifier
            .padding(start = AgendaGeometry.dateColumn + Spacing.hairline)
            .fillMaxWidth()
            .height(Spacing.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .clearAndSetSemantics { },
    )
}

@Composable
internal fun AgendaTimelineRow(
    row: TimelineRow,
    date: LocalDate?,
    showDate: Boolean,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
    focusRequesters: MutableMap<String, FocusRequester>,
) {
    // R4-T1-2 · `SideEffect` + no-leer-en-composición ⇒ escribir el mapa no recompone
    // a nadie: sin bucle. `remember` sin clave porque la posición ya identifica la fila.
    val focusRequester = remember { FocusRequester() }
    SideEffect { focusRequesters[row.actividad.id] = focusRequester }

    val info = resolve(row.actividad.categoryId)
    val categoryColor = categoryTagTriple(info)?.dot ?: MaterialTheme.colorScheme.outline
    val canConfirm = row.actividad.canConfirm(currentUserId)
    val semanticLabel = activitySemanticLabel(row, info, zone)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(AgendaGeometry.dateColumn)
                .padding(end = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showDate && date != null) {
                // R1-T3-1: dos líneas centradas, día arriba / mes abreviado abajo.
                // Sin `maxLines` esto medía 3 líneas (24sp serif en 70dp) y la
                // columna crecía; `maxLines = 1` lo vuelve inalcanzable ante
                // cualquier cambio de fuente futuro.
                Text(
                    text = formatAgendaDay(date),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatAgendaMonth(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // R5-T3-5 / T5-6: la GEOMETRÍA y el color no se tocan — `width(1dp)
        // .fillMaxHeight()` dentro del `Row(height(IntrinsicSize.Min))` y
        // `clearAndSetSemantics { }` viven ahora en `AgendaTimelineLine.kt`, que
        // el esqueleto reutiliza para que `outline` (4.37:1 / 5.83:1) no degrade
        // a `outlineVariant` (1.67:1) justo cuando llega el contenido.
        //
        // NO registrado: la CONTINUIDAD — el `Arrangement.spacedBy(Spacing.xs)` de
        // `WeekAgenda.kt` perfora huecos de 4dp en esta línea reutilizada; eso es
        // FR-22 / A5 / H4, no este cambio.
        AgendaTimelineLine()

        TagDot(
            color = categoryColor,
            modifier = Modifier.padding(horizontal = Spacing.md),
            size = AgendaGeometry.lineNode,
        )

        ActivityBody(
            row = row,
            info = info,
            zone = zone,
            semanticLabel = semanticLabel,
            showTime = true,
            canConfirm = canConfirm,
            onClick = { onSelectActividad(row.actividad.id) },
            // C4: `ActivityBody` aplica su `modifier` ANTES de `.clickable`, el orden que
            // exige `focusRequester`; por eso el fichero no cambia.
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )

        if (canConfirm) {
            Spacer(modifier = Modifier.width(Spacing.xxs))
            ConfirmarChip(
                onClick = { onConfirm(row.actividad.id) },
                enabled = !isConfirming,
                isConfirming = isConfirming,
            )
        }
    }
}
