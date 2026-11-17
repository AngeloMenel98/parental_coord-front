package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.RailGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.activities.ui.swipe.ActivitySwipeBox
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Secciones de día del RIEL del tiempo — T4 de `odd/tasks/activity-rail.md` (S3, S5, S11).
 * **Archivo nuevo.** Traducción del artboard `design/activity-calendar-ui/03-riel-del-tiempo.html`:
 * encabezado de día pegajoso y plegable, cuerpo del día (canaleta + lienzo de T2/T3) y la
 * sección "Sin fecha de inicio". No se copia HTML.
 *
 * NO HACE: montarse en `ActivitiesScreen` (T6), borrar `AgendaList`/`WeekAgenda`/`DayAgenda`
 * (T8), dibujar el mapa de densidad (T5), leer el reloj (`today` y `nowMinute` entran por
 * parámetro) ni re-derivar el día (`RailDay` de T1 ya trae eje, bloques y ticks).
 *
 * 🔴 LA FORMA DE LA LISTA LA DECIDE `RailIndex.kt`, NO ESTE ARCHIVO. Cada sección pone UN
 * encabezado (`stickyHeader`) más `railContentCount(section)` ítems de contenido. El salto a
 * un día (T6) pide su índice a `railItemIndexOf` sobre la MISMA lista, así que el número y
 * esta `LazyColumn` no pueden separarse: si un `item` de más o de menos aparece aquí, la
 * prueba pura de `RailIndexTest` sigue en verde y el salto aterriza en el día equivocado.
 *
 * Nada de hex, nada de dp crudo: `Spacing`, `AgendaGeometry`, `RailGeometry`, `ParentalShapes`
 * y `MaterialTheme.colorScheme`.
 */

/** Chip de "hoy" del encabezado. Misma palabra que ya usa el encabezado del agenda. */
private const val RAIL_TODAY_CHIP = "hoy"

/** Copy del día sin bloques — `"Día libre: sin actividades cargadas"` del artboard (`:481`).
 *  Sin botón: el alta es el FAB único de S7, que no vive en la fila. */
private const val RAIL_FREE_DAY_LABEL = "Día libre: sin actividades cargadas"

/** Título del aviso de «hoy sin actividades» — `.hoynotice` del artboard (`:126`). */
private const val RAIL_TODAY_EMPTY_TITLE = "Hoy no tiene actividades"

/** Acción del aviso. La misma palabra que el `.hact` del artboard y el MISMO alta que el FAB. */
private const val RAIL_TODAY_EMPTY_ACTION = "Agregar"

/**
 * Alpha del disco del aviso sobre `secondaryContainer` — el `rgba(0,105,109,.12)` del
 * `.hoynotice .hnico` del artboard (`:131`).
 *
 * Vive aquí y no en `RailGeometry` porque el token pertenece a `Spacing.kt`, fuera de las
 * superficies de esta tarea; el valor ES el del artboard, no un número elegido a ojo.
 */
private const val RAIL_TODAY_EMPTY_DISC_ALPHA = 0.12f

/** Alpha del subtítulo del aviso — el `opacity:.72` del `.hsub` del artboard (`:133`). */
private const val RAIL_TODAY_EMPTY_SUB_ALPHA = 0.72f

/** Clave de lista de la sección sin fecha (el `date` es `null` y una clave nula no vale). */
private const val RAIL_UNDATED_KEY = "sin-fecha"

/**
 * El riel completo: una `LazyColumn` de secciones de día con encabezado PEGAJOSO (S3).
 *
 * `stickyHeader` es `@ExperimentalFoundationApi` en la Foundation que resuelve Compose
 * Multiplatform 1.7.1 (verificado en las fuentes de `androidx.compose.foundation`
 * 1.7.5), de ahí el `@OptIn`. NO se degrada a un `item` normal si algún día deja de
 * compilar: S3 pide encabezados pegajosos y un encabezado que se va con el scroll es otra
 * pantalla.
 *
 * @param sections secciones ya derivadas y ordenadas por la pantalla; el riel no ordena.
 * @param today día de hoy en [zone], para el chip "hoy" y la línea de "ahora".
 * @param nowMinute minuto de día actual, o `null` si la pantalla no lo conoce. Sólo se
 *   pinta en la sección de hoy (`RailDayCanvas` descarta además lo que caiga fuera del eje).
 * @param listState el estado de la lista lo posee la pantalla: es lo que el salto a un día
 *   (T6) mueve con el índice de `railItemIndexOf`.
 * @param onToggleDay pliega o despliega el día tocado. Sólo el chevron lo dispara (S5): el
 *   encabezado NO es un botón, así que el chevron nunca queda anidado en otro `clickable`.
 * @param onSwipeAction T7 (L11) · ÚNICO punto por el que el riel dispara una acción de swipe. Se
 *   pasa como callback y no se lee de un singleton: la superficie sigue siendo función pura de
 *   sus datos. **Sin valor por defecto**: una llamante nueva que lo omita no compila, que es lo
 *   que impide repetir el agujero de `c817fd0` (riel sin capa de gesto y todo en verde).
 * @param focusRequesters T7 (R4) · id → `FocusRequester` que registra cada bloque y cada fila sin
 *   fecha. Es el MISMO mapa que lee la pantalla al cerrar la hoja de detalle: sin este cable el
 *   retorno de foco cae siempre en el contenedor de la lista.
 * @param todayEmptyNotice T10 (S12) · el día de hoy no tiene actividades y el usuario acaba de
 *   pedir «Hoy»: en vez de saltar en silencio, el riel dice que hoy no hay nada y ofrece el alta.
 *   La pantalla ya lo cruza con los bloques reales, así que aquí es un booleano puro.
 * @param onCreateFromEmptyToday T10 (S12) · el MISMO alta que el FAB (S7): la acción del aviso no
 *   abre un segundo camino de alta. **Sin valor por defecto**: una llamante nueva que lo omita no
 *   compila, que es lo que impide dejar el aviso sin salida.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RailTimeline(
    sections: List<RailSection>,
    categories: Map<String, CategoryInfo>,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    zone: TimeZone,
    today: LocalDate,
    nowMinute: Int?,
    listState: LazyListState,
    onOpen: (TimelineRow) -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
    todayEmptyNotice: Boolean,
    onCreateFromEmptyToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        // Sólo abajo: un `contentPadding` superior desplaza el borde en el que se pega el
        // encabezado y el día quedaría flotando un renglón.
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        sections.forEach { section ->
            val date = section.date
            stickyHeader(key = "rail-header-${date ?: RAIL_UNDATED_KEY}") {
                RailDayHeader(
                    section = section,
                    today = today,
                    onToggleDay = onToggleDay,
                )
            }
            when {
                // La sección sin fecha no se pliega: sus filas siempre están.
                date == null -> section.undatedRows.forEach { undatedRow ->
                    item(key = "rail-undated-${undatedRow.actividad.id}") {
                        RailUndatedRow(
                            row = undatedRow,
                            info = categories[undatedRow.actividad.categoryId],
                            zone = zone,
                            memberNames = memberNames,
                            children = children,
                            focusRequesters = focusRequesters,
                            onClick = { onOpen(undatedRow) },
                            // T7 (R2-T2-7) · El gesto se monta en el PUNTO DE RENDER, igual
                            // que en la agenda: la fila declara el hueco y aquí se crea la capa.
                            // El `actividad` ES esta fila, así que la política de acciones
                            // (`can_*`) no puede aplicarse a otra.
                            swipe = { content ->
                                ActivitySwipeBox(
                                    actividad = undatedRow.actividad,
                                    onSwipe = { action ->
                                        onSwipeAction(undatedRow.actividad.id, action)
                                    },
                                    content = content,
                                )
                            },
                        )
                    }
                }
                // Un día plegado no aporta ítem de contenido (`railContentCount` = 0).
                section.isExpanded -> item(key = "rail-day-$date") {
                    // T10 (S12) · El aviso REEMPLAZA la superficie del día libre: los dos dicen
                    // lo mismo y el aviso ya trae la acción, igual que el artboard oculta su
                    // `.emptyday` cuando aparece el `.hoynotice` (`:561`). No cambia la FORMA de
                    // la lista —sigue siendo UN ítem de contenido—, así que el índice de
                    // `railItemIndexOf` no se desalinea.
                    if (date == today && todayEmptyNotice) {
                        RailTodayEmptyNotice(today = today, onCreate = onCreateFromEmptyToday)
                    } else {
                        RailDayBody(
                            section = section,
                            categories = categories,
                            memberNames = memberNames,
                            children = children,
                            zone = zone,
                            today = today,
                            nowMinute = nowMinute,
                            onOpen = onOpen,
                            onSwipeAction = onSwipeAction,
                            focusRequesters = focusRequesters,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Encabezado PEGAJOSO de una sección — S3 ("encabezados de día pegajosos") y S5 (plegar).
 *
 * Anatomía del `.dayheader` del artboard: rótulo compacto (`railDayLabel`), chip "hoy",
 * contador (`margin-left:auto`), marca de vencidas en `error` y chevron a la derecha.
 *
 * DOS NODOS, y el reparto importa (mismo criterio que `EmptyState`): el bloque de texto se
 * fusiona en UN nodo con `heading()`, y el chevron queda FUERA — fusionarlo escondería su
 * acción de plegado y el usuario oiría un encabezado sin control. El contador y la marca
 * de vencidas ya están en PALABRAS, así que la fusión anuncia la información completa
 * (S11: el estado nunca se codifica sólo con color).
 *
 * `background(surface)` es obligatorio: el encabezado se pega encima del lienzo del día y
 * sin fondo opaco el eje horario se leería a través del texto.
 */
@Composable
private fun RailDayHeader(
    section: RailSection,
    today: LocalDate,
    onToggleDay: (LocalDate) -> Unit,
) {
    val date = section.date
    val isToday = date == today
    val titleColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AgendaGeometry.touchTargetLegacy)
                .padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // La sección sin fecha se rotula con la constante que ya existe.
                    text = date?.let(::railDayLabel) ?: NO_START_LABEL,
                    style = MaterialTheme.typography.labelLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isToday) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    RailTodayChip()
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = railCountLabel(section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val overdue = railOverdueCount(section)
                if (overdue > 0) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = railOverdueLabel(overdue),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // El chevron es el ÚNICO elemento interactivo del encabezado y no va anidado en
            // ningún `clickable`: `toggleable` expone el estado (equivalente a
            // `aria-expanded`) y su acción de plegado.
            if (date != null) {
                RailDayToggle(
                    isExpanded = section.isExpanded,
                    label = formatGroupTitle(date, today),
                    onClick = { onToggleDay(date) },
                )
            }
        }
        // Separador del artboard (`box-shadow: 0 1px 0 var(--outline-variant)`).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Chevron de plegado — S5: "Esto me imagino que es para plegar y desplegar el dia."
 *
 * `toggleable` (no `clickable`) porque el control TIENE estado: TalkBack anuncia el valor
 * como encendido/apagado y `stateDescription` lo dice en las palabras del riel
 * ("Desplegado"/"Plegado"). El objetivo táctil es [AgendaGeometry.touchTargetLegacy] (S11).
 *
 * ⚠ DESVIACIÓN DEL ARTBOARD: allí el mismo chevron gira 180° con una transición; aquí se
 * elige el ícono por estado (`ExpandLess`/`ExpandMore`) para no arrastrar una dependencia
 * de animación sólo por el giro. Mismo significado, cero estados intermedios.
 */
@Composable
private fun RailDayToggle(isExpanded: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(AgendaGeometry.touchTargetLegacy)
            .clip(CircleShape)
            .toggleable(value = isExpanded, role = Role.Button, onValueChange = { onClick() })
            .semantics {
                contentDescription = if (isExpanded) "Plegar el día $label" else "Desplegar el día $label"
                stateDescription = if (isExpanded) "Desplegado" else "Plegado"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            // El texto alternativo lo lleva el contenedor: dos nodos dirían lo mismo dos veces.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

/** Chip "hoy" — `primaryContainer`/`onPrimaryContainer` del `.hoy` del artboard (`:76`). */
@Composable
private fun RailTodayChip() {
    Surface(
        shape = ParentalShapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = RAIL_TODAY_CHIP,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = Spacing.xs),
        )
    }
}

/**
 * Cuerpo de un día: canaleta horaria (T2) + lienzo proporcional (T3), y el "día libre"
 * cuando el día no tiene ni un bloque.
 *
 * La altura del contenedor es la MISMA que la del lienzo —
 * `railOffsetDp(axis.endMinute, axis, RailGeometry.DpPerHour).dp` —, no un alto adivinado:
 * un contenedor más bajo recorta el final del eje y uno más alto deja un hueco muerto
 * antes del día siguiente.
 *
 * T10 · AIRE A LA IZQUIERDA Y CANALETA DEL SISTEMA. La canaleta mide
 * [AgendaGeometry.timeColumn] (48dp) y su rótulo va alineado a la derecha con 8dp de aire
 * antes de la grilla, pero la fila arrancaba en x=0: en un teléfono de 384dp los `07:00`
 * quedaban pegados al borde. `Spacing.lg` de sangría alinea la canaleta con el rótulo del
 * encabezado de día (que usa la misma sangría) y deja aire delante de la hora. El lienzo —y
 * con él la línea de hora y el carril 0 de los bloques— sigue arrancando EXACTAMENTE donde
 * termina la canaleta: es el mismo `Row`, sin márgenes intermedios.
 */
@Composable
private fun RailDayBody(
    section: RailSection,
    categories: Map<String, CategoryInfo>,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    zone: TimeZone,
    today: LocalDate,
    nowMinute: Int?,
    onOpen: (TimelineRow) -> Unit,
    onSwipeAction: (String, SwipeAction) -> Unit,
    focusRequesters: MutableMap<String, FocusRequester>,
) {
    val railDay = section.railDay ?: return
    if (railDay.blocks.isEmpty()) {
        RailFreeDay()
        return
    }
    val axis = railDay.axis
    val canvasHeight = railOffsetDp(axis.endMinute, axis, RailGeometry.DpPerHour).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .padding(start = Spacing.lg, end = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        RailHourGutter(axis = axis, dpPerHour = RailGeometry.DpPerHour)
        RailDayCanvas(
            railDay = railDay,
            categories = categories,
            memberNames = memberNames,
            children = children,
            zone = zone,
            onOpen = onOpen,
            // La línea de "ahora" sólo tiene sentido en el día de hoy: en cualquier otro
            // día diría una hora que no es la de ese eje.
            nowMinute = nowMinute.takeIf { section.date == today },
            onSwipeAction = onSwipeAction,
            focusRequesters = focusRequesters,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Día sin actividades: una fila de borde PUNTEADO con el texto del artboard y **sin botón**.
 *
 * El punteado no existe en `Modifier.border`, así que se dibuja con `drawBehind` y un
 * [PathEffect] de guiones derivado de [Spacing] (`xs` de guion, `xxs` de hueco), con el
 * radio de [ParentalShapes.medium] — ni un dp crudo ni un color fuera del tema. Se resta
 * medio trazo al rectángulo porque el trazo se centra en el borde y la mitad quedaría
 * recortada por el clip.
 */
@Composable
private fun RailFreeDay() {
    val shape = ParentalShapes.medium
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .drawBehind {
                val stroke = Spacing.hairline.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(shape.topStart.toPx(size, this)),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(Spacing.xs.toPx(), Spacing.xxs.toPx()),
                        ),
                    ),
                )
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = RAIL_FREE_DAY_LABEL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Aviso de «hoy no tiene actividades» — T10 (S12), el `.hoynotice` del artboard (`:126`).
 *
 * POR QUÉ EXISTE: con S15 el riel abre anclado, así que tocar «Hoy» muchas veces no cambia
 * nada visible; si el día está vacío, el control parecía no hacer nada. Este aviso es la
 * respuesta: dice que hoy no hay actividades y ofrece el alta en el mismo lugar donde el
 * usuario miró.
 *
 * ANUNCIO (S11): el contenedor es una REGIÓN VIVA (`liveRegion = Polite`), así que TalkBack
 * lee el aviso cuando aparece sin robarle el foco a nada. NO se usa `mergeDescendants`: el
 * botón de alta tiene que seguir siendo un nodo con acción propia — fusionarlo escondería la
 * única salida que el aviso ofrece.
 *
 * La pantalla lo monta sólo cuando hoy no tiene bloques y lo retira en cuanto el usuario elige
 * otro día o la agenda de hoy deja de estar vacía, así que no hay estado "pegado".
 */
@Composable
private fun RailTodayEmptyNotice(today: LocalDate, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm)
            .clip(ParentalShapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.xl)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = RAIL_TODAY_EMPTY_DISC_ALPHA))
                // Decorativo: el título de al lado ya dice el mensaje.
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Spacing.lg),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = RAIL_TODAY_EMPTY_TITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDayTitle(today).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                    alpha = RAIL_TODAY_EMPTY_SUB_ALPHA,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Button(
            onClick = onCreate,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
            modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
        ) {
            Text(
                text = RAIL_TODAY_EMPTY_ACTION,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Una fila de la sección "Sin fecha de inicio" — el `.urow` del artboard (`:146`).
 *
 * ⚠ FILA PROPIA Y NO `AgendaTimelineRow`: esa firma pide `resolve`, `onSelectActividad`,
 * `onConfirm` e `isConfirming` —que el riel no tiene— y "Sin fecha" TAMPOCO tiene un `date` que
 * pasarle; cambiar su firma para acomodar este caso tocaría las dos superficies que todavía la
 * montan (`AgendaList` y `WeekAgenda`), que se retiran en T8. Es la fila mínima del arteboard:
 * riel de categoría, título y estado en palabras.
 *
 * T7 · Lo que la fila SÍ gana es lo que le tocaba por derecho propio: su hueco `swipe` y su
 * registro de foco, con la MISMA forma que `ActivityCard` (`remember` + `SideEffect` + hueco sin
 * default), no una paralela. Lo demás sigue igual.
 * @param swipe T7 · Hueco de gesto (R2-T2-7), con el tipo EXACTO de ranura que declaran
 *   `ActivityCard` y `AgendaTimelineRow` y **sin valor por defecto**: la omisión tiene que ser un
 *   error de compilación, no una superficie sin gesto que compila. El envoltorio
 *   (`ActivitySwipeBox`) lo monta `RailTimeline`, en el punto de render.
 * @param focusRequesters T7 · destino de foco por id; lo registra el `SideEffect`, igual que la
 *   fila de la agenda y como la tarjeta, para que el retorno de foco desde la hoja encuentre esta
 *   fila y no el contenedor de la lista.
 */
@Composable
private fun RailUndatedRow(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    focusRequesters: MutableMap<String, FocusRequester>,
    onClick: () -> Unit,
    swipe: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // R4 · `SideEffect` + no-leer-en-composición ⇒ escribir el mapa no recompone a nadie.
    SideEffect { focusRequesters[row.actividad.id] = focusRequester }

    // El `focusRequester` vive FUERA del hueco de gesto: el hueco desplaza el contenido y los
    // `ParentDataModifier` no lo atraviesan (R2-T2-7). Mismo reparto que `ActivityCard`.
    Box(modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)) {
        swipe {
            RailUndatedRowBody(
                row = row,
                info = info,
                zone = zone,
                memberNames = memberNames,
                children = children,
                onClick = onClick,
            )
        }
    }
}

/** Cuerpo de la fila sin fecha — lo que el huésped dibuja dentro del hueco de gesto. */
@Composable
private fun RailUndatedRowBody(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    memberNames: Map<String, String>,
    children: List<VinculoChild>,
    onClick: () -> Unit,
) {
    val railColor = categoryTagTriple(info)?.dot ?: MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .heightIn(min = AgendaGeometry.touchTargetLegacy)
            .clip(ParentalShapes.medium)
            .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, ParentalShapes.medium)
            // El riel de categoría se DIBUJA en vez de maquetarse: `fillMaxHeight` dentro de
            // una fila sin alto fijo (la de `LazyColumn`) no tiene contra qué resolverse, y
            // `IntrinsicSize.Min` ataría la altura del riel al texto, no al objetivo táctil.
            .drawBehind {
                drawRect(
                    color = railColor,
                    size = Size(RailGeometry.CategoryRail.toPx(), size.height),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                // La etiqueta del calendario ya compone fecha ("Sin fecha de inicio" cuando
                // no hay inicio), título, categoría, tipo y estado: no se reescribe aquí.
                contentDescription = activitySemanticLabel(row, info, zone, memberNames, children)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.actividad.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm),
        )
        StatusTag(status = row.actividad.status, modifier = Modifier.padding(end = Spacing.sm))
    }
}

/**
 * Contador del encabezado: `"sin actividades"`, `"1 actividad"`, `"2 actividades"`.
 *
 * Es el mismo copy del `dcount` del artboard (`:431`) y el mismo criterio de plural del
 * `dayCellSemanticLabel` del calendario.
 */
private fun railCountLabel(section: RailSection): String {
    val count = section.railDay?.blocks?.size ?: section.undatedRows.size
    return when (count) {
        0 -> "sin actividades"
        1 -> "1 actividad"
        else -> "$count actividades"
    }
}

/** Vencidas del día. El estado llega en minúsculas del backend y con una sola clave. */
private fun railOverdueCount(section: RailSection): Int =
    section.railDay?.blocks?.count { block -> block.row.actividad.status.uppercase() == "OVERDUE" } ?: 0

/** Marca de vencidas en PALABRAS: el color de `error` no es la única señal (S11). */
private fun railOverdueLabel(overdue: Int): String =
    if (overdue == 1) "· 1 vencida" else "· $overdue vencidas"
