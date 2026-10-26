package com.parental.shared.feature.notifications.di

import org.koin.dsl.module

// La app se compone a mano en `App.kt` (mismo criterio que `HomeModule`); este
// módulo existe para que el registro de features de `AppModule` siga siendo
// homogéneo y para que el cableado futuro tenga un lugar obvio.
val notificationsModule = module {
    // TODO: bind NotificationsViewModel, NotificationsApi
}
