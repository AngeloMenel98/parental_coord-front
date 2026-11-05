package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeSummary
import io.ktor.client.*
import io.ktor.client.request.*

class HomeApi(
    private val client: HttpClient,
    private val bondsApi: BondsApi,
) {
    suspend fun getHomeSummary(): ApiResult<HomeSummary> {
        return when (val bondsResult = bondsApi.getMyBonds()) {
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
    }

    suspend fun getCompliance(bondId: String): ApiResult<ComplianceResponse> = safeApiCall {
        client.get("activities/$bondId/compliance")
    }
}
