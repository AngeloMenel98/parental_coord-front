package com.parental.shared.api

import com.parental.shared.model.ApproveGastoRequest
import com.parental.shared.model.CreateGastoRequest
import com.parental.shared.model.DisputeGastoRequest
import com.parental.shared.model.Gasto
import com.parental.shared.model.GastoDetalle
import com.parental.shared.model.GastoSummary
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class GastosApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listGastos(
        token: String,
        bondId: String,
        status: String? = null,
    ): ApiResult<List<Gasto>> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/expenses") {
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

    suspend fun getGasto(token: String, id: String): ApiResult<GastoDetalle> {
        return try {
            val response = client.get("$baseUrl/expenses/$id") {
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

    suspend fun getSummary(
        token: String,
        bondId: String,
    ): ApiResult<GastoSummary> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/expenses/summary") {
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

    suspend fun createGasto(
        token: String,
        bondId: String,
        request: CreateGastoRequest,
    ): ApiResult<Gasto> {
        return try {
            val response = client.post("$baseUrl/bonds/$bondId/expenses") {
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

    suspend fun approveGasto(
        token: String,
        id: String,
        request: ApproveGastoRequest = ApproveGastoRequest(),
    ): ApiResult<Gasto> {
        return try {
            val response = client.patch("$baseUrl/expenses/$id/approve") {
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

    suspend fun disputeGasto(
        token: String,
        id: String,
        request: DisputeGastoRequest,
    ): ApiResult<Gasto> {
        return try {
            val response = client.patch("$baseUrl/expenses/$id/dispute") {
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
