package com.parental.shared.platform

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Android actual implementation using SharedPreferences for persistent storage
 * and OkHttp engine for Ktor HTTP client.
 */
actual object Platform {
    private const val PREFS_NAME = "parental_coordination_prefs"

    // Base URL for Android.
    //
    // `10.0.2.2` es el alias que el EMULADOR usa para llegar al host: en un teléfono
    // físico no existe. Para un dispositivo real en la misma red que esta máquina se
    // usa la IP LAN del host (aquí `192.168.0.17`, la de la iface `wlp4s0`).
    //
    // ⚠ El teléfono y el(host) tienen que estar en la MISMA red, y el cleartext HTTP
    // necesita permiso: lo da `androidApp/src/androidMain/res/xml/network_security_config.xml`.
    actual val baseUrl: String = "http://192.168.0.17:3000/api"
    actual val wsUrl: String = "ws://192.168.0.17:3000/realtime"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize with Android Context. Must be called before any storage operations.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(io.ktor.client.plugins.websocket.WebSockets)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
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
