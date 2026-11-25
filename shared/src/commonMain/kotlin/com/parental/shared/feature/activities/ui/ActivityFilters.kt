package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.bonds.domain.model.VinculoChild

/*
 * Fila de filtros — T3 de `odd/tasks/activities-cards-ui.md` (S5).
 *
 * SUSTITUYE a `TimelineControls` (CalendarChrome.kt), que pintaba DOS rótulos ("Vista" y
 * "Filtrar") y un control segmentado. El arteboard no tiene ninguno de los tres: los
 * filtros son una sola línea de chips con su CONTADOR, y el cambio de vista es un ícono
 * de la barra superior. Los rótulos se fueron con ellos: occupied ≈ 64dp verticales que
 * la lista de tarjetas necesita.
 *
 * El contador sale de la lista YA CARGADA (`tabCounts`), no de una segunda consulta: el
 * chip dice cuántos elementos habría, no cuántos hay en el servidor.
 *
 * La fila scrollea en horizontal (`horizontalScroll`), como la del arteboard: con fuente
 * grande o "Obligaciones" al final, tres chips no caben en 360dp y recortarlos sería
 * peor que desplazarlos.
 */

@Composable
internal fun ActivityFiltersRow(
    selectedTab: ActivityTab,
    onSelectTab: (ActivityTab) -> Unit,
    counts: Map<ActivityTab, Int>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = Spacing.maxContentWidth)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityTab.entries.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = (counts[tab] ?: 0).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
            )
        }
    }
}

/**
 * S11 · Fila de filtro de hijos.
 *
 * Aparece SOLO cuando hay más de un hijo. "Todos" limpia la selección; cada hijo es un
 * chip multi-select. Los contadores respetan la pestaña ya aplicada.
 */
@Composable
internal fun ChildrenFiltersRow(
    children: List<VinculoChild>,
    selectedChildren: Set<String>,
    counts: Map<String, Int>,
    onSelectAll: () -> Unit,
    onToggleChild: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = Spacing.maxContentWidth)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedChildren.isEmpty(),
            onClick = onSelectAll,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Todos",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = (counts[CHILD_FILTER_ALL] ?: 0).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
        )

        children.forEach { child ->
            val name = listOfNotNull(child.firstName, child.lastName)
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
                ?: child.id
            FilterChip(
                selected = child.id in selectedChildren,
                onClick = { onToggleChild(child.id) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = (counts[child.id] ?: 0).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
            )
        }
    }
}

/*
 * Dropdowns del mapa de densidad — T3 de `odd/tasks/activity-card-stack.md` (S4,
 * artboard `02-personas-al-frente`), reemplazan a las dos filas de chips dentro del
 * panel del calendario.
 *
 * El contrato con el ViewModel es EL MISMO que el de las filas de chips: misma
 * selección (`selectedTab` / `selectedChildren`), mismos callbacks — lo único que
 * cambia es el envase. Así el cambio es de PRESENTACIÓN pura y el estado no sabe
 * que hubo un rediseño.
 *
 * Los dos dropdowns comparten la misma fila (Decisión humana del 2026-11-25):
 * "Hijo ▾ | Tipo ▾", cada uno con su menú de checkboxes teal; "Todos" es
 * EXCLUYENTE (S4: elegir un hijo despeja Todos, desmarcar la última vuelve a
 * Todos — la lógica ya vive en el VM con `toggleChildFilter`, que no puede dejar
 * el conjunto vacío sin volver al default, y el chip "Todos" (onSelectAll) la
 * aplica). El label del chip muestra las selecciones y el conteo, con un punto
 * teal y sin badge cuando no hay nada elegido ("Todos" por defecto, sin mostralo).
 */

/** Label del dropdown de hijos: "Hijo" cuando no hay nada, nombres si los hay. */
internal fun childrenDropdownLabel(
    children: List<VinculoChild>,
    selectedChildren: Set<String>,
): String {
    if (selectedChildren.isEmpty()) return "Hijo"
    val names = children.filter { it.id in selectedChildren }.map { child ->
        listOfNotNull(child.firstName, child.lastName).joinToString(" ").trim()
            .ifEmpty { child.id }
    }
    return names.joinToString(", ")
}

@Composable
internal fun ChildrenDropdown(
    children: List<VinculoChild>,
    selectedChildren: Set<String>,
    counts: Map<String, Int>,
    onSelectAll: () -> Unit,
    onToggleChild: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedChildren.isNotEmpty(),
            onClick = { expanded = !expanded },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = childrenDropdownLabel(children, selectedChildren),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (selectedChildren.size > 1) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = selectedChildren.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.height(Spacing.md),
                    )
                }
            },
            modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckMark(checked = selectedChildren.isEmpty())
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(text = "Todos", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onClick = {
                    onSelectAll()
                    expanded = false
                },
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            children.forEach { child ->
                val name = listOfNotNull(child.firstName, child.lastName)
                    .joinToString(" ")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: child.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckMark(checked = child.id in selectedChildren)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(text = name, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = (counts[child.id] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                    onClick = { onToggleChild(child.id) },
                )
            }
        }
    }
}

/**
 * Caja de check del artboard: 18dp con borde `outline`; relleno `primary` con el tick
 * blanco cuando está activa. `.boxw` del artboard con los tokens del sistema.
 */
@Composable
private fun CheckMark(checked: Boolean) {
    val color = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color,
        border = BorderStroke(1.5.dp, border),
        modifier = Modifier.width(18.dp).height(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.width(12.dp).height(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * Dropdown de tipo (Todas/Eventos/Obligaciones). En el mapa, "Todas" es el valor sin
 * filtro — el mismito `ActivityTab` de la lista, mismo callback; single-select, con
 * check en la opción elegida.
 */
@Composable
internal fun ActivityTypeDropdown(
    selectedTab: ActivityTab,
    onSelectTab: (ActivityTab) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedTab != ActivityTab.TODOS,
            onClick = { expanded = !expanded },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tipo: ${selectedTab.label}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.height(Spacing.md),
                    )
                }
            },
            modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ActivityTab.entries.forEach { tab ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckMark(checked = selectedTab == tab)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    },
                    onClick = {
                        onSelectTab(tab)
                        expanded = false
                    },
                )
            }
        }
    }
}
