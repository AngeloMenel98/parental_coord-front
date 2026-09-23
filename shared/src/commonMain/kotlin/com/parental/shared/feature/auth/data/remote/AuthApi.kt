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
    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
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
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
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
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}
