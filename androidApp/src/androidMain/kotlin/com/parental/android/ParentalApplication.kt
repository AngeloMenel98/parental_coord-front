package com.parental.android

import android.app.Application
import com.parental.shared.core.di.initKoin
import com.parental.shared.platform.Platform

class ParentalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Storage primero: SessionManager se restaura al resolverse desde Koin.
        Platform.init(
            context = this,
            baseUrl = BuildConfig.API_BASE_URL,
            isDebug = BuildConfig.DEBUG,
        )
        initKoin()
    }
}
