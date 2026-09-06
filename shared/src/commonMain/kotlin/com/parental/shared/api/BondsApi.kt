package com.parental.shared.api

import co.touchlab.kermit.Logger

import com.parental.shared.model.Vinculo
import com.parental.shared.model.VinculoDetalle
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

private const val TAG = "BONDS_API"

class BondsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> {
        return try {
            Logger.d(TAG) { "GET $baseUrl/bonds" }
            val response = client.get("$baseUrl/bonds") {
                header("Authorization", "Bearer $token")
            }
            Logger.d(TAG) {"Response status: ${response.status}"}
            if (response.status.isSuccess()) {
                val body: List<Vinculo> = response.body()
                //Log.d(ggAG, "Response body: ${body.size} bonds")
                ApiResult.Success(body)
            } else {
                val body = response.body<String>()
                //Log.e(TAG, "Error response: $body")
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            //Log.e(TAG, "Exception: ${e.message}", e)
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    suspend fun getBondSummary(token: String, bondId: String): ApiResult<VinculoDetalle> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId") {
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

    suspend fun getChildren(token: String, bondId: String): ApiResult<List<com.parental.shared.model.VinculoChild>> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/children") {
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

    suspend fun getCompliance(token: String, bondId: String): ApiResult<com.parental.shared.model.VinculoCompliance> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/compliance") {
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
