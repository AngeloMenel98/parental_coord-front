package com.parental.shared.feature.admin.di

import com.parental.shared.feature.admin.data.remote.AdminApi
import org.koin.dsl.module

// AdminViewModel sigue siendo una clase común con `destroy()` (igual que en
// origin/main): App.kt la construye a mano con `remember()` + `DisposableEffect`,
// así que NO se registra como ViewModel de Koin.
val adminModule = module {
    single { AdminApi(get()) }
}
