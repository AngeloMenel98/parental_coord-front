package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.components.isDoneStatus
import com.parental.shared.core.ui.components.isOverdueStatus
import com.parental.shared.core.ui.components.statusTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.activities.domain.attributionLine
import com.parental.shared.feature.activities.domain.model.resolveChildNames
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.thirdparty.ui.initialsOf
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Tarjeta de actividad — dirección de diseño 02 ("Personas al frente"), T1/T2 de
 * `odd/tasks/activity-card-stack.md` (S1, S2). El CSS final del artboard es la verdad
 * de las proporciones: persona asignataria en columna de avatar (40dp + rol), título +
 * píldora de estado, hora y límite en UNA línea, atribución del creador, hijos en una
 * línea de texto y categoría con dot en el pie.
 *
 * REEMPLAZA a la fila de la agenda (columna de fecha + riel + `ActivityBody`) en el modo
 * AGENDA. El modo SEMANA no se toca: sigue pintando con `AgendaTimelineRow`.
 *
 * ⚠ CERO COLORES NUEVOS. La tarjeta es una RECOMPOSICIÓN de piezas que ya existen:
 * `TagDot` (punto), `categoryTagTriple`/`statusTagTriple` (color de etiqueta),
 * `TypeTokens.time` (la hora monoespaciada), `ParentalShapes.medium` (radio 12),
 * `secondaryContainer`/`onSecondaryContainer` (el avatar). No se declara ningún hex ni
 * ningún token de tema — `Color.kt` sigue siendo su único dueño.
 *
 * ⚠ EL BOTÓN "CONFIRMAR" ES HERMANO DEL CUERPO, NO HIJO (regla `R2-T2-8`, que el
 * `sdd-verify` de `activity-swipe-actions` convirtió en ley). Anidarlo dentro del área
 * clickeable haría que un toque cerca del botón ABRIERA EL DETALLE en vez de confirmar.
 * Por eso el cuerpo clickeable (título + hora + límite) y la fila de pie (categoría +
 * botón) son DOS hermanos: un nodo de semántica y el otro.
 *
 * EL COLOR DE CATEGORÍA YA NO BORDEA LA TARJETA: el artboard lo vive en el PUNTO DEL
 * RIEL a la izquierda (`.node`) y en el pie. El punto se dibuja aquí, dentro de esta
 * card, con offset lateral negativo para caer sobre la línea del riel que la lista
 * pinta como fondo del contenedor (ver `AgendaList.kt`); así no hay medición cruzada
 * entre ítems. Los offsets los fija `agendaRailDotOffset`, pura y probada en
 * `AgendaRailTest`.
 */

/**
 * Tarjeta de una actividad, con su hueco de gesto y su punto del riel.
 *
 * @param swipe envuelve la TARJETA entera. NO TIENE VALOR POR DEFECTO: la omisión tiene
 *   que ser un error de compilación, no un fallo silencioso (misma regla que el hueco de
 *   `AgendaTimelineRow`).
 * @param focusRequesters destino de foco por id — lo registra el `SideEffect`, igual que
 *   la fila de la agenda, para que el retorno de foco desde la hoja de detalle encuentre
 *   la tarjeta y no el contenedor de la lista.
 * @param railDot dibuja el punto de categoría sobre el riel. La pila (`S3`) lo APAGA:
 *   su franja se marca con el dot-contador que `AgendaList` dibuja, y dos marcas sobre
 *   el riel serían una mentira visual.
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
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    swipe: @Composable (content: @Composable () -> Unit) -> Unit,
    railDot: Boolean = true,
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
                memberNames = memberNames,
                children = children,
                onClick = { onSelectActividad(row.actividad.id) },
                onConfirm = { onConfirm(row.actividad.id) },
            )
        }
        // ⚠ FUERA del hueco de gesto: el punto vive en el FONDO de la pantalla
        // (surfaceVariant), no en la tarjeta que se desplaza al swipe; por eso es
        // hermano del hueco y se dibuje después, sobre él.
        if (railDot) {
            RailDot(
                info = info,
                overdue = isOverdueRow(row),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = agendaRailDotOffset(railLeading = 0.dp))
                    .size(AgendaRailDotBox),
            )
        }
    }
}

// ── Punto del riel (artboard `.node`) y su geometría, probada en `AgendaRailTest` ──

/** Núcleo del punto: 10dp de diámetro, como el `.node` del artboard. */
internal val AgendaRailDotRadius: Dp = 5.dp

/** Aureola del color de la card entre núcleo y fondo: `.node` lleva 2px de borde. */
internal val AgendaRailDotHalo: Dp = 2.dp

/** Radio del anillo punteado del vencimiento (`.ring-over` envuelve al `.node`). */
internal val AgendaRailRingRadius: Dp = 11.dp

/** Trazo del anillo punteado: 2dp, el grosor del `.ring-over` del artboard. */
internal val AgendaRailRingWidth: Dp = 2.dp

/** Punteo del anillo, en px: línea 6, hueco 6, dentro del `DrawScope`. */
private val AgendaRailRingDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

/** Caja del punto: todo lo que alcanza el dibujo, anillo incluido. */
internal val AgendaRailDotBox: Dp = AgendaRailRingRadius * 2

/**
 * Offset lateral del punto respecto del borde izquierdo de la card para que su centro
 * caiga sobre el centro de la línea del riel (geometría en `AgendaList.kt`);
 * `railLeading` es el inset EXTRA que la pila le deja a los fantasmas (0 en una card
 * normal, y la pila apaga su punto con `railDot = false`).
 */
internal fun agendaRailDotOffset(railLeading: Dp): Dp =
    AgendaRailCenter - AgendaListStartPadding - railLeading - AgendaRailDotBox / 2

/**
 * ¿Está vencida? La derivación CANÓNICA es `isOverdueStatus` (`TagColors.kt`: el mapa
 * de alias de `Color.kt`); esto era una segunda copia local de la comparación — la única
 * que el propio `TagColors.kt` esperaba retirar. El anillo del riel y la meta roja leen
 * ESTA función: dos lugares, UNA verdad.
 */
internal fun isOverdueRow(row: TimelineRow): Boolean =
    isOverdueStatus(row.actividad.status)

@Composable
private fun RailDot(info: CategoryInfo?, overdue: Boolean, modifier: Modifier = Modifier) {
    val color = categoryTagTriple(info)?.dot ?: MaterialTheme.colorScheme.outline
    val haloColor = MaterialTheme.colorScheme.surface
    val ringColor = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier.drawBehind {
                // Núcleo + aureola: el `.node` del artboard es un círculo de 10px con borde
                // de 2px del color de la card, y así se diferencia del fondo sin tercer color.
                drawCircle(color = haloColor, radius = (AgendaRailDotRadius + AgendaRailDotHalo).toPx())
                drawCircle(color = color, radius = AgendaRailDotRadius.toPx())
                if (overdue) {
                    drawCircle(
                        color = ringColor,
                        radius = AgendaRailRingRadius.toPx(),
                        style = Stroke(
                            width = AgendaRailRingWidth.toPx(),
                            pathEffect = AgendaRailRingDash,
                        ),
                    )
                }
            },
    )
}

@Composable
private fun ActivityCardBody(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    semanticLabel: String,
    canConfirm: Boolean,
    isConfirming: Boolean,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    val category = categoryTagTriple(info)
    val isDone = isDoneStatus(row.actividad.status)
    val overdue = isOverdueRow(row)
    // Asignatario con nombre RESUELTO: sin mapping (back viejo, id desconocido) la columna
    // no existe — un avatar "?" por su cuenta diría una asignación que nadie puede leer.
    // El rótulo combinado de la tarjeta sigue anunciando al asignatario (S7).
    val assigneeName = row.actividad.assignedTo
        ?.let { memberNames[it]?.takeIf { name -> name.isNotBlank() } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Columna de la persona (artboard `.who`) + cuerpo: dos columnas de un Row, como
        // el `.card { grid-template-columns: 40px 1fr }` del artboard, y la persona fuera
        // del cuerpo clickeable: es adorno del rótulo. El color de categoría ya no borde la
        //  tarjeta — vive en el punto del riel y en el pie.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            assigneeName?.let { name -> AssigneeLead(name = name) }
            Column(
                modifier = Modifier.weight(1f),
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

                    // ── Meta en UNA línea (artboard `.meta`): hora mono, separador
                    // vertical hairline y límite comprimidos en la misma fila — antes
                    // eran dos líneas apiladas.
                    val hasTime = row.localStart != null
                    val hasDeadline = row.deadline != null
                    if (hasTime || hasDeadline) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            row.localStart?.let { localStart ->
                                Text(
                                    text = formatTime(localStart.hour, localStart.minute),
                                    style = TypeTokens.time,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (hasTime && hasDeadline) {
                                Box(
                                    modifier = Modifier
                                        .width(Spacing.hairline)
                                        .height(Spacing.md)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            row.deadline?.let { deadline ->
                                DeadlineLabel(deadline = deadline, zone = zone, overdue = overdue)
                            }
                        }
                    }

                    // S7/S8 · El asignatario vive en la COLUMNA DE LA PERSONA; dentro del
                    // cuerpo clickeable, bajo la misma etiqueta de semántica fusionada,
                    // quedan la atribución del creador terciario y los hijos.

                    // S3/S5 (thirdparty-identity): atribución del creador terciario.
                    // Una sola policy decide la línea; el servidor ya filtró el nombre.
                    val attributionName = attributionLine(row.actividad.attribution)
                    if (attributionName != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        AssigneeLine(name = attributionName)
                    }

                    val childNames = remember(row.actividad.childrenIds, children) {
                        resolveChildNames(children, row.actividad.childrenIds)
                    }
                    if (childNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        ChildrenLine(names = childNames)
                    }
                }

                // ── Pie: categoría + acción. FUERA del cuerpo clickeable ────────
                // 🔴 SIN CHIP "CONFIRMADA" (A-1 · R4). El dato POR PERSONA vive en la hoja
                // de detalle ("Asignación confirmada"); en la tarjeta la insignia de
                // estado ya dice cómo va la actividad, y un segundo aviso la contradice.
                // Por eso aquí ya no se mira `assignedConfirmed` para nada.
                if (canConfirm || info != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    // Filete del pie (artboard `.foot { border-top ... }`).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.hairline)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
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

/** Columna de la persona asignataria: 40dp, como el `.who` del artboard. */
private val LeadColumnWidth: Dp = 40.dp

/** Avatar circular del asignatario: 40dp (S1, artboard `.avatar.assignee`). */
private val LeadAvatarSize: Dp = 40.dp

/**
 * Columna de la persona asignataria (artboard `.who`): avatar circular de 40dp con las
 * iniciales y el nombre en pequeño debajo. Las iniciales NO se re-derivan aquí:
 * `initialsOf` ya existe en `feature/thirdparty/ui` ("MG para Marta Gómez") y es
 * `internal` del módulo. El color es el par `secondaryContainer`/`onSecondaryContainer`
 * del esquema — cero colores nuevos.
 */
@Composable
private fun AssigneeLead(name: String) {
    Column(modifier = Modifier.width(LeadColumnWidth)) {
        Box(
            modifier = Modifier
                .size(LeadAvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Línea de asignatario — S7. Se omite si no hay nombre resuelto (sin "Sin asignar"). */
@Composable
private fun AssigneeLine(name: String) {
    Text(
        text = "Asignada a: $name",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Línea de hijos — S8. No se pinta si no hay nombres resueltos.
 *
 * 🔴 LOS NOMBRES EN UNA LÍNEA, NO UNA FILA DE CHIPS. El PRD sugiere "avatares o una
 *   lista compacta de chips (`+N` si desborda)", pero medir chips con
 *   `SubcomposeLayout` dentro de una tarjeta de una lista es un motor de medición por
 *   cada tarjeta — y su caso degradado es malo: un nombre largo hace que NINGÚN chip
 *   quepa y la tarjeta acaba mostrando "+1" sin el nombre de nadie. Una línea con los
 *   dos primeros nombres y el "+N" del resto dice lo mismo, no se corta a medias y no
 *   mide nada.
 */
@Composable
private fun ChildrenLine(names: List<String>) {
    if (names.isEmpty()) return
    val visible = names.take(MAX_VISIBLE_CHILDREN)
    val overflow = names.size - visible.size
    val suffix = if (overflow > 0) " +$overflow" else ""
    Text(
        text = "Para: ${visible.joinToString(", ")}$suffix",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Cuántos hijos se nombran en la tarjeta antes de resumir el resto como "+N". */
private const val MAX_VISIBLE_CHILDREN = 2
