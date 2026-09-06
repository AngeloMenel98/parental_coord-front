package com.parental.shared.repository

import com.parental.shared.api.ApiClient
import com.parental.shared.api.ApiResult
import com.parental.shared.model.AuthResponse
import com.parental.shared.model.User

class AuthRepository {
    private val authApi = ApiClient.authApi()
    private var cachedToken: String? = null
    private var cachedUser: com.parental.shared.model.User? = null

    val isLoggedIn: Boolean get() = cachedToken != null
    val token: String? get() = cachedToken
    val currentUser: com.parental.shared.model.User? get() = cachedUser

    suspend fun login(email: String, password: String): Result<AuthResponse> {
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

    suspend fun register(
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

    suspend fun getProfile(): Result<User> {
        val token = cachedToken ?: return Result.failure(Exception("Not authenticated"))
        return when (val result = authApi.me(token)) {
            is ApiResult.Success -> Result.success(result.data)
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    fun logout() {
        cachedToken = null
        cachedUser = null
    }
}
