package com.parental.shared.feature.auth.di

import com.parental.shared.feature.auth.data.remote.AuthApi
import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.auth.ui.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    single { AuthApi(get()) }
    single<IAuthRepository> { AuthRepository(get()) }
    viewModelOf(::LoginViewModel)
}
