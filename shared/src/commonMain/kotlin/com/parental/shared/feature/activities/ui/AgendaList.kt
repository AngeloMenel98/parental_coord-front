package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.swipe.ActivitySwipeBox
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import kotlinx.datetime.TimeZone

/*
 * Pestaña Agenda — `calendar-design-redesign`, T4 (paso 6). F4.
 *
 * `AgendaTimeline` + el separador. `ActivityBody` NO vive aquí: al medir
 * el archivo con su bloque de imports, F4 pasaba de 250, así que se disparó la
 * válvula de escape que el propio registro autoriza — `ActivityBody` a su propio
 * `ui/ActivityBody.kt` (archivo 10). Sin replanificar nada.
 *
 * 🪓 2026-10-04 — `AgendaTimelineRow` también se va a su propio fichero
 * (`ui/AgendaTimelineRow.kt`), por la misma válvula: el hueco `swipe` de R2-T2-7
 * lo empujaba otra vez sobre 250. Segunda extracción de la MISMA clase,
 * autorizada por el mismo registro de F4. Este fichero queda en la lista y el
 * separador; lo que la fila necesita para el gesto se le pasa desde aquí.
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
    /**
     * R2-T2-7 · ÚNICO punto por el que la lista Agenda dispara una acción de swipe.
     *
     * Mismo contrato que el de `WeekTimeline`: un callback, no un singleton, para que la
     * fila siga siendo una función pura de sus datos. El tipo es `SwipeAction`, de modo
     * que añadir una acción obliga a tocar aquí a propósito y no por olvido.
     */
    onSwipeAction: (String, SwipeAction) -> Unit,
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
                                    // R2-T2-7 · El gesto se monta AQUÍ, en el punto de
                                    // render de la fila, y no dentro de ella: es lo que
                                    // hace que la lista Agenda tenga swipe. Sin este
                                    // argumento la fila NI COMPILA (el hueco no tiene
                                    // valor por defecto), que es la garantía mecánica de
                                    // que la superficie no se vuelva a quedar sin gesto.
                                    swipe = { content ->
                                        ActivitySwipeBox(
                                            actividad = row.actividad,
                                            onSwipe = { action ->
                                                onSwipeAction(row.actividad.id, action)
                                            },
                                            content = content,
                                        )
                                    },
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
