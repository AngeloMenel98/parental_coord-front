package com.parental.shared.feature.session.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.parental.shared.platform.Platform

class SessionManager {
    var token: String? by mutableStateOf(null)
        private set
    var userName: String? by mutableStateOf(null)
        private set
    var role: String? by mutableStateOf(null)
        private set
    var bondId: String? by mutableStateOf(null)
        private set
    var userId: String? by mutableStateOf(null)
        private set
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
    var bondRole: String? by mutableStateOf(null)
        private set
    /** True while login is in progress (bonds fetch pending). Keeps UI on Login screen. */
    var loginPending: Boolean by mutableStateOf(false)
        internal set

    /**
     * La contraseña con la que se entró la generó el sistema y todavía no se cambió (S2).
     *
     * Mientras esté en `true`, `App` no monta ninguna vista de la app: el único destino es
     * la pantalla de cambio. Se persiste porque el bloqueo no puede evaporarse al cerrar y
     * volver a abrir: el back responde 403 en el resto de las rutas de todos modos, pero la
     * app no tiene por qué descubrirlo con un error.
     */
    var mustChangePassword: Boolean by mutableStateOf(false)
        private set

    val isLoggedIn: Boolean get() = token != null

    /**
     * Abre una sesión NUEVA.
     *
     * 🔴 El vínculo no se hereda: [bondId] y [bondRole] se escriben con lo que recibe la
     * llamada —o `null`—, nunca con lo que dejó la sesión anterior. Si no se resetearan, un
     * logout seguido del login de otra persona podría enrutar al nuevo usuario con el rol
     * del vínculo del anterior ([bondRole] es lo que decide si la app monta la vista de
     * terceros). El rol del vínculo se deriva DESPUÉS del login —o al salir del cambio
     * obligatorio de contraseña—, así que acá siempre arranca vacío.
     */
    fun setSession(
        token: String,
        userName: String,
        role: String?,
        bondId: String? = null,
        userId: String? = null,
        mustChangePassword: Boolean = false,
    ) {
        this.token = token
        this.userName = userName
        this.role = role
        this.bondId = bondId
        this.bondRole = null
        this.userId = userId
        this.mustChangePassword = mustChangePassword
        // Persist to storage
        Platform.saveString(KEY_TOKEN, token)
        Platform.saveString(KEY_USER_NAME, userName)
        role?.let { Platform.saveString(KEY_ROLE, it) }
        saveOrClear(KEY_BOND_ID, bondId)
        // Una sesión nueva no puede dejar el rol del vínculo de la anterior en disco.
        Platform.remove(KEY_BOND_ROLE)
        userId?.let { Platform.saveString(KEY_USER_ID, it) }
        Platform.saveString(KEY_MUST_CHANGE_PASSWORD, mustChangePassword.toString())
    }

    /** Escribe el valor en el almacenamiento o borra la clave: un `null` no deja residuo. */
    private fun saveOrClear(key: String, value: String?) {
        if (value != null) {
            Platform.saveString(key, value)
        } else {
            Platform.remove(key)
        }
    }

    /**
     * Reemplaza el token vigente por el que emitió el cambio de contraseña.
     *
     * Existe como operación propia —y no como un `setSession` completo— porque el cambio
     * de contraseña no toca nombre, rol ni vínculo: sólo la credencial.
     */
    fun updateToken(newToken: String) {
        this.token = newToken
        Platform.saveString(KEY_TOKEN, newToken)
    }

    /** Apaga (o vuelve a poner) el bloqueo por contraseña provisoria, y lo persiste. */
    fun updateMustChangePassword(mustChangePassword: Boolean) {
        this.mustChangePassword = mustChangePassword
        Platform.saveString(KEY_MUST_CHANGE_PASSWORD, mustChangePassword.toString())
    }

    /** Mark login as in-progress so UI stays on Login screen during bonds fetch. */
    fun beginLogin() {
        loginPending = true
    }

    /** Mark login as complete — safe to navigate to Home/Admin. */
    fun completeLogin() {
        loginPending = false
    }

    fun updateBondId(bondId: String?) {
        this.bondId = bondId
        saveOrClear(KEY_BOND_ID, bondId)
    }

    /** Backfill userId for legacy sessions restored without one (via GET /auth/me). */
    fun updateUserId(userId: String) {
        this.userId = userId
        Platform.saveString(KEY_USER_ID, userId)
    }

    /**
     * Fija el rol en el vínculo. Se llama al loguear y también al restaurar la sesión,
     * porque las sesiones guardadas antes de esta feature no lo tienen y sin él la app
     * volvería al Home de los progenitores con los datos de todos (S5).
     */
    fun updateBondRole(bondRole: String?) {
        this.bondRole = bondRole
        saveOrClear(KEY_BOND_ROLE, bondRole)
    }

    /** Cierra la sesión: no sobrevive NADA del usuario anterior, ni en memoria ni en disco. */
    fun clear() {
        token = null
        userName = null
        role = null
        bondId = null
        userId = null
        bondRole = null
        mustChangePassword = false
        loginPending = false
        // Clear from storage
        Platform.remove(KEY_TOKEN)
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
        val savedToken = Platform.loadString(KEY_TOKEN) ?: return false
        val savedUserName = Platform.loadString(KEY_USER_NAME).orEmpty()
        val savedRole = Platform.loadString(KEY_ROLE)
        val savedBondId = Platform.loadString(KEY_BOND_ID)
        val savedUserId = Platform.loadString(KEY_USER_ID)
        val savedBondRole = Platform.loadString(KEY_BOND_ROLE)
        token = savedToken
        userName = savedUserName
        role = savedRole
        bondId = savedBondId
        userId = savedUserId
        bondRole = savedBondRole
        mustChangePassword = Platform.loadString(KEY_MUST_CHANGE_PASSWORD) == "true"
        return true
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
