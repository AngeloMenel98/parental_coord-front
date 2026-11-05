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
    /** True while login is in progress (bonds fetch pending). Keeps UI on Login screen. */
    val loginPending: Boolean = false,
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
    val isLoggedIn: Boolean get() = _state.value.isLoggedIn

    /** Llamado desde la capa de red (puede ser desde cualquier hilo). */
    fun notifySessionExpired() {
        if (isLoggedIn) _sessionExpired.tryEmit(Unit)
    }

    fun setSession(token: String, userName: String, role: String?, bondId: String? = null, userId: String? = null) {
        // copy conserva loginPending (beginLogin() se llama antes).
        _state.update {
            it.copy(token = token, userName = userName, role = role, bondId = bondId, userId = userId)
        }
        // Persist to storage (los null se borran para no dejar datos de una sesión anterior)
        Platform.saveString(KEY_TOKEN, token)
        Platform.saveString(KEY_USER_NAME, userName)
        persistOrRemove(KEY_ROLE, role)
        persistOrRemove(KEY_BOND_ID, bondId)
        persistOrRemove(KEY_USER_ID, userId)
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

    /** Backfill userId for legacy sessions restored without one (via GET /auth/me). */
    fun updateUserId(userId: String) {
        _state.update { it.copy(userId = userId) }
        Platform.saveString(KEY_USER_ID, userId)
    }

    fun clear() {
        _state.value = SessionState()
        // Clear from storage
        Platform.remove(KEY_TOKEN)
        Platform.remove(KEY_USER_NAME)
        Platform.remove(KEY_ROLE)
        Platform.remove(KEY_BOND_ID)
        Platform.remove(KEY_USER_ID)
    }

    /**
     * Restore session from persistent storage. Call on app startup.
     * Returns true if a session was restored, false otherwise.
     */
    fun restoreFromStorage(): Boolean {
        val savedToken = Platform.loadString(KEY_TOKEN) ?: return false
        _state.value = SessionState(
            token = savedToken,
            userName = Platform.loadString(KEY_USER_NAME).orEmpty(),
            role = Platform.loadString(KEY_ROLE),
            bondId = Platform.loadString(KEY_BOND_ID),
            userId = Platform.loadString(KEY_USER_ID),
        )
        return true
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
    }
}
