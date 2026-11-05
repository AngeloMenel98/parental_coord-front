package com.parental.shared.feature.admin.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondHealth
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminBondTrend
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminReport
import com.parental.shared.feature.admin.domain.model.AdminReportRequest
import com.parental.shared.feature.admin.domain.model.AdminUser
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

/**
 * Body of `PATCH /admin/users/:id` and `PATCH /admin/bonds/:id`.
 *
 * Built by hand instead of a `@Serializable` class so the exact bytes are
 * assertable without depending on the serializer's null/default policy.
 */
internal fun buildSetActiveBody(isActive: Boolean): JsonObject = buildJsonObject {
    put("isActive", isActive)
}

/** Una sola fuente del path, para que update y delete no se desincronicen. */
internal fun adminChildPath(baseUrl: String, childId: String): String =
    "$baseUrl/admin/children/$childId"

/**
 * Body of `PATCH /admin/children/:id`.
 *
 * Only the fields the admin actually changed are sent: a field left untouched
 * must not overwrite what is stored. `clearDateOfBirth` is what emits
 * `"dateOfBirth": null` — sending an empty string would be rejected by the
 * backend's `@IsDateString`.
 */
internal fun buildChildPatchBody(
    firstName: String?,
    lastName: String?,
    dateOfBirth: String?,
    clearDateOfBirth: Boolean,
): JsonObject = buildJsonObject {
    firstName?.let { put("firstName", it) }
    lastName?.let { put("lastName", it) }
    when {
        clearDateOfBirth -> put("dateOfBirth", JsonNull)
        dateOfBirth != null -> put("dateOfBirth", dateOfBirth)
    }
}

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

    /** F1 · S1 — activa o desactiva una cuenta. Devuelve la fila que la lista muestra. */
    suspend fun setUserActive(
        token: String,
        userId: String,
        isActive: Boolean,
    ): ApiResult<AdminUser> =
        patch("$baseUrl/admin/users/$userId", token, buildSetActiveBody(isActive))

    /** F1 · S2 — activa o desactiva un vínculo completo. */
    suspend fun setBondActive(
        token: String,
        bondId: String,
        isActive: Boolean,
    ): ApiResult<AdminBond> =
        patch("$baseUrl/admin/bonds/$bondId", token, buildSetActiveBody(isActive))

    private suspend inline fun <reified T> patch(
        url: String,
        token: String,
        body: JsonObject,
    ): ApiResult<T> {
        return try {
            val response = client.patch(url) {
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

    /** F1 · S3 — edición parcial de un hijo; `clearDateOfBirth` manda `null`. */
    suspend fun updateChild(
        token: String,
        childId: String,
        firstName: String? = null,
        lastName: String? = null,
        dateOfBirth: String? = null,
        clearDateOfBirth: Boolean = false,
    ): ApiResult<AdminChild> = patch(
        adminChildPath(baseUrl, childId),
        token,
        buildChildPatchBody(firstName, lastName, dateOfBirth, clearDateOfBirth),
    )

    /** F1 · S3 — 204 sin cuerpo. */
    suspend fun deleteChild(token: String, childId: String): ApiResult<Unit> {
        return try {
            val response = client.delete(adminChildPath(baseUrl, childId)) {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    // ── F2 · Reportes ────────────────────────────────────────────────

    /** S5 — el reporte JSON del período. */
    suspend fun getBondReport(
        token: String,
        bondId: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<AdminBondReport> {
        return try {
            val response = client.get("$baseUrl/admin/bonds/$bondId/report") {
                header("Authorization", "Bearer $token")
                parameter("dateFrom", dateFrom)
                parameter("dateTo", dateTo)
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

    /** S7 — encola el PDF; el backend responde 202 con la fila en `requested`. */
    suspend fun requestReportPdf(
        token: String,
        bondId: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<AdminReportRequest> {
        return try {
            val response = client.post("$baseUrl/admin/bonds/$bondId/reports") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("dateFrom", dateFrom)
                    put("dateTo", dateTo)
                })
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

    /** S7 — estado del PDF; `fileUrl` viene firmada sólo cuando está completado. */
    suspend fun getReport(token: String, reportId: String): ApiResult<AdminReport> {
        return try {
            val response = client.get("$baseUrl/admin/reports/$reportId") {
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

    /**
     * F3 · una sola llamada para las tarjetas de la lista.
     *
     * La salud NO usa el período del reporte: siempre mira el mes en curso, así
     * cambiar el rango del reporte no altera lo que dice la lista.
     */
    suspend fun getBondsHealth(
        token: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<List<AdminBondHealth>> {
        return try {
            val response = client.get("$baseUrl/admin/bonds/health") {
                header("Authorization", "Bearer $token")
                parameter("dateFrom", dateFrom)
                parameter("dateTo", dateTo)
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

    /** F3 · serie de cumplimiento del detalle del vínculo. */
    suspend fun getBondTrend(
        token: String,
        bondId: String,
        months: Int = 6,
    ): ApiResult<AdminBondTrend> {
        return try {
            val response = client.get("$baseUrl/admin/bonds/$bondId/trend") {
                header("Authorization", "Bearer $token")
                parameter("months", months)
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
