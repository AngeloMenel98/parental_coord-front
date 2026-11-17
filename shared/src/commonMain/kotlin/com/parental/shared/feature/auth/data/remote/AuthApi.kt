package com.parental.shared.feature.auth.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.auth.domain.model.AuthResponse
import com.parental.shared.feature.auth.domain.model.LoginRequest
import com.parental.shared.feature.auth.domain.model.RegisterRequest
import com.parental.shared.feature.auth.domain.model.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * Mensaje de error que nunca queda vacío.
     *
     * Un status que Ktor no conoce (los `52x` de Cloudflare, por ejemplo) llega con
     * `description` vacío; `ApiResult.Error` acepta el string vacío y entonces la UI
     * dibujaba el cartel de error sin texto, imposible de diagnosticar desde el
     * teléfono.
     */
    private fun httpErrorLabel(status: HttpStatusCode): String {
        val description = status.description.trim()
        return if (description.isNotEmpty()) description else "HTTP ${status.value}"
    }

    /** Igual que [httpErrorLabel] para el camino de excepción: `toString()` siempre trae el tipo. */
    private fun failureLabel(e: Exception): String =
        e.message?.trim().orEmpty().ifEmpty { e.toString() }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, httpErrorLabel(response.status))
            }
        } catch (e: Exception) {
            ApiResult.Error(0, failureLabel(e))
        }
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, password, firstName, lastName))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, httpErrorLabel(response.status))
            }
        } catch (e: Exception) {
            ApiResult.Error(0, failureLabel(e))
        }
    }

    suspend fun me(token: String): ApiResult<User> {
        return try {
            val response = client.get("$baseUrl/auth/me") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, httpErrorLabel(response.status))
            }
        } catch (e: Exception) {
            ApiResult.Error(0, failureLabel(e))
        }
    }
}
