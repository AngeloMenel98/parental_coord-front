package com.parental.shared.feature.auth.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
)

@Serializable
data class AuthResponse(
    val access_token: String,
    val user: User,
)

/**
 * Cambio de la contraseña provisoria (S2).
 *
 * Viaja con el Bearer del token que el usuario ya tiene: la contraseña actual es la
 * autorización de la operación, no un segundo login. El back responde 401 si no coincide,
 * 400 si la nueva no cumple la política o es igual a la actual, y en el éxito devuelve un
 * `access_token` NUEVO — el anterior deja de ser el que corresponde usar.
 */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val systemRole: String,
    val firstName: String? = null,
    val lastName: String? = null,
    /**
     * Contraseña generada por el sistema (terceros invitados, altas del admin): el usuario
     * tiene que elegir una propia antes de usar la app (S2/D3).
     *
     * El default es `false` a propósito: una respuesta vieja del back sin el campo no
     * puede encerrar a nadie en la pantalla de cambio. Quien decide es el servidor, y con
     * el campo ausente la respuesta honesta es "no hace falta cambiarla".
     */
    val mustChangePassword: Boolean = false,
)
