package com.parental.shared.core.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** Configuración JSON única para requests, responses y cuerpos de error. */
@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    encodeDefaults = true
    // Los campos opcionales en null no se envían (p. ej. dateOfBirth al crear un hijo).
    explicitNulls = false
}

/**
 * Construye el único HttpClient de la app. Las APIs usan rutas relativas
 * (`client.get("bonds")`) que se resuelven contra [baseUrl].
 */
fun createHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    isDebug: Boolean,
    tokenProvider: () -> String?,
    onUnauthorized: () -> Unit,
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    install(Logging) {
        val httpLog = Logger.withTag("HTTP")
        logger = object : io.ktor.client.plugins.logging.Logger {
            override fun log(message: String) = httpLog.d { message }
        }
        // HEADERS y no BODY: el body del login lleva la contraseña.
        level = if (isDebug) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(SessionAuth) {
        this.tokenProvider = tokenProvider
        this.onUnauthorized = onUnauthorized
    }
    defaultRequest {
        // Barra final obligatoria: "auth/login" debe resolver a <baseUrl>/auth/login.
        url(baseUrl.trimEnd('/') + "/")
    }
}
