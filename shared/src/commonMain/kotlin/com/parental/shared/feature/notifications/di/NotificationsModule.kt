package com.parental.shared.feature.notifications.di

import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import com.parental.shared.platform.Platform
import org.koin.dsl.module

// NotificationsApi mantiene la forma vieja de origin/main (cliente + baseUrl por
// instancia, token por llamada): se instancia sobre el HttpClient único de Koin.
val notificationsModule = module {
    single { NotificationsApi(get(), Platform.baseUrl) }
}
