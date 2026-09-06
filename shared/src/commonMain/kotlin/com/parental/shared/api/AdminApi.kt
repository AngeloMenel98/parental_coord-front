package com.parental.shared.api

import com.parental.shared.model.AdminBond
import com.parental.shared.model.AdminChild
import com.parental.shared.model.AdminUser
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API layer for Admin screen — manages users, bonds, and children.
 */
class AdminApi(
    private val client: io.ktor.client.HttpClient,
    private val baseUrl: String,
) {
    // ── Users ────────────────────────────────────────────────────────

    suspend fun listUsers(token: String): ApiResult<List<AdminUser>> {
        return try {
            val response = client.get("$baseUrl/admin/users") {
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

    suspend fun createUser(
        token: String,
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<AdminUser> {
        return try {
            val response = client.post("$baseUrl/admin/users") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "email" to email,
                        "password" to password,
                        "firstName" to firstName,
                        "lastName" to lastName,
                    )
                )
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

    // ── Bonds ────────────────────────────────────────────────────────

    suspend fun listBonds(token: String): ApiResult<List<AdminBond>> {
        return try {
            val response = client.get("$baseUrl/admin/bonds") {
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

    suspend fun createBond(
        token: String,
        title: String,
        agreementType: String,
        userIds: List<String>,
    ): ApiResult<AdminBond> {
        return try {
            val response = client.post("$baseUrl/admin/bonds") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    mapOf(
                        "title" to title,
                        "agreementType" to agreementType,
                        "userIds" to userIds,
                    )
                )
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

    // ── Children ─────────────────────────────────────────────────────

    suspend fun listBondChildren(token: String, bondId: String): ApiResult<List<AdminChild>> {
        return try {
            val response = client.get("$baseUrl/admin/bonds/$bondId/children") {
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

    suspend fun addChildToBond(
        token: String,
        bondId: String,
        firstName: String,
        lastName: String,
        dateOfBirth: String? = null,
    ): ApiResult<AdminChild> {
        return try {
            val body = mutableMapOf<String, Any>(
                "firstName" to firstName,
                "lastName" to lastName,
            )
            if (dateOfBirth != null) body["dateOfBirth"] = dateOfBirth

            val response = client.post("$baseUrl/admin/bonds/$bondId/children") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
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
