package com.parental.shared.core.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

class SessionAuthConfig {
    /** Token vigente; se consulta en cada request (no se cachea). */
    var tokenProvider: () -> String? = { null }

    /** Se invoca cuando un request autenticado recibe 401 (token vencido o revocado). */
    var onUnauthorized: () -> Unit = {}
}

/**
 * Agrega `Authorization: Bearer <token>` a cada request a partir de la sesión.
 *
 * Se usa en lugar del plugin `Auth` de Ktor porque su BearerAuthProvider cachea
 * el token tras la primera carga (habría que invalidarlo en cada login/logout)
 * y el backend no expone un endpoint de refresh.
 */
val SessionAuth = createClientPlugin("SessionAuth", ::SessionAuthConfig) {
    val tokenProvider = pluginConfig.tokenProvider
    val onUnauthorized = pluginConfig.onUnauthorized

    onRequest { request, _ ->
        if (request.headers[HttpHeaders.Authorization] == null) {
            tokenProvider()?.let { request.bearerAuth(it) }
        }
    }

    onResponse { response ->
        // Solo requests que llevaban token: un 401 del login (credenciales
        // incorrectas) no debe cerrar la sesión.
        if (response.status == HttpStatusCode.Unauthorized &&
            response.call.request.headers[HttpHeaders.Authorization] != null
        ) {
            onUnauthorized()
        }
    }
}
