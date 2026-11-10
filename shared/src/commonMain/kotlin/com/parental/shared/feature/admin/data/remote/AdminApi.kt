package com.parental.shared.feature.admin.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminUser
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /admin/bonds`.
 *
 * A `mapOf("title" to …, "agreementType" to …, "userIds" to listOf(…))` infers
 * `Map<String, Any>`, which has no serializer: Ktor falls back to the
 * polymorphic path and throws
 * `serializing collection of different types is not yet supported`.
 * A concrete DTO keeps every field typed.
 */
@Serializable
internal data class CreateBondRequest(
    val title: String,
    val agreementType: String,
    val userIds: List<String>,
)

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
                    CreateBondRequest(
                        title = title,
                        agreementType = agreementType,
                        userIds = userIds,
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
