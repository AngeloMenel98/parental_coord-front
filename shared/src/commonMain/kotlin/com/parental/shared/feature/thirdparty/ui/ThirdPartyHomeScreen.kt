package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.BottomNavItem
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.ui.currentDateIn
import com.parental.shared.feature.activities.ui.formatAgendaDate
import com.parental.shared.feature.activities.ui.formatLongDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Inicio del terciario temporal — artboard `03-permiso-explicito`, primera pantalla.
 *
 * La jerarquía es la del artboard: **primero el permiso y su vigencia**, después las
 * tareas. Es lo contrario del Inicio de los progenitores (saludo → Hoy → métricas →
 * anillo), y a propósito: lo que un visitante temporal necesita saber antes que nada es
 * cuánto dura su acceso y quién se lo dio.
 *
 * Lo que esta pantalla NO monta, por S5: anillo de cumplimiento, franja de métricas con
 * saldo, sección de hijos y lista de miembros. Lo que sí reutiliza: la barra superior con
 * la campana, la barra de navegación (con dos ítems), la fila de agenda y los tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThirdPartyHomeScreen(
    state: ThirdPartyUiState,
    onOpenActivities: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onFinalize: (String) -> Unit,
    onOpenPermit: () -> Unit,
    onAcknowledgePermit: () -> Unit,
    onMessageShown: () -> Unit,
    unreadCount: Int,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { currentDateIn(zone) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Inicio",
                onLogout = onLogout,
                unreadCount = unreadCount,
                onOpenNotifications = onOpenNotifications,
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedRoute = BottomNavItem.Home.route,
                onNavigate = { route ->
                    if (route == BottomNavItem.Actividades.route) onOpenActivities()
                },
                items = THIRD_PARTY_NAV_ITEMS,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> ContentListSkeleton(
                contentDescription = "Cargando tu inicio",
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.error != null -> EmptyContentState(
                title = "No pudimos cargar tu inicio",
                message = state.error,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> HomeFeed(
                state = state,
                today = today,
                zone = zone,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenDetail = onOpenDetail,
                onFinalize = onFinalize,
                onOpenPermit = onOpenPermit,
                onOpenActivities = onOpenActivities,
            )
        }
    }

    if (state.showPermitDialog) {
        PermitOnboardingDialog(permit = state.permit, onDismiss = onAcknowledgePermit)
    }
}

@Composable
private fun HomeFeed(
    state: ThirdPartyUiState,
    today: LocalDate,
    zone: TimeZone,
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit,
    onFinalize: (String) -> Unit,
    onOpenPermit: () -> Unit,
    onOpenActivities: () -> Unit,
) {
    val resolve: (String) -> CategoryInfo? = { id -> state.categories[id] }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ThirdPartyGreeting(userName = state.userName, today = today) }

        state.permit?.let { permit ->
            item { ThirdPartyPermitCard(permit = permit, onOpenPermit = onOpenPermit) }
        }

        item {
            ThirdPartyTasksCard(
                tasks = state.homeTasks,
                activeCount = state.activeCount,
                zone = zone,
                resolve = resolve,
                onOpenDetail = onOpenDetail,
                onFinalize = onFinalize,
                onOpenActivities = onOpenActivities,
            )
        }

        state.permit?.let { permit ->
            item { ThirdPartyPermitEntry(permit = permit, onOpenPermit = onOpenPermit) }
        }
    }
}

@Composable
private fun ThirdPartyGreeting(userName: String, today: LocalDate) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = formatLongDate(today),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Hola, $userName",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * La tarjeta de permiso: quién autorizó, con qué relación y hasta cuándo.
 *
 * La barra de vigencia usa el par `primary` sobre `surfaceVariant` (el track que el
 * sistema define), y **siempre va acompañada de la frase en palabras** ("39 días" /
 * "Vence hoy" / "Venció"): una barra sola no dice nada a quien no puede verla.
 */
@Composable
private fun ThirdPartyPermitCard(permit: ThirdPartyPermitUi, onOpenPermit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = permit.authorizedByName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    Text(
                        text = "Autorizado por ${permit.authorizedByName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    permit.relationship?.let { relationship ->
                        Text(
                            text = relationship,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PermitSeal()
            }

            val endDate = permit.endDate
            val daysLabel = thirdPartyDaysLeftLabel(permit.daysLeft)
            if (endDate != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Vence el ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatAgendaDate(endDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (daysLabel != null) {
                        Text(
                            text = daysLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PermitInfoButton(onOpenPermit)
                }
                PermitProgressBar(permit.progress)
            } else {
                // Sin fecha del servidor no hay vigencia que mostrar: se dice el estado y
                // se deja el icono para leer el detalle del permiso. Nada se inventa.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Acceso temporal vigente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    PermitInfoButton(onOpenPermit)
                }
            }
        }
    }
}

@Composable
private fun PermitSeal() {
    Text(
        text = "Acceso temporal",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(ParentalShapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

@Composable
private fun PermitInfoButton(onOpenPermit: () -> Unit) {
    IconButton(onClick = onOpenPermit) {
        Icon(
            imageVector = Icons.Default.Info,
            // No es decorativo: abre el popup que explica el permiso.
            contentDescription = "Ver mi permiso",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermitProgressBar(progress: Float?) {
    val fraction = progress?.coerceIn(0f, 1f) ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ThirdPartyTasksCard(
    tasks: List<Actividad>,
    activeCount: Int,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    onOpenDetail: (String) -> Unit,
    onFinalize: (String) -> Unit,
    onOpenActivities: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tus tareas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = activeCountLabel(activeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.isEmpty()) {
                Text(
                    text = "No tenés tareas asignadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
            tasks.forEachIndexed { index, actividad ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        thickness = Spacing.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                ThirdPartyTaskRow(
                    actividad = actividad,
                    zone = zone,
                    resolve = resolve,
                    action = thirdPartyTaskAction(actividad),
                    showDateColumn = false,
                    onClick = { onOpenDetail(actividad.id) },
                    onFinalize = { onFinalize(actividad.id) },
                )
            }
            if (tasks.isNotEmpty()) {
                Text(
                    text = "Ver todas en Actividades",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .clickableRow(onOpenActivities),
                )
            }
        }
    }
}

/**
 * El acceso repetido en una línea al pie del Inicio, con el icono de información. Es el
 * segundo abridor del popup (el primero es el de la tarjeta), y el que el humano pidió en
 * el comentario `ivaso2yy` del canvas.
 */
@Composable
private fun ThirdPartyPermitEntry(permit: ThirdPartyPermitUi, onOpenPermit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tu permiso: finalizar y adjuntar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = permit.endDate?.let { "Vence el ${formatAgendaDate(it)}" }
                        ?: "Acceso temporal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PermitInfoButton(onOpenPermit)
        }
    }
}

/** "3 activas" / "1 activa" / "Sin activas". Las vencidas no cuentan (ver la política). */
private fun activeCountLabel(count: Int): String = when (count) {
    0 -> "Sin activas"
    1 -> "1 activa"
    else -> "$count activas"
}

/** Un `clickable` con forma y sin relleno extra: el target lo da la fila del pie. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this
        .clip(ParentalShapes.medium)
        .clickable(onClick = onClick)
