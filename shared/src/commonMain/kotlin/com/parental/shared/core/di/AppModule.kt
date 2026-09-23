package com.parental.shared.core.di

import com.parental.shared.feature.activities.di.activitiesModule
import com.parental.shared.feature.auth.di.authModule
import com.parental.shared.feature.admin.di.adminModule
import com.parental.shared.feature.home.di.homeModule
import com.parental.shared.feature.bonds.di.bondsModule
import com.parental.shared.feature.session.di.sessionModule
import org.koin.dsl.module

val appModule = module {
    includes(
        activitiesModule,
        authModule,
        adminModule,
        homeModule,
        bondsModule,
        sessionModule,
    )
}
