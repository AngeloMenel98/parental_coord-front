package com.parental.shared.core.network

import com.parental.shared.platform.Platform
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.auth.data.remote.AuthApi
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.bonds.data.remote.BondsApi

object ApiClient {
    val httpClient = Platform.createHttpClient()

    val baseUrl: String get() = Platform.baseUrl

    fun authApi(): AuthApi = AuthApi(httpClient, baseUrl)

    fun homeApi(): HomeApi = HomeApi(httpClient, baseUrl)

    fun adminApi(): AdminApi = AdminApi(httpClient, baseUrl)

    fun bondsApi(): BondsApi = BondsApi(httpClient, baseUrl)

    fun categoriesApi(): CategoriesApi = CategoriesApi(httpClient, baseUrl)

    fun activitiesApi(): ActivitiesApi = ActivitiesApi(httpClient, baseUrl)
}
