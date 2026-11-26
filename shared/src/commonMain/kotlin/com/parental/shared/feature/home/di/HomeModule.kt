package com.parental.shared.feature.home.di

import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.home.ui.HomeViewModel
import com.parental.shared.feature.session.data.SessionManager
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// HomeApi no lleva token (viaja en el bearer del shared HttpClient), pero ExpensesApi
// mantiene por ahora la forma vieja de origin/main (token por llamada), así que se le
// pasa un provider consultado en cada request.
val homeModule = module {
    single {
        HomeApi(
            bondsApi = get(),
            activitiesApi = get(),
            expensesApi = get(),
            categoriesApi = get(),
            complianceClient = get(),
            tokenProvider = { get<SessionManager>().token.orEmpty() },
        )
    }
    viewModelOf(::HomeViewModel)
}
