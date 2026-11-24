package com.parental.shared.core.di

import com.parental.shared.core.network.networkModule
import com.parental.shared.feature.activities.di.activitiesModule
import com.parental.shared.feature.auth.di.authModule
import com.parental.shared.feature.admin.di.adminModule
import com.parental.shared.feature.expenses.di.expensesModule
import com.parental.shared.feature.home.di.homeModule
import com.parental.shared.feature.bonds.di.bondsModule
import com.parental.shared.feature.notifications.di.notificationsModule
import com.parental.shared.feature.session.di.sessionModule
import com.parental.shared.feature.thirdparty.di.thirdPartiesModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    includes(
        networkModule,
        sessionModule,
        bondsModule,
        authModule,
        homeModule,
        activitiesModule,
        adminModule,
        // Features que llegaron por origin/main y mantienen la forma vieja de las
        // APIs (cliente + baseUrl por instancia, token por llamada): se registran
        // igual, con el HttpClient único de Koin.
        notificationsModule,
        expensesModule,
        thirdPartiesModule,
    )
}

/**
 * Arranca Koin una sola vez por proceso. Android lo llama desde
 * `ParentalApplication.onCreate()`; un host iOS debe llamarlo antes de crear la UI.
 * La firma no expone tipos de Koin: los módulos host no dependen de Koin.
 */
fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
