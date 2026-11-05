package com.parental.shared.feature.thirdparty.di

import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.platform.Platform
import org.koin.dsl.module

// ThirdPartiesApi mantiene la forma vieja de origin/main (cliente + baseUrl por
// instancia, token por llamada): se instancia sobre el HttpClient único de Koin.
val thirdPartiesModule = module {
    single { ThirdPartiesApi(get(), Platform.baseUrl) }
}
