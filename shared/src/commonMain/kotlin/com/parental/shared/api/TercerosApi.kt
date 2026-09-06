package com.parental.shared.api

import com.parental.shared.model.CreateTerceroRequest
import com.parental.shared.model.RevokeTerceroRequest
import com.parental.shared.model.Tercero
import com.parental.shared.model.TerceroDetalle
import com.parental.shared.model.UpdateTerceroRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TercerosApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listTerceros(
        token: String,
        bondId: String,
        status: String? = null,
    ): ApiResult<List<Tercero>> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/third-parties") {
                header("Authorization", "Bearer $token")
                status?.let { parameter("status", it) }
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

    suspend fun getTercero(token: String, id: String): ApiResult<TerceroDetalle> {
        return try {
            val response = client.get("$baseUrl/third-parties/$id") {
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

    suspend fun createTercero(
        token: String,
        bondId: String,
        request: CreateTerceroRequest,
    ): ApiResult<Tercero> {
        return try {
            val response = client.post("$baseUrl/bonds/$bondId/third-parties") {
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

    suspend fun updateTercero(
        token: String,
        id: String,
        request: UpdateTerceroRequest,
    ): ApiResult<Tercero> {
        return try {
            val response = client.put("$baseUrl/third-parties/$id") {
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

    suspend fun revokeTercero(
        token: String,
        id: String,
        request: RevokeTerceroRequest = RevokeTerceroRequest(),
    ): ApiResult<Tercero> {
        return try {
            val response = client.patch("$baseUrl/third-parties/$id/revoke") {
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
