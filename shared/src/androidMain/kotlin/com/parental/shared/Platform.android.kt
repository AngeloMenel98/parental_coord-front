package com.parental.shared

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Android actual implementation using SharedPreferences for persistent storage
 * and OkHttp engine for Ktor HTTP client.
 */
actual object Platform {
    private const val PREFS_NAME = "parental_coordination_prefs"

    // Base URL for Android: emulator uses 10.0.2.2, real device should use production URL
    actual val baseUrl: String = "http://10.0.2.2:3000/api"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize with Android Context. Must be called before any storage operations.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            })
        }
    }

    actual fun saveString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    actual fun loadString(key: String): String? {
        return prefs?.getString(key, null)
    }

    actual fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }
}
