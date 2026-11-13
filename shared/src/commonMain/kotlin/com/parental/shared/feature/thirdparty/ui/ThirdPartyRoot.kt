package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parental.shared.core.network.ApiClient
import com.parental.shared.core.ui.components.BottomNavItem
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.notifications.ui.NotificationsScreen
import com.parental.shared.feature.notifications.ui.NotificationsViewModel
import com.parental.shared.feature.notifications.ui.UnreadBadge
import com.parental.shared.feature.notifications.ui.UnreadCountPoller
import kotlinx.datetime.TimeZone

/**
 * El árbol de navegación del terciario temporal: **dos destinos y nada más**.
 *
 * No reusa el `Screen.Home` de los progenitores ni le pasa un modo: la restricción tiene
 * que ser auditable por construcción (`odd/tasks/terciarios-temporales.md`, §6). Lo único
 * compartido con los progenitores es el contenido de cada pantalla (filas de agenda,
 * barra superior, navegación y tokens), no el grafo.
 */
internal val THIRD_PARTY_NAV_ITEMS = listOf(BottomNavItem.Home, BottomNavItem.Actividades)

@Composable
fun ThirdPartyRoot(
    token: String,
    userName: String,
    bondId: String?,
    currentUserId: String?,
    onLogout: () -> Unit,
) {
    var bottomRoute by remember { mutableStateOf(BottomNavItem.Home.route) }
    var showNotifications by remember { mutableStateOf(false) }

    // La campana existe en las dos pantallas, así que el contador vive acá (una sola
    // fuente) con el mismo patrón que el Home de los progenitores: badge + poller.
    val notificationsApi = remember { ApiClient.notificationsApi() }
    val unreadBadge = remember(token) { UnreadBadge() }
    val unreadCount by unreadBadge.count.collectAsState()
    val unreadPoller = remember(token) {
        UnreadCountPoller(
            api = notificationsApi,
            token = token,
            badge = unreadBadge,
        )
    }
    DisposableEffect(unreadPoller) {
        onDispose { unreadPoller.destroy() }
    }
    LaunchedEffect(unreadPoller, showNotifications) {
        if (!showNotifications) {
            unreadPoller.start()
        } else {
            unreadPoller.stop()
        }
    }

    if (bondId == null || currentUserId == null) {
        Scaffold { padding ->
            EmptyContentState(
                title = "Sin vínculo activo",
                message = "No hay un vínculo con acceso temporal para mostrar.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        return
    }

    val viewModel = remember(currentUserId, bondId) {
        ThirdPartyViewModel(
            thirdPartiesApi = ApiClient.thirdPartiesApi(),
            activitiesApi = ApiClient.activitiesApi(),
            categoriesApi = ApiClient.categoriesApi(),
            bondsApi = ApiClient.bondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = currentUserId,
            userName = userName,
        )
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }
    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    val state by viewModel.state.collectAsState()
    val zone = remember { TimeZone.currentSystemDefault() }

    if (showNotifications) {
        val notificationsViewModel = remember(token) {
            NotificationsViewModel(
                api = notificationsApi,
                token = token,
                badge = unreadBadge,
            )
        }
        DisposableEffect(notificationsViewModel) {
            onDispose { notificationsViewModel.destroy() }
        }
        NotificationsScreen(
            viewModel = notificationsViewModel,
            onBack = { showNotifications = false },
            onLogout = onLogout,
        )
        return
    }

    when (bottomRoute) {
        BottomNavItem.Actividades.route -> ThirdPartyActivitiesScreen(
            state = state,
            onHome = { bottomRoute = BottomNavItem.Home.route },
            onSelectFilter = viewModel::selectFilter,
            onOpenDetail = viewModel::openDetail,
            onFinalize = viewModel::complete,
            onMessageShown = viewModel::dismissMessage,
            unreadCount = unreadCount,
            onOpenNotifications = { showNotifications = true },
            onLogout = onLogout,
        )

        else -> ThirdPartyHomeScreen(
            state = state,
            onOpenActivities = { bottomRoute = BottomNavItem.Actividades.route },
            onOpenDetail = viewModel::openDetail,
            onFinalize = viewModel::complete,
            onOpenPermit = viewModel::showPermitDialog,
            onAcknowledgePermit = viewModel::acknowledgePermit,
            onMessageShown = viewModel::dismissMessage,
            unreadCount = unreadCount,
            onOpenNotifications = { showNotifications = true },
            onLogout = onLogout,
        )
    }

    // La hoja de detalle se monta acá y no dentro de una pantalla: el Inicio y la lista
    // abren la MISMA tarea, y con dos montajes habría dos `ModalBottomSheet` capaces de
    // divergir.
    val detail = state.detail
    if (detail != ThirdPartyDetail.Closed) {
        val resolve: (String) -> CategoryInfo? = { id -> state.categories[id] }
        ThirdPartyActivitySheet(
            detail = detail,
            ownerName = state.permit?.authorizedByName,
            resolve = resolve,
            currentUserId = currentUserId,
            zone = zone,
            isCompleting = state.isCompleting,
            onDismiss = viewModel::closeDetail,
            onFinalize = viewModel::complete,
            onAddAttachment = viewModel::addAttachment,
        )
    }
}
