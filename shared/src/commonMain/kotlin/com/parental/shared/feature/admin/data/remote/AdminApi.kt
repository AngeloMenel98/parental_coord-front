package com.parental.shared.feature.admin.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminUser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// Bodies tipados: un Map<String, Any> con valores de distinto tipo no es
// serializable con kotlinx.serialization (createBond fallaba siempre).

@Serializable
internal data class CreateUserRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
)

@Serializable
internal data class CreateBondRequest(
    val title: String,
    val agreementType: String,
    val userIds: List<String>,
)

@Serializable
internal data class AddChildRequest(
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String? = null,
)

class AdminApi(
    private val client: HttpClient,
) {
    // ── Users ────────────────────────────────────────────────────────

    suspend fun listUsers(): ApiResult<List<AdminUser>> = safeApiCall {
        client.get("admin/users")
    }

    suspend fun createUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<AdminUser> = safeApiCall {
        client.post("admin/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(email, password, firstName, lastName))
        }
    }

    // ── Bonds ────────────────────────────────────────────────────────

    suspend fun listBonds(): ApiResult<List<AdminBond>> = safeApiCall {
        client.get("admin/bonds")
    }

    suspend fun createBond(
        title: String,
        agreementType: String,
        userIds: List<String>,
    ): ApiResult<AdminBond> = safeApiCall {
        client.post("admin/bonds") {
            contentType(ContentType.Application.Json)
            setBody(CreateBondRequest(title, agreementType, userIds))
        }
    }

    // ── Children ─────────────────────────────────────────────────────

    suspend fun listBondChildren(bondId: String): ApiResult<List<AdminChild>> = safeApiCall {
        client.get("admin/bonds/$bondId/children")
    }

    suspend fun addChildToBond(
        bondId: String,
        firstName: String,
        lastName: String,
        dateOfBirth: String? = null,
    ): ApiResult<AdminChild> = safeApiCall {
        client.post("admin/bonds/$bondId/children") {
            contentType(ContentType.Application.Json)
            // dateOfBirth null se omite del JSON (ApiJson.explicitNulls = false).
            setBody(AddChildRequest(firstName, lastName, dateOfBirth))
        }
    }
}
