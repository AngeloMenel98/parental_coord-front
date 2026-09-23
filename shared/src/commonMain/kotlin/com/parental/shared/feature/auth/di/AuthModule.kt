package com.parental.shared.feature.auth.di

import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import org.koin.dsl.module

val authModule = module {
    single<IAuthRepository> { AuthRepository() }
}
