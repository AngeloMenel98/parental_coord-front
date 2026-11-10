package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.MonthMapGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import kotlinx.datetime.LocalDate

/*
 * Mapa de densidad mensual — T5 de `odd/tasks/activity-rail.md` (S4, S11).
 * **Archivo nuevo.** Traducción del panel `.density` del artboard
 * `design/activity-calendar-ui/03-riel-del-tiempo.html` (`:190`). No se copia HTML.
 *
 * S4: "codifica cantidad con opacidad y número —y que se muestra u oculta desde el pill del
 * encabezado del calendario—". Por eso el ÚNICO control de plegado es el pill del encabezado
 * (`[data-toggle]` equivalente: `toggleable` + `stateDescription`), y vive en ESTE encabezado,
 * no en una barra inferior.
 *
 * NO HACE: montarse en `ActivitiesScreen` (T6), borrar `CalendarJumpSheet` (T8), ni derivar
 * densidad — eso ya llega resuelto en `Map<LocalDate, MonthDensity>` desde
 * `MonthDensity.kt`, que es puro y probado.
 *
 * CERO COLORES NUEVOS: `primary` (tinte escalado y marcador de hoy), `error` (vencida),
 * `onSurface` (número), `onSurfaceVariant` (rótulos) y `surface` (anillo de los marcadores).
 * CERO dp crudo: `Spacing` y `MonthMapGeometry`.
 */

/** Rótulo del panel. En minúscula como el resto del copy del riel ("densidad"). */
private const val DENSITY_CAPTION = "Densidad"

/** Copy del pill cuando el mapa está ABIERTO — el `toggleLabel` del artboard (`:196`). */
private const val DENSITY_COLLAPSE_LABEL = "Ocultar"

/** Copy del pill cuando el mapa está CERRADO — el `setDensity(false)` del artboard (`:535`). */
private const val DENSITY_EXPAND_LABEL = "Ver el mes"

/** Extremos de la leyenda: "Menos → Más" (`.legend`, `:203`). */
private const val DENSITY_LEGEND_LOW = "Menos"
private const val DENSITY_LEGEND_HIGH = "Más"

/** Marca de vencida de la leyenda. La misma palabra que usa el rótulo de la celda. */
private const val DENSITY_LEGEND_OVERDUE = "vencida"

/** Los cuatro peldaños VISIBLES de la escala. `EMPTY` no es un peldaño: es la ausencia de tinte. */
private val DENSITY_LEGEND_LEVELS = listOf(
    DensityLevel.LOW,
    DensityLevel.MEDIUM,
    DensityLevel.HIGH,
    DensityLevel.FULL,
)

/**
 * Mapa de densidad del mes — S4.
 *
 * Dos estados, y el plegado **compone condicionalmente**: cerrado, la fila de días de la
 * semana, la grilla y la leyenda NO existen en el árbol (`if (expanded)`), no están a alpha 0.
 * Un `alpha(0f)` dejaría los botones de la grilla en el recorrido de TalkBack, que es
 * exactamente el defecto que el `[hidden]` del artboard evita. El estado del pill lo anuncia
 * `stateDescription`.
 *
 * @param density salida de `buildMonthDensity` para ESTE mes y la zona del usuario. Un día
 *   ausente es un día sin actividades, y su celda sigue siendo un botón: elegirla es válido
 *   (el riel salta al día con actividades más cercano).
 * @param selected día seleccionado; puede caer fuera del mes (no se marca nada).
 * @param today día de hoy en la zona del usuario; puede caer fuera del mes mostrado.
 * @param expanded estado del pill. Lo posee la pantalla (T6), igual que el `rememberSaveable`
 *   del resto de la vista.
 */
@Composable
internal fun MonthDensityMap(
    year: Int,
    monthNumber: Int,
    density: Map<LocalDate, MonthDensity>,
    selected: LocalDate,
    today: LocalDate,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Todo el contenido va con el mismo margen lateral del arteboard (`.density{padding:0
        // 12px}`), para que rótulos de día, celdas y leyenda compartan columnas. El separador
        // queda FUERA de ese margen: en el arteboard el `border-bottom` cruza la sección
        // entera.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            DensityHeader(
                year = year,
                monthNumber = monthNumber,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
            )
            if (expanded) {
                DensityWeekdayCaps()
                DensityGrid(
                    year = year,
                    monthNumber = monthNumber,
                    density = density,
                    selected = selected,
                    today = today,
                    onSelectDay = onSelectDay,
                )
                DensityLegend()
            }
        }
        // Separador del artboard (`border-bottom:1px solid var(--outline-variant)`): cierra el
        // panel en LOS DOS estados, porque el encabezado sigue estando.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Encabezado del panel: flechas del mes, rótulo, "Densidad" y el pill de plegado.
 *
 * ⚠ ADAPTACIÓN AL ANCHO (desviación del artboard, que pone el rótulo y el `sub` en la MISMA
 * línea): aquí el rótulo del mes y la leyenda "Densidad" van apilados dentro de la columna con
 * peso. En un móvil de 320dp, dos flechas de 48dp + el pill dejan ~115dp para el mes, y
 * "Mayo De 2026" con el `sub` al lado no cabe sin cortar el mes — que es el dato, no el
 * adorno. El orden de lectura y la jerarquía no cambian.
 *
 * ⚠ TIPOGRAFÍA: el artboard usa serif de 16px para el mes. La escala del proyecto no tiene
 * ningún rol serif por debajo de `headlineSmall` (24sp, el que usa la hoja de calendario), y
 * `Type.kt` está fuera de las superficies de esta tarea: se usa `titleMedium` (16sp), que
 * reproduce el TAMAÑO del artboard y respeta el token existente en vez de inventar un estilo.
 */
@Composable
private fun DensityHeader(
    year: Int,
    monthNumber: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevMonth,
            modifier = Modifier.size(AgendaGeometry.touchTargetLegacy),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Mes anterior",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // El formateador que ya usa la hoja de calendario: una sola forma de escribir
                // el mes en el producto.
                text = formatMonthYear(LocalDate(year, monthNumber, 1)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DENSITY_CAPTION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onNextMonth,
            modifier = Modifier.size(AgendaGeometry.touchTargetLegacy),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Mes siguiente",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.xs))
        DensityToggle(expanded = expanded, onToggleExpanded = onToggleExpanded)
    }
}

/**
 * Pill "Ocultar" / "Ver el mes" — el ÚNICO control de mostrar/ocultar (S4).
 *
 * `toggleable` (no `clickable`) porque el control TIENE estado: TalkBack anuncia el valor y
 * `stateDescription` lo dice en palabras ("Visible"/"Oculto"), igual que el chevron de día de
 * `RailTimeline.kt`. El ícono cambia de sentido por estado en vez de girar: no se arrastra una
 * dependencia de animación por un giro.
 *
 * `heightIn(min = touchTargetLegacy)` para que el pill de 28dp del artboard siga siendo un
 * objetivo táctil de S11 — el piso manda sobre la medida del dibujo.
 */
@Composable
private fun DensityToggle(expanded: Boolean, onToggleExpanded: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .heightIn(min = AgendaGeometry.touchTargetLegacy)
            .clip(CircleShape)
            .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .toggleable(value = expanded, role = Role.Button, onValueChange = { onToggleExpanded() })
            .semantics {
                contentDescription = if (expanded) {
                    "Ocultar el mapa de densidad"
                } else {
                    "Ver el mes en el mapa de densidad"
                }
                stateDescription = if (expanded) "Visible" else "Oculto"
            }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = if (expanded) DENSITY_COLLAPSE_LABEL else DENSITY_EXPAND_LABEL,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            // El texto alternativo lo lleva el contenedor: dos nodos dirían lo mismo dos veces.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Spacing.lg),
        )
    }
}

/**
 * Fila de rótulos LU…DO.
 *
 * Sale de [ES_WEEKDAYS_CAPS] —la tabla que ya usan la agenda y la hoja de calendario—, así que
 * no hay una segunda lista de días que se pueda desincronizar. Es DECORATIVA
 * (`clearAndSetSemantics { }`): los días los anuncian las celdas, y sin esto TalkBack leería
 * "LU, MA, MI…" como siete nodos sueltos antes de la grilla (el `aria-hidden` del artboard).
 *
 * Mismo `spacedBy` y mismos pesos que la grilla: sin eso los centros de los rótulos caen
 * corridos respecto de los de las celdas.
 */
@Composable
private fun DensityWeekdayCaps() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
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
}

/**
 * Grilla del mes: 7 columnas, lunes primero, de [buildMonthGrid].
 *
 * Las celdas de OTRO mes son relleno, no destinos: se pintan como un `Spacer` mudo en vez de
 * un botón (misma decisión que `CalendarJumpSheet`), porque el 31 de mayo dentro de la semana
 * del 1 de junio no es parte del mes que el usuario está mirando. Un día SIN actividades, en
 * cambio, SÍ es un botón: está en el mes y elegirlo es válido.
 *
 * Los `remember` dependen de año y mes: navegar de mes no reconstruye la grilla si el mismo mes
 * vuelve a componerse.
 */
@Composable
private fun DensityGrid(
    year: Int,
    monthNumber: Int,
    density: Map<LocalDate, MonthDensity>,
    selected: LocalDate,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val grid = remember(year, monthNumber) { buildMonthGrid(year, monthNumber) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        grid.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                week.forEach { date ->
                    if (date.year == year && date.monthNumber == monthNumber) {
                        DensityDayCell(
                            date = date,
                            density = density[date],
                            selected = selected,
                            today = today,
                            onSelectDay = onSelectDay,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // Sin semántica y sin clic: relleno de la semana, no un día del mes.
                        Spacer(modifier = Modifier.weight(1f).height(MonthMapGeometry.CellHeight))
                    }
                }
            }
        }
    }
}

/**
 * Celda de un día — S4 ("cantidad con opacidad y número") y S11.
 *
 * El NÚMERO se pinta siempre (`onSurface`), y la CANTIDAD se codifica además con el tinte de
 * `primary` en cuatro peldaños de [MonthMapGeometry]. Las dos señales conviven a propósito: el
 * número dice cuánto, el tinte lo hace comparable de un vistazo, y ninguna depende de la otra.
 *
 * Diadas del arteboard, sin color nuevo:
 *  · HOY → punto `primary` de 5dp arriba a la derecha, con anillo `surface`.
 *  · VENCIDA → punto `error` de 5dp abajo al centro, con anillo `surface` (se lee sobre
 *    cualquier peldaño; el estado también viaja en PALABRAS por `densityCellSemanticLabel`).
 *  · SELECCIONADO → borde interior de 2dp `primary`.
 *
 * UN solo nodo de semántica y UNA acción (`selectable`): la celda no contiene nada pulsable.
 */
@Composable
private fun DensityDayCell(
    date: LocalDate,
    density: MonthDensity?,
    selected: LocalDate,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = densityLevel(density?.count ?: 0)
    val isSelected = date == selected
    val isToday = date == today
    // El borde se aplica DESPUÉS del clip y del fondo: `Modifier.border` traza por dentro del
    // contorno, así que el "inset 2px" del artboard sale sin desplazar el contenido.
    val selectionBorder = if (isSelected) {
        Modifier.border(
            MonthMapGeometry.SelectedBorderWidth,
            MaterialTheme.colorScheme.primary,
            ParentalShapes.small,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .height(MonthMapGeometry.CellHeight)
            .clip(ParentalShapes.small)
            .then(densityTint(level))
            .then(selectionBorder)
            .selectable(selected = isSelected, role = Role.Button, onClick = { onSelectDay(date) })
            // `mergeDescendants`: el número y los puntos son parte de la MISMA celda; el
            // `contentDescription` sustituye al texto visible y es la única frase que se oye.
            .semantics(mergeDescendants = true) {
                contentDescription = densityCellSemanticLabel(date, today, density)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (isToday) {
            DensityMarker(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
            )
        }
        if (density?.hasOverdue == true) {
            DensityMarker(
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs),
            )
        }
    }
}

/**
 * Tinte de fondo de la celda: `primary` al alpha del peldaño, o NADA cuando el día está vacío.
 *
 * `EMPTY` no lleva tinte a propósito: la escala mide "cuánto", y un relleno base haría que un
 * día sin actividades se leyera como el primer peldaño de la escala (el arteboard lo resuelve
 * con un `rgba(onSurface,.04)`, que aquí sería un quinto relleno de otro rol).
 */
@Composable
private fun densityTint(level: DensityLevel): Modifier {
    val alpha = when (level) {
        DensityLevel.EMPTY -> return Modifier
        DensityLevel.LOW -> MonthMapGeometry.LevelLowAlpha
        DensityLevel.MEDIUM -> MonthMapGeometry.LevelMediumAlpha
        DensityLevel.HIGH -> MonthMapGeometry.LevelHighAlpha
        DensityLevel.FULL -> MonthMapGeometry.LevelFullAlpha
    }
    return Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
}

/**
 * Marcador circular con anillo `surface` — el `.hcell.today::after` / `.alert` del arteboard.
 *
 * El anillo NO es decoración: `primary` sobre el tinte `FULL` de `primary` queda en 3.10:1 y
 * `error` sobre ese mismo tinte en 3.09:1 (claro). Sin el halo, los dos marcadores del peldaño
 * más oscuro se confunden con el fondo. Es decorativo para el lector de pantalla: lo que
 * anuncian estas señales ya está en el rótulo de la celda.
 */
@Composable
private fun DensityMarker(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MonthMapGeometry.MarkerRingSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MonthMapGeometry.MarkerSize)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * Leyenda: "Menos → Más" con los cuatro peldaños, y la marca de vencida.
 *
 * UN nodo de semántica para toda la escala (`mergeDescendants` + `contentDescription`): seis
 * elementos sueltos ("Menos", cuatro cuadraditos mudos, "Más", "vencida") se oyen como ruido
 * en el barrido. La frase dice la escala completa en el orden en que se ve.
 */
@Composable
private fun DensityLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Escala de densidad: $DENSITY_LEGEND_LOW a $DENSITY_LEGEND_HIGH, " +
                        "con marca de actividad $DENSITY_LEGEND_OVERDUE"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = DENSITY_LEGEND_LOW,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        DENSITY_LEGEND_LEVELS.forEach { level ->
            Box(
                modifier = Modifier
                    .size(MonthMapGeometry.SwatchSize)
                    .clip(ParentalShapes.extraSmall)
                    .then(densityTint(level)),
            )
        }
        Text(
            text = DENSITY_LEGEND_HIGH,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .size(MonthMapGeometry.OverdueMarkerSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Text(
            text = DENSITY_LEGEND_OVERDUE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
