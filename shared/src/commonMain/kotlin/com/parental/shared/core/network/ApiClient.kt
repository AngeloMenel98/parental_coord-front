package com.parental.shared.core.network

import com.parental.shared.platform.Platform
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.auth.data.remote.AuthApi
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi

object ApiClient {
    val httpClient = Platform.createHttpClient()

    val baseUrl: String get() = Platform.baseUrl

    fun authApi(): AuthApi = AuthApi(httpClient, baseUrl)

    fun homeApi(): HomeApi = HomeApi(httpClient, baseUrl)

    fun adminApi(): AdminApi = AdminApi(httpClient, baseUrl)

    fun bondsApi(): BondsApi = BondsApi(httpClient, baseUrl)

    fun categoriesApi(): CategoriesApi = CategoriesApi(httpClient, baseUrl)

    fun activitiesApi(): ActivitiesApi = ActivitiesApi(httpClient, baseUrl)

    fun expensesApi(): ExpensesApi = ExpensesApi(httpClient, baseUrl)

    fun notificationsApi(): NotificationsApi = NotificationsApi(httpClient, baseUrl)

    fun thirdPartiesApi(): ThirdPartiesApi = ThirdPartiesApi(httpClient, baseUrl)
}
