package com.parental.shared.feature.bonds.di

import com.parental.shared.feature.bonds.data.remote.BondsApi
import org.koin.dsl.module

val bondsModule = module {
    single { BondsApi(get()) }
}
