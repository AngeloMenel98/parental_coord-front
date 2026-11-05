package com.parental.shared.feature.activities.di

import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.ui.ActividadesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val activitiesModule = module {
    single { ActivitiesApi(get()) }
    viewModelOf(::ActividadesViewModel)
}
