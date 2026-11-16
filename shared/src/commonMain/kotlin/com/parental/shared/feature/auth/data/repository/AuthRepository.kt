package com.parental.shared.feature.auth.data.repository

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.ApiClient
import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.User
import com.parental.shared.feature.auth.domain.repository.IAuthRepository

class AuthRepository : IAuthRepository {
    private val authApi = ApiClient.authApi()
    private var cachedToken: String? = null
    private var cachedUser: User? = null

    override val isLoggedIn: Boolean get() = cachedToken != null
    override val token: String? get() = cachedToken
    override val currentUser: User? get() = cachedUser

    override suspend fun login(email: String, password: String): Result<AuthResponse> {
        return when (val result = authApi.login(email, password)) {
            is ApiResult.Success -> {
                cachedToken = result.data.access_token
                cachedUser = result.data.user
                Result.success(result.data)
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.message))
            }
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): Result<AuthResponse> {
        return when (val result = authApi.register(email, password, firstName, lastName)) {
            is ApiResult.Success -> {
                cachedToken = result.data.access_token
                Result.success(result.data)
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.message))
            }
        }
    }

    override suspend fun getProfile(): Result<User> {
        val token = cachedToken ?: return Result.failure(Exception("Not authenticated"))
        return when (val result = authApi.me(token)) {
            is ApiResult.Success -> Result.success(result.data)
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /**
     * El token sale de [cachedToken] y, en el éxito, se reemplaza por el que devolvió el
     * back: el cambio emite un token nuevo a propósito (el anterior llevaba la claim
     * `mustChangePassword` en `true`), así que quedarse con el viejo dejaría al usuario
     * bloqueado en la primera ruta autenticada.
     */
    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<AuthResponse> {
        val token = cachedToken ?: return Result.failure(Exception("Not authenticated"))
        return when (val result = authApi.changePassword(token, currentPassword, newPassword)) {
            is ApiResult.Success -> {
                cachedToken = result.data.access_token
                cachedUser = result.data.user
                Result.success(result.data)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    override fun logout() {
        cachedToken = null
        cachedUser = null
    }
}
