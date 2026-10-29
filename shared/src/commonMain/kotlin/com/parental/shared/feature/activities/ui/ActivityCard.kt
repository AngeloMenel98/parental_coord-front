package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.components.isDoneStatus
import com.parental.shared.core.ui.components.statusTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Tarjeta de actividad — dirección de diseño 03 ("Tarjetas a la vista"), T2 de
 * `odd/tasks/activities-cards-ui.md` (S2, S4).
 *
 * REEMPLAZA a la fila de la agenda (columna de fecha + riel + `ActivityBody`) en el modo
 * AGENDA. El modo SEMANA no se toca: sigue pintando con `AgendaTimelineRow`.
 *
 * ⚠ CERO COLORES NUEVOS. La tarjeta es una RECOMPOSICIÓN de piezas que ya existen:
 * `TagDot` (punto), `categoryTagTriple`/`statusTagTriple` (color de etiqueta),
 * `TypeTokens.time` (la hora monoespaciada), `ParentalShapes.medium` (radio 12). No se
 * declara ningún hex ni ningún token de tema — `Color.kt` sigue siendo su único dueño.
 *
 * ⚠ EL BOTÓN "CONFIRMAR" ES HERMANO DEL CUERPO, NO HIJO (regla `R2-T2-8`, que el
 * `sdd-verify` de `activity-swipe-actions` convirtió en ley). Anidarlo dentro del área
 * clickeable haría que un toque cerca del botón ABRIERA EL DETALLE en vez de confirmar.
 * Por eso el cuerpo clickeable (título + hora + límite) y la fila de pie (categoría +
 * botón) son DOS hermanos: un nodo de semántica y el otro.
 */

/**
 * Tarjeta de una actividad, con su hueco de gesto.
 *
 * @param swipe envuelve la TARJETA entera. NO TIENE VALOR POR DEFECTO: la omisión tiene
 *   que ser un error de compilación, no un fallo silencioso (misma regla que el hueco de
 *   `AgendaTimelineRow`).
 * @param focusRequesters destino de foco por id — lo registra el `SideEffect`, igual que
 *   la fila de la agenda, para que el retorno de foco desde la hoja de detalle encuentre
 *   la tarjeta y no el contenedor de la lista.
 */
@Composable
internal fun ActivityCard(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    semanticLabel: String,
    canConfirm: Boolean,
    isConfirming: Boolean,
    focusRequesters: MutableMap<String, FocusRequester>,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    swipe: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    // R4-T1-2 (mismo patrón que la fila): `SideEffect` + no-leer-en-composición ⇒
    // escribir el mapa no recompone a nadie.
    val focusRequester = remember { FocusRequester() }
    SideEffect { focusRequesters[row.actividad.id] = focusRequester }

    // El `focusRequester` vive FUERA del hueco de gesto: el hueco es un
    // `SubcomposeLayout` y los `ParentDataModifier` no lo atraviesan (R2-T2-7).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    ) {
        swipe {
            ActivityCardBody(
                row = row,
                info = info,
                zone = zone,
                semanticLabel = semanticLabel,
                canConfirm = canConfirm,
                isConfirming = isConfirming,
                onClick = { onSelectActividad(row.actividad.id) },
                onConfirm = { onConfirm(row.actividad.id) },
            )
        }
    }
}

@Composable
private fun ActivityCardBody(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    semanticLabel: String,
    canConfirm: Boolean,
    isConfirming: Boolean,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    val category = categoryTagTriple(info)
    val railColor = category?.dot ?: MaterialTheme.colorScheme.outline
    val isDone = isDoneStatus(row.actividad.status)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            // `IntrinsicSize.Min` + `fillMaxHeight()` en el riel: es el mismo patrón que
            // usa la línea del timeline. Sin él, el riel mediría 0 en una `LazyColumn`
            // (alto no acotado) y la tarjeta perdería su color de categoría.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(Spacing.xs)
                    .fillMaxHeight()
                    .background(railColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md),
            ) {
                // ── Cuerpo clickeable: UN nodo de semántica ──────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AgendaGeometry.touchTargetLegacy)
                        .clickable(onClick = onClick)
                        .semantics(mergeDescendants = true) { contentDescription = semanticLabel },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = row.actividad.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                            color = if (isDone) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        StatusPill(status = row.actividad.status)
                    }

                    row.localStart?.let { localStart ->
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = formatTime(localStart.hour, localStart.minute),
                            style = TypeTokens.time,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    row.deadline?.let { deadline ->
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        DeadlineLabel(deadline = deadline, zone = zone, overdue = isOverdue(row))
                    }
                }

                // ── Pie: categoría + acción. FUERA del cuerpo clickeable ────────
                if (canConfirm || info != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryLabel(info = info, category = category)
                        if (canConfirm) {
                            Spacer(modifier = Modifier.weight(1f))
                            ConfirmButton(
                                isConfirming = isConfirming,
                                onClick = onConfirm,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Insignia de estado: punto + etiqueta sobre el contenedor de alfa 0,15 — la receta de
 * `StatusTag`, con el punto delante. El punto es decorativo (`TagDot` limpia su
 * semántica); el estado lo anuncia el rótulo combinado de la tarjeta.
 */
@Composable
private fun StatusPill(status: String) {
    val dot = statusTagTriple(status)?.dot ?: MaterialTheme.colorScheme.outline
    Surface(
        shape = ParentalShapes.medium,
        color = dot.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagDot(color = dot, size = AgendaGeometry.statusBadge)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = statusSemanticLabel(status),
                style = MaterialTheme.typography.labelSmall,
                color = dot,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Etiqueta de categoría: punto + nombre, en el color de contenido de la categoría. */
@Composable
private fun CategoryLabel(info: CategoryInfo?, category: com.parental.shared.core.ui.theme.TagTriple?) {
    val dot = category?.dot ?: MaterialTheme.colorScheme.outline
    val content = category?.content ?: MaterialTheme.colorScheme.outline
    TagDot(color = dot, size = AgendaGeometry.statusBadge)
    Spacer(modifier = Modifier.width(Spacing.xs))
    Text(
        text = info?.name?.takeIf { it.isNotBlank() } ?: "Categoría",
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

/**
 * Botón "Confirmar" en la forma del arteboard (contorno `primary`, fondo transparente).
 *
 * `OutlinedButton` y no un `Surface` + `clickable` a mano: el botón de Material aplica
 * `minimumInteractiveComponentSize()`, así que el objetivo táctil real es de 48dp aunque
 * la caja visible mida menos — S8 exige 48dp y el arteboard dibujaba 36dp.
 */
@Composable
private fun ConfirmButton(
    isConfirming: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isConfirming,
        shape = ParentalShapes.medium,
    ) {
        if (isConfirming) {
            CircularProgressIndicator(
                modifier = Modifier.height(Spacing.md),
                strokeWidth = ProgressStroke.thin,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.height(Spacing.md),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "Confirmar",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Línea de límite: "Límite: 12 may 16:00", o "Límite vencido: …" cuando la actividad
 * está vencida. El límite se pinta en `error` sólo cuando la actividad lo está: el
 * color nunca es la única señal — la insignia de estado dice "Vencida" en palabras.
 */
@Composable
private fun DeadlineLabel(deadline: Instant, zone: TimeZone, overdue: Boolean) {
    val local = deadline.toLocalDateTime(zone)
    val stamp = "${formatDeadline(deadline, zone)} ${formatTime(local.hour, local.minute)}"
    Text(
        text = if (overdue) "Límite vencido: $stamp" else "Límite: $stamp",
        style = MaterialTheme.typography.bodySmall,
        color = if (overdue) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * ¿Está vencida? El estado llega en minúsculas del backend (`overdue`). No hay mapa de
 * alias aquí: es el MISMO valor que `statusSemanticLabel` traduce a "Vencida", y la
 * insignia de estado ya lo dice en palabras.
 */
private fun isOverdue(row: TimelineRow): Boolean =
    row.actividad.status.uppercase() == "OVERDUE"
