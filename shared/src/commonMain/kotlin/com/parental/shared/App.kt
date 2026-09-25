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
import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.ui.LoginScreen
import com.parental.shared.feature.auth.ui.LoginViewModel
import com.parental.shared.feature.admin.ui.AdminScreen
import com.parental.shared.feature.admin.ui.AdminViewModel
import com.parental.shared.feature.home.ui.HomeScreen
import com.parental.shared.feature.home.ui.HomeViewModel
import com.parental.shared.feature.session.data.SessionManager

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
                onLogout = {
                    authRepository.logout()
                    sessionManager.clear()
                },
            )
        }

        Screen.Home -> {
            var bottomRoute by remember { mutableStateOf("home") }
            val token = sessionManager.token
            val userName = sessionManager.userName.orEmpty()

            if (token == null) {
                sessionManager.clear()
                return
            }

            // Gastos/Terceros son placeholders inertes: solo Inicio/Actividades navegan.
            val onNavigateBottom: (String) -> Unit = { route ->
                when (route) {
                    "home", "activities" -> bottomRoute = route
                    else -> Unit
                }
            }

            if (bottomRoute == "activities") {
                val bondId = sessionManager.bondId
                when {
                    bondId == null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> {
                        // userId: login lo provee; sesiones legacy → backfill vía /auth/me.
                        // keyed on userId para que el VM se reconstruya si el backfill llega tarde.
                        val currentUserId = sessionManager.userId
                        val activitiesViewModel = remember(currentUserId) {
                            ActividadesViewModel(
                                activitiesApi = ApiClient.activitiesApi(),
                                categoriesApi = ApiClient.categoriesApi(),
                                bondsApi = ApiClient.bondsApi(),
                                token = token,
                                bondId = bondId,
                                currentUserId = currentUserId ?: "",
                            )
                        }
                        DisposableEffect(Unit) {
                            onDispose { activitiesViewModel.destroy() }
                        }
                        ActividadesScreen(
                            viewModel = activitiesViewModel,
                            onNavigateBottom = onNavigateBottom,
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
                    onLogout = {
                        authRepository.logout()
                        sessionManager.clear()
                    },
                    onNavigateBottom = onNavigateBottom,
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
