package com.parental.shared.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Orquesta el login completo (credenciales → sesión → vínculo activo) dentro de
 * [viewModelScope]: una rotación o cambio de tema a mitad del flujo no lo
 * cancela, porque el VM sobrevive a la recreación de la Activity. La
 * navegación no se dispara desde acá: App reacciona al cambio de sesión.
 */
class LoginViewModel(
    private val authRepository: IAuthRepository,
    private val sessionManager: SessionManager,
    private val bondsApi: BondsApi,
    private val thirdPartiesApi: ThirdPartiesApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password are required") }
            return
        }
        // Single-flight: ignora un segundo tap/IME Done mientras el login está en vuelo.
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.login(state.email, state.password)
                .onSuccess { response -> startSession(response) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // El mensaje de error nunca queda vacio: un Exception sin message
                            // mostraba el cartel sin texto (fix de main, forma del refactor).
                            error = e.message?.ifBlank { "Login failed" } ?: "Login failed",
                        )
                    }
                }
        }
    }

    private suspend fun startSession(response: AuthResponse) {
        // Mark login as pending — keeps UI on Login screen during bonds fetch
        sessionManager.beginLogin()
        try {
            sessionManager.setSession(
                token = response.access_token,
                userName = response.user.firstName ?: response.user.email,
                role = response.user.systemRole,
                userId = response.user.id,
                // S2/D3: es el servidor el que dice si la contraseña con la que se entró la
                // generó el sistema. Si el flag se pierde acá, la app abriría el Inicio de
                // alguien que debe cambiarla.
                mustChangePassword = response.user.mustChangePassword,
            )

            // D2/F7: el rol vive en el miembro del vínculo, no en el JWT ni en
            // `system_role`; la derivación también fija el `bondId` del vínculo activo.
            // 🔴 Con una contraseña provisoria NO se deriva: el guard del back responde
            // `403 PASSWORD_CHANGE_REQUIRED` en `GET /bonds` mientras el flag esté
            // encendido, así que sería trabajo perdido a sabiendas (R2-001). Esa
            // derivación la hace [BondDerivationCoordinator] al salir del cambio
            // obligatorio de contraseña.
            if (!sessionManager.mustChangePassword) {
                sessionManager.deriveBondWithRetry(bondsApi, thirdPartiesApi)
            }
        } finally {
            // Complete login — now safe to navigate to Home/Admin. También si se cancela.
            sessionManager.completeLogin()
            _uiState.update { it.copy(isLoading = false, password = "") }
        }
    }
}
