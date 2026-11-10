package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.components.isDoneStatus
import com.parental.shared.core.ui.components.isOverdueStatus
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.RailGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.activities.ui.swipe.ActivitySwipeBox
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import kotlinx.datetime.TimeZone

/*
 * Lienzo del RIEL del tiempo — T2 + T3 de `odd/tasks/activity-rail.md` (S3, S11).
 * **Archivo nuevo.** Traducción del artboard `03-riel-del-tiempo.html`: canaleta de horas
 * (T2) y bloque proporcional (T3). No se copia HTML.
 *
 * NO HACE: montarse en `ActivitiesScreen` (T6), borrar `DayAgenda`/`WeekAgenda` (T8),
 * dibujar "Confirmar" ni una casilla (S8), leer el reloj (`nowMinute` y `zone` entran por
 * parámetro) ni re-derivar geometría (`RailDay`/`RailBlock` de T1 ya traen eje, carriles y
 * altura; aquí sólo se convierten a dp).
 *
 * Z-ORDER — una sola `Box`: grilla → línea de "ahora" → bloques. La línea va POR DEBAJO a
 * propósito: cruzada encima taparía el texto de la actividad en curso.
 *
 * Nada de hex, nada de `!!`, ningún dp crudo: los valores viven en `RailGeometry`.
 */

/** Minutos por hora — la misma constante de `RailDerivation.kt`, que allí es `private` y no se
 *  comparte. */
private const val MINUTES_PER_HOUR = 60

/** Paso de la línea secundaria de la grilla. */
private const val HALF_HOUR_MINUTES = 30

/**
 * Canaleta de horas — T2 ("eje horario por día").
 *
 * Ancho [AgendaGeometry.timeColumn]; una etiqueta `TypeTokens.time` por tick, a la derecha y
 * en `onSurfaceVariant`; alto = `railOffsetDp(axis.endMinute, …)`, el MISMO que [RailDayCanvas]
 * usa: dos valores paralelos que pueden separarse se separarán. La etiqueta se centra sobre SU
 * línea con media altura de línea del propio estilo (derivada, no un dp literal) y se recorta a
 * 0 en el primer tick: no hay `clip` y un tick negativo se leería como parte del encabezado de
 * día de T4.
 *
 * Los ticks salen de `railHourTicks` en `RailDerivation.kt` — la MISMA función que alimenta
 * `RailDay.hourTicks` —, así la hora etiquetada y la línea dibujada no pueden separarse.
 */
@Composable
internal fun RailHourGutter(axis: RailAxis, dpPerHour: Float, modifier: Modifier = Modifier) {
    val axisHeight = railOffsetDp(axis.endMinute, axis, dpPerHour).dp
    val halfLabelHeight = with(LocalDensity.current) { TypeTokens.time.lineHeight.toDp() / 2 }
    Box(modifier = modifier.width(AgendaGeometry.timeColumn).height(axisHeight).padding(end = Spacing.sm)) {
        // Nodo de TEXTO real: la canaleta es la única pieza que dice dónde cae cada hora.
        railHourTicks(axis).forEach { minute ->
                Text(
                    text = formatTime(minute / MINUTES_PER_HOUR, 0),
                    style = TypeTokens.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().offset(
                        y = (railOffsetDp(minute, axis, dpPerHour).dp - halfLabelHeight).coerceAtLeast(0.dp),
                    ),
                )
            }
    }
}

/**
 * Lienzo de un día del riel — T3 (`S3`, `S11`).
 *
 * Ancho = contenedor, alto = eje × [dpPerHour]. El ancho de cada carril sale de
 * `railLaneFraction` sobre el ancho REAL (`BoxWithConstraints`), así que el mismo `RailBlock`
 * se pinta correcto a 320dp y a 640dp sin tabla de anchos. Un id de categoría ausente no es
 * error (cae en `outline`/`surfaceVariant`); `onOpen` abre el detalle y NO confirma (S8).
 *
 * @param nowMinute minuto de día de la línea de "ahora", o `null` si el día no es hoy: lo
 *   calcula la pantalla. Fuera del eje no se dibuja.
 * @param minBlockHeight piso de toque; T7 lo necesita para el estado de carga.
 * @param onSwipeAction T7 (L11) · ÚNICO punto por el que un bloque dispara una acción de swipe.
 *   **Sin valor por defecto**: el día entero se quedaría sin gesto en silencio si lo tuviera.
 * @param focusRequesters T7 (R4) · id → `FocusRequester` que registra cada bloque: es el mapa que
 *   la pantalla lee para devolver el foco al bloque que abrió la hoja de detalle.
 */
@Composable
internal fun RailDayCanvas(
    railDay: RailDay,
    categories: Map<String, CategoryInfo>,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    zone: TimeZone,
    onOpen: (TimelineRow) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    nowMinute: Int? = null,
    modifier: Modifier = Modifier,
    dpPerHour: Float = RailGeometry.DpPerHour,
    minBlockHeight: Dp = RailGeometry.MinBlockHeight,
) {
    val axis = railDay.axis
    val canvasHeight = railOffsetDp(axis.endMinute, axis, dpPerHour).dp
    // Un "ahora" fuera del eje no se pinta: la línea caería sobre el encabezado de día de
    // T4, y pegarla al borde diría una hora falsa.
    val nowLineY = nowMinute
        ?.takeIf { minute -> minute in axis.startMinute..axis.endMinute }
        ?.let { minute -> railOffsetDp(minute, axis, dpPerHour).dp }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(canvasHeight)) {
        val contentWidth = maxWidth
        RailHourGrid(axis, railDay.hourTicks, dpPerHour, Modifier.fillMaxSize())
        if (nowLineY != null) {
            RailNowLine(nowLineY, Modifier.fillMaxWidth())
        }
        railDay.blocks.forEach { block ->
            val lane = railLaneFraction(block)
            // `.pill` del artboard: el primer carril arranca en el borde y los demás se separan
            // [RailGeometry.LaneGap] para que dos bloques contiguos no se lean como uno. El ancho
            // nunca es negativo: un `width` negativo es una excepción, no un layout, así que el
            // suelo es el propio hueco.
            val laneX = contentWidth * lane.start + (if (block.lane == 0) 0.dp else RailGeometry.LaneGap)
            val laneWidth = (contentWidth * (lane.endInclusive - lane.start) - RailGeometry.LaneGap)
                .coerceAtLeast(RailGeometry.LaneGap)
            val info = categories[block.row.actividad.categoryId]
            RailBlockPill(
                block = block,
                info = info,
                semanticLabel = railBlockSemanticLabel(block, info, zone, memberNames, children),
                x = laneX,
                y = railOffsetDp(block.startMinute, axis, dpPerHour).dp,
                width = laneWidth,
                height = railHeightDp(block, dpPerHour, minBlockHeight.value).dp,
                focusRequesters = focusRequesters,
                onClick = { onOpen(block.row) },
                // T7 (R2-T2-7, L11) · El gesto se monta AQUÍ, en el punto de render; el bloque
                // sólo declara el hueco. `ActivitySwipeBox` se reutiliza TAL CUAL —sin un
                // parámetro nuevo ni un cambio de su contrato de ranura— porque el alto de
                // entrada está ACOTADO: la caja de foco mide `.height(height)`, así que su
                // `fillCrossAxis` por defecto encuentra un máximo y no colapsa a 0 (la trampa
                // documentada del envoltorio). El alto MEDIDO del bloque no cambia: el envoltorio
                // mide exactamente el `height` del eje.
                swipe = { content ->
                    ActivitySwipeBox(
                        actividad = block.row.actividad,
                        modifier = Modifier.fillMaxSize(),
                        onSwipe = { action ->
                            onSwipeAction(block.row.actividad.id, action)
                        },
                        content = content,
                    )
                },
            )
        }
    }
}

/**
 * Grilla del eje: una línea por tick horario y una de media hora entre cada dos.
 *
 * `outlineVariant` es el rol del `--outline-variant` del artboard (separador del calendario),
 * no `outline`, que es el trazo que carga cronología. La media hora es la MISMA tinta con
 * [RailGeometry.HalfHourLineAlpha]: sin color nuevo y sin un segundo rol cuya AA nadie
 * verifica. Trazo [Spacing.hairline] (1dp).
 */
@Composable
private fun RailHourGrid(axis: RailAxis, hourTicks: List<Int>, dpPerHour: Float, modifier: Modifier) {
    val hourColor = MaterialTheme.colorScheme.outlineVariant
    val halfHourColor = hourColor.copy(alpha = RailGeometry.HalfHourLineAlpha)
    Canvas(modifier = modifier) {
        val stroke = Spacing.hairline.toPx()
        fun drawTick(minute: Int, color: Color) {
            val y = railOffsetDp(minute, axis, dpPerHour).dp.toPx()
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
        hourTicks.forEach { tick ->
            drawTick(tick, hourColor)
            if (tick + HALF_HOUR_MINUTES <= axis.endMinute) drawTick(tick + HALF_HOUR_MINUTES, halfHourColor)
        }
    }
}

/**
 * Línea de "ahora": 2dp de `primary` a todo el ancho, por debajo de los bloques.
 *
 * `clearAndSetSemantics { }` — decorativa. La hora ya la dicen el reloj del sistema y los
 * bloques vecinos, y un nodo sin acción sólo interrumpe el barrido; el artboard dibuja su
 * etiqueta de hora, pero aquí duplicaría la información.
 */
@Composable
private fun RailNowLine(y: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .offset(y = y)
            .height(RailGeometry.NowLineStroke)
            .background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics { },
    )
}

/**
 * Un bloque de actividad — T3.
 *
 * Fondo `container` de la categoría, riel de [RailGeometry.CategoryRail] con el `dot` y columna
 * de texto. Radio [ParentalShapes.medium] y sin sombra: una sombra por bloque en un eje de 14
 * horas es textura, no elevación. El alto llega resuelto por `railHeightDp`, así que el objetivo
 * táctil de S11 es el bloque entero (carril × alto, `role = Button`). TRES DENSIDADES, y el
 * estado siempre es PALABRA:
 *  · `≥ ChipRowMinHeight` → título (2 líneas) + `hora–hora · duración` + `StatusTag` al pie.
 *  · entre `ShortBlockHeight` y `ChipRowMinHeight` → lo mismo sin chip: no cabe.
 *  · `< ShortBlockHeight` → título a 1 línea + `hora–hora · estado` en una sola línea.
 *
 * Un bloque `Done` baja a [RailGeometry.DoneAlpha], tacha el título Y conserva el estado en
 * palabras: la opacidad nunca es la única señal.
 *
 * T7 · DOS CAJAS, y el reparto importa. La EXTERNA es geometría (posición, tamaño, foco) y la
 * INTERNA es la píldora. El hueco `swipe` se monta entre las dos para que:
 *  · el `FocusRequester` quede FUERA del envoltorio de gesto (un `ParentDataModifier` no lo
 *    atraviesa — R2-T2-7), igual que en `ActivityCard`; y
 *  · el desplazamiento del gesto mueva el bloque DENTRO de su propio rectángulo, sin tocar la
 *    x/y del eje ni el alto que reserva el día.
 */
@Composable
private fun RailBlockPill(
    block: RailBlock,
    info: CategoryInfo?,
    semanticLabel: String,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    focusRequesters: MutableMap<String, FocusRequester>,
    onClick: () -> Unit,
    swipe: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // R4-T1-2 (mismo patrón que la tarjeta y la fila): `SideEffect` + no-leer-en-composición ⇒
    // escribir el mapa no recompone a nadie.
    SideEffect { focusRequesters[block.row.actividad.id] = focusRequester }

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .width(width)
            .height(height)
            .focusRequester(focusRequester),
    ) {
        swipe {
            RailBlockBody(
                block = block,
                info = info,
                semanticLabel = semanticLabel,
                height = height,
                onClick = onClick,
            )
        }
    }
}

/** Píldora del bloque: fondo, riel de categoría, texto y estado. Ocupa su caja entera. */
@Composable
private fun RailBlockBody(
    block: RailBlock,
    info: CategoryInfo?,
    semanticLabel: String,
    height: Dp,
    onClick: () -> Unit,
) {
    val triple = categoryTagTriple(info)
    val status = block.row.actividad.status
    val isDone = isDoneStatus(status)
    val isShort = height < RailGeometry.ShortBlockHeight
    val showsChip = height >= RailGeometry.ChipRowMinHeight
    // Corto dice hora Y estado, porque el chip no cabe; alto dice hora Y duración y deja el
    // estado al chip.
    val meta = if (isShort) {
        "${railRangeLabel(block)} · ${statusSemanticLabel(status)}"
    } else {
        "${railRangeLabel(block)} · ${railDurationLabel(block)}"
    }
    val overdueBorder = if (isOverdueStatus(status)) {
        Modifier.border(RailGeometry.OverdueBorderWidth, MaterialTheme.colorScheme.error, ParentalShapes.medium)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isDone) RailGeometry.DoneAlpha else 1f)
            .clip(ParentalShapes.medium)
            .background((triple?.dot ?: MaterialTheme.colorScheme.outline).copy(alpha = RailGeometry.BlockTintAlpha))
            .then(overdueBorder)
            .clickable(role = Role.Button, onClick = onClick)
            // UN nodo por bloque. `contentDescription` SUSTITUYE al texto visible, por eso
            // `railBlockSemanticLabel` añade la duración que el bloque alto sí muestra.
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(RailGeometry.CategoryRail)
                    .fillMaxHeight()
                    .background(triple?.dot ?: MaterialTheme.colorScheme.outline),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = block.row.actividad.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    // Dos líneas en un bloque corto se comen la línea de hora.
                    maxLines = if (isShort) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = TypeTokens.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showsChip) {
                    // `margin-top:auto`: el chip va al PIE, no pegado al rango — así un bloque
                    // largo no se lee como un párrafo con una etiqueta colgando.
                    Spacer(modifier = Modifier.weight(1f))
                    StatusTag(status = status)
                }
            }
        }
    }
}

/**
 * Rótulo accesible del bloque — S11: título, rango, duración y estado.
 *
 * `activitySemanticLabel` ya compone fecha, hora, título, categoría, tipo, estado y
 * responsables, y es la ÚNICA frase que anuncia una actividad en el calendario: reescribirla
 * aquí sería una segunda fuente de copy. Le falta la DURACIÓN, que el bloque sí muestra, así
 * que se añade al final en vez de construir un rótulo paralelo.
 */
private fun railBlockSemanticLabel(
    block: RailBlock,
    info: CategoryInfo?,
    zone: TimeZone,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
): String = "${activitySemanticLabel(block.row, info, zone, memberNames, children)}, " +
    "Duración ${railDurationLabel(block)}"

/**
 * Rango visible: `"10:00–11:00"`. Reutiliza [formatTime] con los minutos de día de
 * [RailBlock]; un fin recortado a medianoche sale `"24:00"`, que es lo que ocupa en el eje.
 */
private fun railRangeLabel(block: RailBlock): String {
    val start = formatTime(block.startMinute / MINUTES_PER_HOUR, block.startMinute % MINUTES_PER_HOUR)
    val end = formatTime(block.endMinute / MINUTES_PER_HOUR, block.endMinute % MINUTES_PER_HOUR)
    return "$start–$end"
}

/**
 * Duración legible: `"30 min"`, `"1 h"`, `"1 h 30 min"` — el copy de `durLabel` del artboard.
 *
 * Local y `private` porque el proyecto no tiene formateador de duraciones: `TimelineFormatters`
 * tiene fechas, horas y límites. Si otra pantalla lo necesita, ese es el momento de subirlo.
 */
private fun railDurationLabel(block: RailBlock): String {
    val minutes = block.endMinute - block.startMinute
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}
