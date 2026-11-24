package com.parental.shared.feature.activities.di

import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import org.koin.dsl.module

// ActividadesViewModel sigue siendo una clase común con `destroy()` (igual que en
// origin/main): App.kt la construye a mano con `remember()` + `DisposableEffect`,
// así que NO se registra como ViewModel de Koin.
val activitiesModule = module {
    single { ActivitiesApi(get()) }
}
