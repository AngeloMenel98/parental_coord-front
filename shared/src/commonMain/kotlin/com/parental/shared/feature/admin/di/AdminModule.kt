package com.parental.shared.feature.admin.di

import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.admin.ui.AdminViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminModule = module {
    single { AdminApi(get()) }
    viewModelOf(::AdminViewModel)
}
