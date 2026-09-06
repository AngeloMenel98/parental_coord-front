package com.parental.shared.api

import com.parental.shared.Platform

object ApiClient {
    val httpClient = Platform.createHttpClient()

    val baseUrl: String get() = Platform.baseUrl

    fun authApi(): AuthApi = AuthApi(httpClient, baseUrl)

    fun homeApi(): HomeApi = HomeApi(httpClient, baseUrl)

    fun adminApi(): AdminApi = AdminApi(httpClient, baseUrl)

    fun actividadesApi(): ActividadesApi = ActividadesApi(httpClient, baseUrl)

    fun gastosApi(): GastosApi = GastosApi(httpClient, baseUrl)

    fun tercerosApi(): TercerosApi = TercerosApi(httpClient, baseUrl)

    fun bondsApi(): BondsApi = BondsApi(httpClient, baseUrl)
}
