package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
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

open class HomeApi(
    private val client: io.ktor.client.HttpClient,
    private val baseUrl: String,
    private val bondsApi: BondsApi = BondsApi(client, baseUrl),
) {

    open suspend fun getHomeSummary(token: String): ApiResult<HomeSummary> {
        return try {
            when (val bondsResult = bondsApi.getMyBonds(token)) {
                is ApiResult.Success -> {
                    val bonds = bondsResult.data
                    val firstBond = bonds.firstOrNull()
                    val compliance = firstBond?.let {
                        when (val complianceResult = getCompliance(it.id, token)) {
                            is ApiResult.Success -> complianceResult.data
                            is ApiResult.Error -> null
                        }
                    }
                    ApiResult.Success(buildHomeSummary(bonds, compliance))
                }
                is ApiResult.Error -> bondsResult
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    open suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
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

/**
 * Construye un [HomeSummary] a partir de los vínculos y el cumplimiento opcional.
 *
 * Función pura y testable; la decisión de llamar a compliance (y de qué hacer con
 * un error) la toma [HomeApi.getHomeSummary].
 */
internal fun buildHomeSummary(
    bonds: List<Vinculo>,
    compliance: ComplianceResponse?,
): HomeSummary {
    val firstBond = bonds.firstOrNull()
    return HomeSummary(
        children = bonds.flatMap { bond ->
            bond.children.map { child ->
                ChildProfile(
                    id = child.id,
                    firstName = child.firstName,
                    lastName = child.lastName,
                    dateOfBirth = "",
                )
            }
        },
        memberCount = firstBond?.members?.size ?: 0,
        bondId = firstBond?.id,
        compliance = compliance,
    )
}
