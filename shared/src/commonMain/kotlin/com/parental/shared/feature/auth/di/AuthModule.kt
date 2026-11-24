package com.parental.shared.feature.auth.di

import com.parental.shared.feature.auth.data.remote.AuthApi
import com.parental.shared.feature.auth.data.repository.AuthRepository
import com.parental.shared.feature.auth.domain.repository.IAuthRepository
import com.parental.shared.feature.auth.ui.ChangePasswordViewModel
import com.parental.shared.feature.auth.ui.LoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    single { AuthApi(get()) }
    single<IAuthRepository> { AuthRepository(get()) }
    viewModelOf(::LoginViewModel)
    // S8 · CP3: la hoja de cambio obligatorio se monta con `koinViewModel` (sobre el
    // `ViewModelStoreOwner` de la sesión), así que entra por Koin; `dispatcher` conserva
    // su default y no se declara.
    viewModel { ChangePasswordViewModel(authRepository = get(), sessionManager = get()) }
}
