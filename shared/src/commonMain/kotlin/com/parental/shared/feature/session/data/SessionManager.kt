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
    /** True while login is in progress (bonds fetch pending). Keeps UI on Login screen. */
    var loginPending: Boolean by mutableStateOf(false)
        internal set

    val isLoggedIn: Boolean get() = token != null

    fun setSession(token: String, userName: String, role: String?, bondId: String? = null, userId: String? = null) {
        this.token = token
        this.userName = userName
        this.role = role
        this.bondId = bondId
        this.userId = userId
        // Persist to storage
        Platform.saveString(KEY_TOKEN, token)
        Platform.saveString(KEY_USER_NAME, userName)
        role?.let { Platform.saveString(KEY_ROLE, it) }
        bondId?.let { Platform.saveString(KEY_BOND_ID, it) }
        userId?.let { Platform.saveString(KEY_USER_ID, it) }
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
        if (bondId != null) {
            Platform.saveString(KEY_BOND_ID, bondId)
        } else {
            Platform.remove(KEY_BOND_ID)
        }
    }

    /** Backfill userId for legacy sessions restored without one (via GET /auth/me). */
    fun updateUserId(userId: String) {
        this.userId = userId
        Platform.saveString(KEY_USER_ID, userId)
    }

    fun clear() {
        token = null
        userName = null
        role = null
        bondId = null
        userId = null
        loginPending = false
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
        val savedUserName = Platform.loadString(KEY_USER_NAME).orEmpty()
        val savedRole = Platform.loadString(KEY_ROLE)
        val savedBondId = Platform.loadString(KEY_BOND_ID)
        val savedUserId = Platform.loadString(KEY_USER_ID)
        token = savedToken
        userName = savedUserName
        role = savedRole
        bondId = savedBondId
        userId = savedUserId
        return true
    }

    companion object {
        private const val KEY_TOKEN = "session_token"
        private const val KEY_USER_NAME = "session_user_name"
        private const val KEY_ROLE = "session_role"
        private const val KEY_BOND_ID = "session_bond_id"
        private const val KEY_USER_ID = "session_user_id"
    }
}
