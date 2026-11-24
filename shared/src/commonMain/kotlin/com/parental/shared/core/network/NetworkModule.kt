package com.parental.shared.core.network

import com.parental.shared.feature.session.data.SessionManager
import com.parental.shared.platform.Platform
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Infraestructura de red compartida: un único [HttpClient] para toda la app.
 * El token se toma de [SessionManager] en cada request; un 401 dispara el logout.
 */
val networkModule = module {
    single<HttpClient> {
        val sessionManager = get<SessionManager>()
        createHttpClient(
            engine = Platform.createHttpEngine(),
            baseUrl = Platform.baseUrl,
            isDebug = Platform.isDebug,
            tokenProvider = { sessionManager.token },
            onUnauthorized = sessionManager::notifySessionExpired,
        )
    }
    single { CategoriesApi(get()) }
}
