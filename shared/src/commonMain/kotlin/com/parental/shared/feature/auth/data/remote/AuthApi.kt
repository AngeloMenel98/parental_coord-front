package com.parental.shared.feature.auth.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.LoginRequest
import com.parental.shared.feature.auth.domain.model.RegisterRequest
import com.parental.shared.feature.auth.domain.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApi(
    private val client: HttpClient,
) {
    suspend fun login(email: String, password: String): ApiResult<AuthResponse> = safeApiCall {
        client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<AuthResponse> = safeApiCall {
        client.post("auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, firstName, lastName))
        }
    }

    /** GET /auth/me — usuario del token de la sesión actual. */
    suspend fun me(): ApiResult<User> = safeApiCall {
        client.get("auth/me")
    }
}
