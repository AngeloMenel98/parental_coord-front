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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.theme.ParentalCoordinationTheme
import com.parental.shared.feature.activities.ui.ActividadesScreen
import com.parental.shared.feature.activities.ui.ActividadesViewModel
import com.parental.shared.feature.auth.data.remote.AuthApi
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.auth.ui.LoginScreen
import com.parental.shared.feature.auth.ui.LoginViewModel
import com.parental.shared.feature.admin.ui.AdminScreen
import com.parental.shared.feature.admin.ui.AdminViewModel
import com.parental.shared.feature.home.ui.HomeScreen
import com.parental.shared.feature.home.ui.HomeViewModel
import com.parental.shared.feature.session.data.SessionManager
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Admin : Screen("admin")
}

/**
 * Dueño de los ViewModels de la sesión actual. Vive como ViewModel de la
 * Activity (sobrevive a rotaciones) y se vacía en el logout para que el
 * próximo usuario no herede VMs ni datos del anterior.
 */
private class SessionViewModelStoreOwner : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun reset() = viewModelStore.clear()

    override fun onCleared() = viewModelStore.clear()
}

@Composable
fun App() {
    KoinContext {
        // Singletons de Koin: la sesión ya viene restaurada desde el storage.
        val sessionManager = koinInject<SessionManager>()
        val authApi = koinInject<AuthApi>()

        LaunchedEffect(Unit) {
            // Legacy sessions (pre userId) — backfill via GET /auth/me so the
            // activities confirm-affordance visibility rule can evaluate.
            if (sessionManager.token != null && sessionManager.userId == null) {
                when (val me = authApi.me()) {
                    is ApiResult.Success -> sessionManager.updateUserId(me.data.id)
                    is ApiResult.Error -> Unit // degrade safely: no confirm affordance until re-login
                }
            }
        }

        val sessionOwner = viewModel { SessionViewModelStoreOwner() }

        // ADR-10: un único wrap del tema en commonMain — Android hoy, iOS hereda.
        ParentalCoordinationTheme {
            CompositionLocalProvider(LocalViewModelStoreOwner provides sessionOwner) {
                AppContent(
                    sessionManager = sessionManager,
                    onSessionEnded = sessionOwner::reset,
                )
            }
        }
    }
}

/**
 * Contenido de la app (ADR-10): el bloque `when` existente se movió verbatim
 * desde App() — los `return` tempranos son legales dentro de una composable
 * función (los lambdas no-inline no los permitirían). Cero delta de layout.
 */
@Composable
private fun AppContent(
    sessionManager: SessionManager,
    onSessionEnded: () -> Unit,
) {
    val authRepository = koinInject<IAuthRepository>()

    val onLogout: () -> Unit = {
        authRepository.logout()
        sessionManager.clear()
        onSessionEnded()
    }

    // Token vencido/revocado (401 en un request autenticado) → mismo camino que el logout manual.
    LaunchedEffect(sessionManager) {
        sessionManager.sessionExpired.collect {
            if (sessionManager.isLoggedIn) onLogout()
        }
    }

    // Snapshot reactivo de la sesión: cualquier cambio (login, backfill, logout) recompone.
    val session by sessionManager.state.collectAsStateWithLifecycle()

    val currentScreen = if (session.loginPending) {
        // Login in progress (bonds fetch pending) — keep on Login screen
        Screen.Login
    } else if (session.isLoggedIn) {
        when (session.role) {
            "admin" -> Screen.Admin
            else -> Screen.Home
        }
    } else {
        Screen.Login
    }

    when (currentScreen) {
        Screen.Login -> {
            // El flujo de login vive en el VM (sobrevive a rotaciones); al
            // completarse, el cambio de sesión recompone hacia Home/Admin.
            LoginScreen(viewModel = koinViewModel<LoginViewModel>())
        }

        Screen.Admin -> {
            AdminScreen(
                viewModel = koinViewModel<AdminViewModel>(),
                onLogout = onLogout,
            )
        }

        Screen.Home -> {
            var bottomRoute by rememberSaveable { mutableStateOf("home") }

            // Gastos/Terceros son placeholders inertes: solo Inicio/Actividades navegan.
            val onNavigateBottom: (String) -> Unit = { route ->
                when (route) {
                    "home", "activities" -> bottomRoute = route
                    else -> Unit
                }
            }

            if (bottomRoute == "activities") {
                when (session.bondId) {
                    null -> SinVinculoScreen(onGoHome = { bottomRoute = "home" })
                    else -> ActividadesScreen(
                        // Una única instancia por sesión: volver a la tab no refetchea.
                        viewModel = koinViewModel<ActividadesViewModel>(),
                        onNavigateBottom = onNavigateBottom,
                    )
                }
            } else {
                HomeScreen(
                    viewModel = koinViewModel<HomeViewModel>(),
                    onLogout = onLogout,
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
