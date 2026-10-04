package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.canConfirm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Fila de la lista Agenda — `calendar-design-redesign`, T4 (paso 6), F4. **Fichero nuevo.**
 *
 * 🪓 2026-10-04 — `AgendaTimelineRow` sale de `AgendaList.kt` por `activity-swipe-actions`.
 * Motivo MECÁNICO, no estético: el hueco `swipe` que R2-T2-7 colgó de la fila se suma a un
 * fichero que ya estaba en 254 líneas sobre un techo de 250, y el registro de `AgendaList.kt`
 * (F4) autoriza una única válvula —extraer a su propio fichero— sin replanificar. La fila
 * es la pieza que pesa; `AgendaTimeline` y el separador se quedan detrás.
 *
 * 🔴 POR QUÉ ESTA FILA Y NO `DayAgendaRow`. El punto de extensión de R2-T2-7 es la fila de
 * TIMELINE, y lo es porque es la que pintan los TRES sitios desde los que se desliza una
 * actividad: la lista Agenda (`TimelineMode.AGENDA`) y las filas SIN fecha de inicio del
 * timeline de semana. `DayAgendaRow` sólo pinta las filas CON hora de un único día, así que
 * dejarla como dueña del hueco hacía que el gesto funcionara en un sitio y no en los otros
 * dos — y como el hueco llevaba valor por defecto, la omisión compilaba en silencio. Por eso
 * aquí el parámetro NO tiene valor por defecto: omitirlo es un error de compilación, que es
 * justo lo que hacía falta.
 *
 * RR-4 no aplica aquí: sus cuatro piezas (`DayAgendaHeader`, `DayAgendaRow`, `AgendaNode`,
 * `TimelineConnector`) viven en `DayAgenda.kt` y `DayAgendaHeader.kt`. Lo que sí se conserva
 * de F4, intacto, es lo de abajo: los dos nodos de `R4-T3-4`, la columna de fecha, el riel
 * reutilizado y el `focusRequester` en la tarjeta completa.
 */

/**
 * Fila de la lista Agenda — F4 (`calendar-design-redesign`, T4) + hueco de swipe de R2-T2-7.
 *
 * 🔴 `swipe` NO TIENE VALOR POR DEFECTO, y esa es la parte importante de la firma. Con un
 * valor por defecto (`= { c -> c() }`) cualquier llamante que se olvidara de montarlo seguiría
 * compilando, y una fila sin gesto es indistinguible de una fila correcta: es exactamente el
 * fallo que `sdd-verify` encontró en este cambio (gesto montado en `DayAgendaRow` mientras
 * las superficies de lista y de filas sin fecha no lo montaban). Sin default, `kotlinc`
 * señala cada sitio que falta. La comprobación mecánica vive en `SwipeMountingTest`.
 *
 * @param swipe envuelve el CUERPO de la fila, no la fila entera: el `weight` y el
 *   `focusRequester` se quedan en el `Box` de fuera porque `RowColumnParentData` y el destino
 *   del foco los lee la fila que los emite. Un `weight` dentro del hueco sería invisible
 *   para el `Row` de arriba — vive al otro lado de un `BoxWithConstraints`, y los
 *   `ParentDataModifier` no atraviesan un `SubcomposeLayout`.
 */
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
    swipe: @Composable (content: @Composable () -> Unit) -> Unit,
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

        // R2-T2-7 · `weight` y `focusRequester` FUERA del hueco, `ActivityBody` dentro.
        // Es el mismo reparto que `DayAgendaRow` usa para su cuerpo, y por el mismo
        // motivo: el `Box` es lo que el `Row` de arriba puede ponderar y lo que recibe
        // el foco, así que el gesto se monta un nivel más adentro sin tocar la
        // geometría de F4.
        //
        // ⚠ `Box` SIN `semantics`: `R4-T2-4` cuenta nodos de semántica con precisión y
        // un `Box` pelado no crea ninguno, así que el conteo no cambia. Un
        // `Modifier.semantics {}` aquí sí lo movería.
        Box(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        ) {
            swipe {
                ActivityBody(
                    row = row,
                    info = info,
                    zone = zone,
                    semanticLabel = semanticLabel,
                    showTime = true,
                    canConfirm = canConfirm,
                    onClick = { onSelectActividad(row.actividad.id) },
                    // C4: `ActivityBody` aplica su `modifier` ANTES de `.clickable`, el
                    // orden que exige `focusRequester`; por eso el fichero no cambia.
                )
            }
        }

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
