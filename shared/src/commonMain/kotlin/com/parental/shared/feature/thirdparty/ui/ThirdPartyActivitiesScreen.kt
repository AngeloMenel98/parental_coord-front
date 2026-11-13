package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.BottomNavItem
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone

/**
 * Actividades del terciario — artboard `03-permiso-explicito`, segunda pantalla.
 *
 * La lista es la de los progenitores (filas de agenda, mismo riel, mismo punto de
 * categoría) pero **sin las tres cosas que el terciario no puede hacer**: no hay botón
 * de alta, no hay swipe y no hay tabs por tipo de actividad. En su lugar, un chip por
 * estado accionable con su conteo, y la única acción posible al final de cada fila.
 *
 * Un chip vacío es un estado vacío explicado, no una lista en blanco: "Nada en vencidas"
 * dice qué miró y por qué no hay nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThirdPartyActivitiesScreen(
    state: ThirdPartyUiState,
    onHome: () -> Unit,
    onSelectFilter: (ThirdPartyFilter) -> Unit,
    onOpenDetail: (String) -> Unit,
    onFinalize: (String) -> Unit,
    onMessageShown: () -> Unit,
    unreadCount: Int,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Actividades",
                onLogout = onLogout,
                unreadCount = unreadCount,
                onOpenNotifications = onOpenNotifications,
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedRoute = BottomNavItem.Actividades.route,
                onNavigate = { route ->
                    if (route == BottomNavItem.Home.route) onHome()
                },
                items = THIRD_PARTY_NAV_ITEMS,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ThirdPartyFiltersRow(
                selected = state.filter,
                counts = state.counts,
                onSelect = onSelectFilter,
            )
            val resolve: (String) -> CategoryInfo? = { id -> state.categories[id] }
            when {
                state.isLoading -> ContentListSkeleton(
                    contentDescription = "Cargando tus actividades",
                    modifier = Modifier.fillMaxSize(),
                )

                state.error != null -> EmptyContentState(
                    title = "No pudimos cargar tus actividades",
                    message = state.error,
                    modifier = Modifier.fillMaxSize(),
                )

                state.filtered.isEmpty() -> EmptyContentState(
                    title = "Nada en ${state.filter.label.lowercase()}",
                    message = "Cuando tengas una tarea en este estado, aparece acá.",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.sm,
                        end = Spacing.sm,
                        bottom = Spacing.xl,
                    ),
                ) {
                    items(items = state.filtered, key = { it.id }) { actividad ->
                        ThirdPartyTaskRow(
                            actividad = actividad,
                            zone = zone,
                            resolve = resolve,
                            action = thirdPartyTaskAction(actividad),
                            showDateColumn = true,
                            onClick = { onOpenDetail(actividad.id) },
                            onFinalize = { onFinalize(actividad.id) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            thickness = Spacing.hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Los cuatro chips, con el conteo de cada uno tomado de la lista cargada (el mismo patrón
 * `tabCounts` de la pantalla de los progenitores). Scroll horizontal y 48dp de alto: con
 * cuatro chips no entran cómodos en un teléfono angosto y un chip recortado no se toca.
 */
@Composable
private fun ThirdPartyFiltersRow(
    selected: ThirdPartyFilter,
    counts: Map<ThirdPartyFilter, Int>,
    onSelect: (ThirdPartyFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThirdPartyFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = (counts[filter] ?: 0).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}
