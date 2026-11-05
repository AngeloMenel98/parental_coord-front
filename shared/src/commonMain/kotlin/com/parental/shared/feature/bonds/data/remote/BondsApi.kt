package com.parental.shared.feature.bonds.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.feature.bonds.domain.model.Vinculo
import io.ktor.client.*
import io.ktor.client.request.*

class BondsApi(
    private val client: HttpClient,
) {
    suspend fun getMyBonds(): ApiResult<List<Vinculo>> = safeApiCall {
        client.get("bonds")
    }
}
