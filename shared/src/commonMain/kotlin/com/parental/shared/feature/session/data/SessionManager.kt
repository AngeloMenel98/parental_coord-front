package com.parental.shared.feature.session.data

import com.parental.shared.platform.Platform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot inmutable de la sesión. La UI la observa vía [SessionManager.state]. */
data class SessionState(
    val token: String? = null,
    val userName: String? = null,
    val role: String? = null,
    val bondId: String? = null,
    val userId: String? = null,
    /**
     * Rol DENTRO del vínculo (`progenitor|coordinador|third_party`), separado de [role]
     * (`systemRole`: `user|admin`).
     *
     * Son dos ejes distintos y por eso son dos campos: `system_role` enruta a grumos
     * (Admin vs app) y este rol decide la vista y, del lado del servidor, la autorización.
     * Se deriva en el cliente desde `GET /bonds` (`VinculoMember.role` del miembro cuyo
     * `user.id` es el usuario actual), sin esperar a que el JWT lleve el vínculo.
     *
     * Un valor desconocido cae en el mismo `else` que `user`: la degradación segura es el
     * Home de siempre, nunca una vista restringida por accidente.
     */
    val bondRole: String? = null,
    /** True while login is in progress (bonds fetch pending). Keeps UI on Login screen. */
    val loginPending: Boolean = false,
    /**
     * La contraseña con la que se entró la generó el sistema y todavía no se cambió.
     *
     * Mientras esté en `true`, `App` no monta ninguna vista de la app: el único destino es
     * la pantalla de cambio. Se persiste porque el bloqueo no puede evaporarse al cerrar y
     * volver a abrir: el back responde 403 en el resto de las rutas de todos modos, pero la
     * app no tiene por qué descubrirlo con un error.
     */
    val mustChangePassword: Boolean = false,
) {
    val isLoggedIn: Boolean get() = token != null
}

/**
 * Fuente única de verdad de la sesión. Expone un [StateFlow] (sin dependencia de
 * Compose runtime) y persiste cada cambio en el storage de la plataforma.
 * Las mutaciones usan `update {}`: son atómicas aunque lleguen desde varios hilos.
 */
class SessionManager {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite cuando el backend rechaza el token (401); la UI responde con el logout. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // Lecturas puntuales para código no-UI (VMs, plugin HTTP). La UI debe observar [state].
    val token: String? get() = _state.value.token
    val userName: String? get() = _state.value.userName
    val role: String? get() = _state.value.role
    val bondId: String? get() = _state.value.bondId
    val userId: String? get() = _state.value.userId
    val bondRole: String? get() = _state.value.bondRole
    val mustChangePassword: Boolean get() = _state.value.mustChangePassword
    val isLoggedIn: Boolean get() = _state.value.isLoggedIn

    /** Llamado desde la capa de red (puede ser desde cualquier hilo). */
    fun notifySessionExpired() {
        if (isLoggedIn) _sessionExpired.tryEmit(Unit)
    }

    fun setSession(
        token: String,
        userName: String,
        role: String?,
        bondId: String? = null,
        userId: String? = null,
        mustChangePassword: Boolean = false,
    ) {
        // El vínculo no se hereda: bondId/bondRole se escriben con lo que recibe la
        // llamada —o null—, nunca con lo que dejó la sesión anterior. mustChangePassword
        // también se fija explícito desde la respuesta del login (S2, develop).
        _state.update {
            it.copy(
                token = token,
                userName = userName,
                role = role,
                bondId = null,
                bondRole = null,
                userId = userId,
                mustChangePassword = mustChangePassword,
            )
        }
        // Persist to storage (los null se borran para no dejar datos de una sesión anterior)
        Platform.saveSecureString(KEY_TOKEN, token)
        Platform.saveString(KEY_USER_NAME, userName)
        persistOrRemove(KEY_ROLE, role)
        persistOrRemove(KEY_BOND_ID, bondId)
        persistOrRemove(KEY_USER_ID, userId)
        Platform.saveString(KEY_MUST_CHANGE_PASSWORD, mustChangePassword.toString())
    }

    /** Mark login as in-progress so UI stays on Login screen during bonds fetch. */
    fun beginLogin() {
        _state.update { it.copy(loginPending = true) }
    }

    /** Mark login as complete — safe to navigate to Home/Admin. */
    fun completeLogin() {
        _state.update { it.copy(loginPending = false) }
    }

    fun updateBondId(bondId: String?) {
        _state.update { it.copy(bondId = bondId) }
        persistOrRemove(KEY_BOND_ID, bondId)
    }

    /**
     * Reemplaza el token de la sesión viva: lo pide el cambio de password, que
     * devuelve un access_token nuevo con el que el backend invalidó al viejo.
     */
    fun updateToken(newToken: String) {
        _state.update { it.copy(token = newToken) }
        Platform.saveString(KEY_TOKEN, newToken)
    }

    /** Fija (o apaga) el bloqueo de cambio de contraseña; vive también en storage. */
    fun updateMustChangePassword(mustChangePassword: Boolean) {
        _state.update { it.copy(mustChangePassword = mustChangePassword) }
        Platform.saveString(KEY_MUST_CHANGE_PASSWORD, mustChangePassword.toString())
    }

    /** Backfill userId for legacy sessions restored without one (via GET /auth/me). */
    fun updateUserId(userId: String) {
        _state.update { it.copy(userId = userId) }
        Platform.saveString(KEY_USER_ID, userId)
    }

    /**
     * Fija el rol en el vínculo. Se llama al loguear y también al restaurar la sesión,
     * porque las sesiones guardadas antes de esta feature no lo tienen y sin él la app
     * volvería al Home de los progenitores con los datos de todos (S5).
     */
    fun updateBondRole(bondRole: String?) {
        _state.update { it.copy(bondRole = bondRole) }
        if (bondRole != null) {
            Platform.saveString(KEY_BOND_ROLE, bondRole)
        } else {
            Platform.remove(KEY_BOND_ROLE)
        }
    }

    fun clear() {
        _state.value = SessionState()
        // Clear from storage
        Platform.removeSecureString(KEY_TOKEN)
        Platform.remove(KEY_TOKEN) // token en claro de versiones anteriores, si quedó alguno
        Platform.remove(KEY_USER_NAME)
        Platform.remove(KEY_ROLE)
        Platform.remove(KEY_BOND_ID)
        Platform.remove(KEY_USER_ID)
        Platform.remove(KEY_BOND_ROLE)
        Platform.remove(KEY_MUST_CHANGE_PASSWORD)
    }

    /**
     * Restore session from persistent storage. Call on app startup.
     * Returns true if a session was restored, false otherwise.
     */
    fun restoreFromStorage(): Boolean {
        val savedToken = Platform.loadSecureString(KEY_TOKEN) ?: migrateLegacyToken() ?: return false
        _state.value = SessionState(
            token = savedToken,
            userName = Platform.loadString(KEY_USER_NAME).orEmpty(),
            role = Platform.loadString(KEY_ROLE),
            bondId = Platform.loadString(KEY_BOND_ID),
            userId = Platform.loadString(KEY_USER_ID),
            bondRole = Platform.loadString(KEY_BOND_ROLE),
            mustChangePassword = Platform.loadString(KEY_MUST_CHANGE_PASSWORD) == "true",
        )
        return true
    }

    /** Instalaciones previas guardaban el token en claro: lo pasa al almacén seguro y lo borra. */
    private fun migrateLegacyToken(): String? {
        val legacyToken = Platform.loadString(KEY_TOKEN) ?: return null
        Platform.saveSecureString(KEY_TOKEN, legacyToken)
        Platform.remove(KEY_TOKEN)
        return legacyToken
    }

    private fun persistOrRemove(key: String, value: String?) {
        if (value != null) Platform.saveString(key, value) else Platform.remove(key)
    }

    companion object {
        private const val KEY_TOKEN = "session_token"
        private const val KEY_USER_NAME = "session_user_name"
        private const val KEY_ROLE = "session_role"
        private const val KEY_BOND_ID = "session_bond_id"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_BOND_ROLE = "session_bond_role"
        private const val KEY_MUST_CHANGE_PASSWORD = "session_must_change_password"
    }
}
