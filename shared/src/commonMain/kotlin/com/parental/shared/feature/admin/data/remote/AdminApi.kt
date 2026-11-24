package com.parental.shared.feature.admin.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondHealth
import com.parental.shared.feature.admin.domain.model.AdminBondActivities
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
/** B5 · un hijo que nace junto con el vínculo. */
@Serializable
data class CreateBondChildRequest(
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String? = null,
)

@Serializable
internal data class CreateBondRequest(
    val title: String,
    val agreementType: String,
    val userIds: List<String>,
    val children: List<CreateBondChildRequest>,
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
internal fun adminChildPath(childId: String): String =
    "admin/children/$childId"

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
) {
    // ── Users ────────────────────────────────────────────────────────

    suspend fun listUsers(): ApiResult<List<AdminUser>> {
        return try {
            val response = client.get("admin/users") {
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
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): ApiResult<AdminUser> {
        return try {
            val response = client.post("admin/users") {
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

    suspend fun listBonds(): ApiResult<List<AdminBond>> {
        return try {
            val response = client.get("admin/bonds") {
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
        title: String,
        agreementType: String,
        userIds: List<String>,
        children: List<CreateBondChildRequest>,
    ): ApiResult<AdminBond> {
        return try {
            val response = client.post("admin/bonds") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateBondRequest(
                        title = title,
                        agreementType = agreementType,
                        userIds = userIds,
                        children = children,
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
        userId: String,
        isActive: Boolean,
    ): ApiResult<AdminUser> =
        patch("admin/users/$userId", buildSetActiveBody(isActive))

    /** F1 · S2 — activa o desactiva un vínculo completo. */
    suspend fun setBondActive(
        bondId: String,
        isActive: Boolean,
    ): ApiResult<AdminBond> =
        patch("admin/bonds/$bondId", buildSetActiveBody(isActive))

    private suspend inline fun <reified T> patch(
        url: String,
        body: JsonObject,
    ): ApiResult<T> {
        return try {
            val response = client.patch(url) {
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

    suspend fun listBondChildren(bondId: String): ApiResult<List<AdminChild>> {
        return try {
            val response = client.get("admin/bonds/$bondId/children") {
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

            val response = client.post("admin/bonds/$bondId/children") {
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
        childId: String,
        firstName: String? = null,
        lastName: String? = null,
        dateOfBirth: String? = null,
        clearDateOfBirth: Boolean = false,
    ): ApiResult<AdminChild> = patch(
        adminChildPath( childId),
        buildChildPatchBody(firstName, lastName, dateOfBirth, clearDateOfBirth),
    )

    /** F1 · S3 — 204 sin cuerpo. */
    suspend fun deleteChild(childId: String): ApiResult<Unit> {
        return try {
            val response = client.delete(adminChildPath( childId)) {
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
        bondId: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<AdminBondReport> {
        return try {
            val response = client.get("admin/bonds/$bondId/report") {
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

    /**
     * F7 · S21 — las actividades del período para el detalle del reporte.
     *
     * Va por una ruta admin y no por `GET /activities/bond/:bondId` porque aquélla es
     * member-scoped: un admin que no es miembro del vínculo recibiría 403.
     */
    suspend fun getBondActivities(
        token: String,
        bondId: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<AdminBondActivities> {
        return try {
            val response = client.get("admin/bonds/$bondId/activities") {
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
        bondId: String,
        dateFrom: String,
        dateTo: String,
    ): ApiResult<AdminReportRequest> {
        return try {
            val response = client.post("admin/bonds/$bondId/reports") {
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
    suspend fun getReport(reportId: String): ApiResult<AdminReport> {
        return try {
            val response = client.get("admin/reports/$reportId") {
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
        dateFrom: String,
        dateTo: String,
    ): ApiResult<List<AdminBondHealth>> {
        return try {
            val response = client.get("admin/bonds/health") {
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
        bondId: String,
        months: Int = 6,
    ): ApiResult<AdminBondTrend> {
        return try {
            val response = client.get("admin/bonds/$bondId/trend") {
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
