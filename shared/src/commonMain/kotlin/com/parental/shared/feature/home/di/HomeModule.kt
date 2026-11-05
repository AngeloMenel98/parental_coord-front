package com.parental.shared.feature.home.di

import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.home.ui.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    single { HomeApi(get(), get()) }
    viewModelOf(::HomeViewModel)
}
