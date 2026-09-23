package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeSummary
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

class HomeApi(
    private val client: io.ktor.client.HttpClient,
    private val baseUrl: String,
) {
    private val bondsApi = BondsApi(client, baseUrl)

    suspend fun getHomeSummary(token: String): ApiResult<HomeSummary> {
        return try {
            val bondsResult = bondsApi.getMyBonds(token)

            when (bondsResult) {
                is ApiResult.Success -> {
                    val bonds = bondsResult.data
                    val firstBond = bonds.firstOrNull()

                    val children = bonds.flatMap { bond ->
                        bond.children.map { child ->
                            ChildProfile(
                                id = child.id,
                                firstName = child.firstName,
                                lastName = child.lastName,
                                dateOfBirth = "",
                            )
                        }
                    }

                    ApiResult.Success(
                        HomeSummary(
                            children = children,
                            bondTitle = firstBond?.title,
                            bondType = firstBond?.agreementType,
                            memberCount = firstBond?.members?.size ?: 0,
                            bondId = firstBond?.id,
                        )
                    )
                }
                is ApiResult.Error -> bondsResult
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
        return try {
            val response = client.get("$baseUrl/activities/$bondId/compliance") {
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
            ApiResult.Error(0, "GET $baseUrl/activities/$bondId/compliance failed: ${e.message}")
        }
    }
}
