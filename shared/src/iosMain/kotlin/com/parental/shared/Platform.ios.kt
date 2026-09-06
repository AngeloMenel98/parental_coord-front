package com.parental.shared

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults
import platform.Foundation.standardUserDefaults

/**
 * iOS actual implementation using NSUserDefaults for persistent storage
 * and Darwin engine for Ktor HTTP client.
 */
actual object Platform {
    private const val PREFS_PREFIX = "parental_coord_"

    // Base URL for iOS: adjust for your environment
    // - iOS Simulator: http://127.0.0.1:3000/api (or http://localhost:3000/api)
    // - Real device: use your machine's IP (e.g., http://192.168.1.x:3000/api)
    // - Production: https://api.yourdomain.com/api
    actual val baseUrl: String = "http://127.0.0.1:3000/api"

    actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            })
        }
    }

    actual fun saveString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = PREFS_PREFIX + key)
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    actual fun loadString(key: String): String? {
        return NSUserDefaults.standardUserDefaults.objectForKey(PREFS_PREFIX + key) as? String
    }

    actual fun remove(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(PREFS_PREFIX + key)
        NSUserDefaults.standardUserDefaults.synchronize()
    }
}
