package com.parental.shared.feature.activities.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ConfirmAssignmentResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val message: String? = null,
    val error: String? = null,
)

class ActivitiesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /activities/bond/:bondId — lista de actividades del vínculo activo,
     * ordenadas scheduledStart → deadline → createdAt (nulls last).
     */
    suspend fun listActividades(
        token: String,
        bondId: String,
    ): ApiResult<List<Actividad>> {
        return try {
            val response = client.get("$baseUrl/activities/bond/$bondId") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                val message = runCatching { response.body<ApiErrorBody>() }
                    .getOrNull()
                    ?.let { it.message ?: it.error }
                    ?: response.status.description
                ApiResult.Error(response.status.value, message)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/bond/$bondId failed: ${e.message}")
        }
    }

    /**
     * GET /activities/:id — detalle completo de una actividad.
     */
    suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> {
        return try {
            val response = client.get("$baseUrl/activities/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                val message = runCatching { response.body<ApiErrorBody>() }
                    .getOrNull()
                    ?.let { it.message ?: it.error }
                    ?: response.status.description
                ApiResult.Error(response.status.value, message)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/$id failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/confirm — confirma la asignación (flag-only,
     * idempotente). Sin cuerpo. Errores: ApiErrorBody (message ?? error)
     * cuando el backend responde JSON; si no, status.description.
     */
    suspend fun confirmAssignment(
        token: String,
        id: String,
    ): ApiResult<ConfirmAssignmentResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/confirm") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                val message = runCatching { response.body<ApiErrorBody>() }
                    .getOrNull()
                    ?.let { it.message ?: it.error }
                    ?: response.status.description
                ApiResult.Error(response.status.value, message)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/confirm failed: ${e.message}")
        }
    }
}