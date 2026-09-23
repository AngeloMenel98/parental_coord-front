package com.parental.shared.feature.bonds.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.bonds.domain.model.Vinculo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class BondsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> {
        return try {
            val response = client.get("$baseUrl/bonds") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                val body: List<Vinculo> = response.body()
                ApiResult.Success(body)
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}
