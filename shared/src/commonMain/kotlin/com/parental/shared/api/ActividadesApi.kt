package com.parental.shared.api

import com.parental.shared.model.Actividad
import com.parental.shared.model.ActividadDetalle
import com.parental.shared.model.CreateActividadRequest
import com.parental.shared.model.UpdateActividadRequest
import com.parental.shared.model.UpdateStatusRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ActividadesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listActividades(
        token: String,
        bondId: String,
        type: String? = null,
    ): ApiResult<List<Actividad>> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/activities") {
                header("Authorization", "Bearer $token")
                type?.let { parameter("type", it) }
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

    suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> {
        return try {
            val response = client.get("$baseUrl/activities/$id") {
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

    suspend fun createActividad(
        token: String,
        bondId: String,
        request: CreateActividadRequest,
    ): ApiResult<Actividad> {
        return try {
            val response = client.post("$baseUrl/bonds/$bondId/activities") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
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

    suspend fun updateActividad(
        token: String,
        id: String,
        request: UpdateActividadRequest,
    ): ApiResult<Actividad> {
        return try {
            val response = client.put("$baseUrl/activities/$id") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
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

    suspend fun updateStatus(
        token: String,
        id: String,
        request: UpdateStatusRequest,
    ): ApiResult<Actividad> {
        return try {
            val response = client.patch("$baseUrl/activities/$id/status") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
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
