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

    /**
     * Cambia la contraseña provisoria por una elegida (S2).
     *
     * El token vive en el repositorio, no en el parámetro: el resto de la app no tiene por
     * qué saber cuál es. En el éxito el token cacheado pasa a ser el NUEVO que devolvió el
     * servidor — el viejo ya no es el que corresponde enviar — y el usuario cacheado se
     * reemplaza por el que vino en la respuesta (con `mustChangePassword: false`).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<AuthResponse>

    fun logout()
}
