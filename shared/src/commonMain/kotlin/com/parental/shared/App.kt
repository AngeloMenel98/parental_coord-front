package com.parental.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.ApiClient
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.theme.ParentalCoordinationTheme
import com.parental.shared.feature.activities.ui.ActividadesScreen
import com.parental.shared.feature.activities.ui.ActividadesViewModel
import com.parental.shared.feature.expenses.ui.ExpensesScreen
import com.parental.shared.feature.expenses.ui.ExpensesViewModel
import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.ui.LoginScreen
import com.parental.shared.feature.auth.ui.LoginViewModel
import com.parental.shared.feature.admin.ui.AdminScreen
import com.parental.shared.feature.admin.ui.AdminViewModel
import com.parental.shared.feature.home.ui.HomeScreen
import com.parental.shared.feature.home.ui.HomeViewModel
import com.parental.shared.feature.notifications.ui.NotificationsScreen
import com.parental.shared.feature.notifications.ui.NotificationsViewModel
import com.parental.shared.feature.notifications.ui.UnreadBadge
import com.parental.shared.feature.notifications.ui.UnreadCountPoller
import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.core.network.realtime.RealtimeConnectionManager
import com.parental.shared.platform.Platform

private sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Admin : Screen("admin")
}

@Composable
fun App() {
    val authRepository = remember { AuthRepository() }
    val sessionManager = remember { SessionManager() }

    // Restore persisted session on startup
    LaunchedEffect(Unit) {
        sessionManager.restoreFromStorage()
        // Legacy sessions (pre userId) — backfill via GET /auth/me so the
        // activities confirm-affordance visibility rule can evaluate.
        val token = sessionManager.token
        if (token != null && sessionManager.userId == null) {
            when (val me = ApiClient.authApi().me(token)) {
                is ApiResult.Success -> sessionManager.updateUserId(me.data.id)
                is ApiResult.Error -> Unit // degrade safely: no confirm affordance until re-login
            }
        }
    }

    // ADR-10: un único wrap del tema en commonMain — Android hoy, iOS hereda.
    ParentalCoordinationTheme {
        AppContent(authRepository = authRepository, sessionManager = sessionManager)
    }
}

/**
 * Contenido de la app (ADR-10): el bloque `when` existente se movió verbatim
 * desde App() — los `return` tempranos son legales dentro de una composable
 * función (los lambdas no-inline no los permitirían). Cero delta de layout.
 */
@Composable
private fun AppContent(
    authRepository: AuthRepository,
    sessionManager: SessionManager,
) {
    // S12 · G-3 — la salida vive en la barra global de todas las vistas, así que el
    // efecto es UNO solo: no hay dos lambdas que puedan divergir.
    val logout: () -> Unit = {
        authRepository.logout()
        sessionManager.clear()
    }

    val currentScreen = if (sessionManager.loginPending) {
        // Login in progress (bonds fetch pending) — keep on Login screen
        Screen.Login
    } else if (sessionManager.isLoggedIn) {
        when (sessionManager.role) {
            "admin" -> Screen.Admin
            else -> Screen.Home
        }
    } else {
        Screen.Login
    }

    when (currentScreen) {
        Screen.Login -> {
            val viewModel = remember { LoginViewModel(authRepository) }
            DisposableEffect(Unit) {
                onDispose { viewModel.destroy() }
            }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { response ->
                    val name = response.user.firstName ?: response.user.email

                    // Mark login as pending — keeps UI on Login screen during bonds fetch
                    sessionManager.beginLogin()

                    sessionManager.setSession(
                        token = response.access_token,
                        userName = name,
                        role = response.user.systemRole,
                        userId = response.user.id,
                    )

                    // Fetch bonds and set the first one as active
                    val bondsApi = ApiClient.bondsApi()
                    try {
                        val result = bondsApi.getMyBonds(response.access_token)
                        when (result) {
                            is ApiResult.Success -> {
                                if (result.data.isNotEmpty()) {
                                    val firstBond = result.data.first()
                                    sessionManager.updateBondId(firstBond.id)
                                } else {
                                    // No bonds found for this user
                                }
                            }
                            is ApiResult.Error -> {
                                // Failed to fetch bonds
                            }
                        }
                    } catch (e: Exception) {
                        // Exception fetching bonds
                    } finally {
                        // Complete login — now safe to navigate to Home/Admin
                        sessionManager.completeLogin()
                    }
                },
            )
        }

        Screen.Admin -> {
            val adminApi = remember { ApiClient.adminApi() }
            val token = sessionManager.token
            if (token == null) {
                sessionManager.clear()
                return
            }
            val viewModel = remember {
                AdminViewModel(
                    adminApi = adminApi,
                    token = token,
                )
            }
            DisposableEffect(Unit) {
                onDispose { viewModel.destroy() }
            }
            AdminScreen(
                viewModel = viewModel,
                onLogout = logout,
            )
        }

        Screen.Home -> {
            var bottomRoute by remember { mutableStateOf("home") }
            var showNotifications by remember { mutableStateOf(false) }
            val token = sessionManager.token
            val userName = sessionManager.userName.orEmpty()

            if (token == null) {
                sessionManager.clear()
                return
            }

            // Terceros sigue siendo placeholder; Gastos ya navega (T29/T19).
            val onNavigateBottom: (String) -> Unit = { route ->
                when (route) {
                    "home", "activities", "expenses" -> bottomRoute = route
                    else -> Unit
                }
            }

            // Realtime (slice 5): UN manager por composición de Home, ya no dentro de la
            // pestaña Activities. El badge de Inicio necesita la señal
            // `notification.created` mientras el usuario está en Home, y la pestaña se
            // desmonta al navegar. Sin vínculo no hay frames posibles (el handshake
            // cerraría 4403 en bucle de reconexión), así que sólo corre con bondId.
            val bondId = sessionManager.bondId
            val currentUserId = sessionManager.userId
            val realtimeManager = remember(token) {
                RealtimeConnectionManager(
                    http = ApiClient.httpClient,
                    url = Platform.wsUrl,
                    tokenProvider = { sessionManager.token },
                )
            }
            // Único dueño del ciclo de vida (antes lo era la rama Activities). start() y
            // stop() son idempotentes ⇒ exactamente UN stop() por manager, y un bondId que
            // aparece tarde (backfill) arranca el loop sin recrear el manager.
            DisposableEffect(realtimeManager, bondId) {
                if (bondId != null) realtimeManager.start() else realtimeManager.stop()
                onDispose { realtimeManager.stop() }
            }

            // Notificaciones (slice 4): el contador vive acá porque lo leen DOS
            // superficies (la campana de Home y la lista) y lo escriben TRES
            // (el poller, markRead, markAllRead). Una sola fuente de verdad.
            val notificationsApi = remember { ApiClient.notificationsApi() }
            val unreadBadge = remember(token) { UnreadBadge() }
            val unreadPoller = remember(token) {
                UnreadCountPoller(
                    api = notificationsApi,
                    token = token,
                    badge = unreadBadge,
                )
            }
            val unreadCount by unreadBadge.count.collectAsState()

            DisposableEffect(unreadPoller) {
                onDispose { unreadPoller.destroy() }
            }
            // Poll-only (S8): el bucle corre mientras haya una campana a la vista. Desde
            // S12 la campana vive también en Actividades (barra global), así que ya no
            // alcanza con "sólo Home": el contador tiene que estar fresco en las dos. En
            // la lista el VM ya sincroniza el badge, por eso ahí se detiene. Al volver de
            // la lista este efecto rearranca y fetcha de inmediato.
            LaunchedEffect(unreadPoller, showNotifications) {
                if (!showNotifications) {
                    unreadPoller.start()
                } else {
                    unreadPoller.stop()
                }
            }

            // Puente frame → badge (slice 5): un `notification.created` refresca el
            // contador sin esperar los 30 s del poll. El coalescer del manager ya acota
            // la ráfaga a ≤1 señal por ventana, así que esto son pocos GET extra, y
            // `refreshNow` funciona con el bucle parado (usuario en la lista o en
            // Actividades) ⇒ el badge no queda viejo mientras el poller duerme.
            LaunchedEffect(realtimeManager, unreadPoller) {
                realtimeManager.notificationSignals.collect {
                    unreadPoller.refreshNow()
                }
            }

            if (showNotifications) {
                // El VM se reconstruye en cada apertura ⇒ la lista siempre se carga
                // al abrirse, sin caché vieja entre visitas.
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
                    onLogout = logout,
                )
            } else if (bottomRoute == "activities") {
                when {
                    bondId == null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> {
                        // userId: login lo provee; sesiones legacy → backfill vía /auth/me.
                        // keyed on userId para que el VM se reconstruya si el backfill llega tarde.
                        val activitiesViewModel = remember(currentUserId) {
                            ActividadesViewModel(
                                activitiesApi = ApiClient.activitiesApi(),
                                categoriesApi = ApiClient.categoriesApi(),
                                bondsApi = ApiClient.bondsApi(),
                                token = token,
                                bondId = bondId,
                                currentUserId = currentUserId ?: "",
                                realtime = realtimeManager,
                            )
                        }
                        // W2: keyed en currentUserId — al cambiar el userId (backfill tardío)
                        // o salir de la pantalla, el dispose corre contra el VM DE ESA
                        // composición ⇒ cada VM recibe exactamente UN destroy().
                        // S1: un único efecto (antes eran dos ⇒ destroy() dos veces).
                        // N4 (slice 5): el manager ya NO se detiene acá — su dueño es la
                        // pantalla Home, que sigue viva mientras el usuario navega.
                        DisposableEffect(currentUserId) {
                            onDispose { activitiesViewModel.destroy() }
                        }
                        ActividadesScreen(
                            viewModel = activitiesViewModel,
                            onNavigateBottom = onNavigateBottom,
                            // S12 · la campana de la barra global abre la lista que ya
                            // vive en esta rama de Home; el contador es el mismo.
                            unreadCount = unreadCount,
                            onOpenNotifications = { showNotifications = true },
                            onLogout = logout,
                        )
                    }
                }
            } else if (bottomRoute == "expenses") {
                when {
                    bondId == null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> {
                        val expensesViewModel = remember(currentUserId) {
                            ExpensesViewModel(
                                api = ApiClient.expensesApi(),
                                token = token,
                                bondId = bondId,
                            )
                        }
                        DisposableEffect(currentUserId) {
                            onDispose { expensesViewModel.destroy() }
                        }
                        ExpensesScreen(
                            viewModel = expensesViewModel,
                            onLogout = logout,
                            onNavigateBottom = onNavigateBottom,
                            unreadCount = unreadCount,
                            onOpenNotifications = { showNotifications = true },
                        )
                    }
                }
            } else {
                val homeApi = remember { ApiClient.homeApi() }
                val viewModel = remember {
                    HomeViewModel(
                        homeApi = homeApi,
                        token = token,
                        userName = userName,
                    )
                }
                DisposableEffect(Unit) {
                    onDispose { viewModel.destroy() }
                }
                HomeScreen(
                    viewModel = viewModel,
                    onLogout = logout,
                    onNavigateBottom = onNavigateBottom,
                    unreadCount = unreadCount,
                    onOpenNotifications = { showNotifications = true },
                )
            }
        }
    }
}

/** Estado liviano sin VM (A14): sin vínculo activo no se puede listar actividades. */
@Composable
private fun SinVinculoScreen(onGoHome: () -> Unit) {
    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedRoute = "activities",
                onNavigate = { route -> if (route == "home") onGoHome() },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sin vínculo activo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay un vínculo activo para mostrar actividades.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGoHome) {
                    Text("Ir al inicio")
                }
            }
        }
    }
}
