package com.parental.shared.feature.session.di

import com.parental.shared.feature.session.data.SessionManager
import org.koin.dsl.module

val sessionModule = module {
    // Se restaura al crearse: el primer frame ya conoce la sesión persistida.
    single { SessionManager().apply { restoreFromStorage() } }
}
