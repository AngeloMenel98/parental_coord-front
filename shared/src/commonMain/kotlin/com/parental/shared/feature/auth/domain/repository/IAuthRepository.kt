package com.parental.shared.feature.auth.domain.repository

import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.User

interface IAuthRepository {
    val isLoggedIn: Boolean
    val token: String?
    val currentUser: User?

    suspend fun login(email: String, password: String): Result<AuthResponse>
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): Result<AuthResponse>
    suspend fun getProfile(): Result<User>
    fun logout()
}
