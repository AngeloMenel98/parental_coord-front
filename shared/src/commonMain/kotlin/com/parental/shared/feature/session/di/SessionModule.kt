package com.parental.shared.feature.session.di

import com.parental.shared.feature.session.data.SessionManager
import org.koin.dsl.module

val sessionModule = module {
    single { SessionManager() }
}
