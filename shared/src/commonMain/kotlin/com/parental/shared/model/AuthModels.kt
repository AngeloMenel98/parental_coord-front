package com.parental.shared.model

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

@Serializable
data class User(
    val id: String,
    val email: String,
    val systemRole: String,
    val firstName: String? = null,
    val lastName: String? = null,
)
