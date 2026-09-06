package com.parental.shared.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.parental.shared.api.ApiClient
import com.parental.shared.api.ApiResult
import com.parental.shared.api.BondsApi
import com.parental.shared.repository.AuthRepository
import com.parental.shared.session.SessionManager
import com.parental.shared.ui.components.BottomNavBar
import com.parental.shared.ui.components.BottomNavItem

private const val TAG = "BONDS_DEBUG"

private sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Admin : Screen("admin")
    data object Actividades : Screen("actividades")
    data object Gastos : Screen("gastos")
    data object Terceros : Screen("terceros")
}

@Composable
fun App() {
    val authRepository = remember { AuthRepository() }
    val sessionManager = remember { SessionManager() }

    // Restore persisted session on startup
    LaunchedEffect(Unit) {
        sessionManager.restoreFromStorage()
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
                    Log.d(TAG, "Login success for: ${response.user.email}")
                    Log.d(TAG, "Token received: ${response.access_token.take(20)}...")

                    // Mark login as pending — keeps UI on Login screen during bonds fetch
                    sessionManager.beginLogin()

                    sessionManager.setSession(
                        token = response.access_token,
                        userName = name,
                        role = response.user.systemRole,
                    )

                    // Fetch bonds and set the first one as active
                    val bondsApi = ApiClient.bondsApi()
                    Log.d(TAG, "Fetching bonds from: ${com.parental.shared.Platform.baseUrl}/bonds")
                    try {
                        val result = bondsApi.getMyBonds(response.access_token)
                        Log.d(TAG, "Bonds result: $result")
                        when (result) {
                            is ApiResult.Success -> {
                                Log.d(TAG, "Bonds count: ${result.data.size}")
                                if (result.data.isNotEmpty()) {
                                    val firstBond = result.data.first()
                                    Log.d(TAG, "Setting bondId: ${firstBond.id} (${firstBond.title})")
                                    sessionManager.updateBondId(firstBond.id)
                                } else {
                                    Log.w(TAG, "No bonds found for this user!")
                                }
                            }
                            is ApiResult.Error -> {
                                Log.e(TAG, "Failed to fetch bonds: HTTP ${result.code} - ${result.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception fetching bonds: ${e.message}")
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
                // Safe fallback: if token is null, go back to login
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

        else -> {
            // Main app with bottom navigation
            MainApp(
                sessionManager = sessionManager,
                authRepository = authRepository,
                initialRoute = currentScreen.route,
            )
        }
    }
}

@Composable
private fun MainApp(
    sessionManager: SessionManager,
    authRepository: AuthRepository,
    initialRoute: String,
) {
    var selectedRoute by remember { mutableStateOf(initialRoute) }

    val token = sessionManager.token
    val bondId = sessionManager.bondId
    val userName = sessionManager.userName.orEmpty()

    if (token == null) {
        sessionManager.clear()
        return
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedRoute = selectedRoute,
                onNavigate = { route -> selectedRoute = route },
            )
        },
    ) { padding ->
        when (selectedRoute) {
            Screen.Home.route -> {
                val homeApi = remember { ApiClient.homeApi() }
                val viewModel = remember {
                    HomeViewModel(
                        homeApi = homeApi,
                        token = token,
                        userName = userName,
                        bondId = bondId,
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
                )
            }

            Screen.Actividades.route -> {
                val actividadesApi = remember { ApiClient.actividadesApi() }
                val currentBondId = bondId
                if (currentBondId == null) {
                    // No bond ID, show error
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(padding),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        androidx.compose.material3.Text(
                            text = "No hay vínculo asociado",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    val viewModel = remember {
                        ActividadesViewModel(
                            actividadesApi = actividadesApi,
                            token = token,
                            bondId = currentBondId,
                        )
                    }
                    DisposableEffect(Unit) {
                        onDispose { viewModel.destroy() }
                    }
                    ActividadesScreen(viewModel = viewModel)
                }
            }

            Screen.Gastos.route -> {
                val gastosApi = remember { ApiClient.gastosApi() }
                val currentBondId = bondId
                if (currentBondId == null) {
                    // No bond ID, show error
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(padding),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        androidx.compose.material3.Text(
                            text = "No hay vínculo asociado",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    val viewModel = remember {
                        GastosViewModel(
                            gastosApi = gastosApi,
                            token = token,
                            bondId = currentBondId,
                        )
                    }
                    DisposableEffect(Unit) {
                        onDispose { viewModel.destroy() }
                    }
                    GastosScreen(viewModel = viewModel)
                }
            }

            Screen.Terceros.route -> {
                val tercerosApi = remember { ApiClient.tercerosApi() }
                val currentBondId = bondId
                if (currentBondId == null) {
                    // No bond ID, show error
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(padding),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        androidx.compose.material3.Text(
                            text = "No hay vínculo asociado",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    val viewModel = remember {
                        TercerosViewModel(
                            tercerosApi = tercerosApi,
                            token = token,
                            bondId = currentBondId,
                        )
                    }
                    DisposableEffect(Unit) {
                        onDispose { viewModel.destroy() }
                    }
                    TercerosScreen(viewModel = viewModel)
                }
            }
        }
    }
}
