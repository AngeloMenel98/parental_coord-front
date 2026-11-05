package com.parental.shared.feature.expenses.di

import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.platform.Platform
import org.koin.dsl.module

// ExpensesApi mantiene la forma vieja de origin/main (cliente + baseUrl por
// instancia, token por llamada): se instancia sobre el HttpClient único de Koin.
val expensesModule = module {
    single { ExpensesApi(get(), Platform.baseUrl) }
}
