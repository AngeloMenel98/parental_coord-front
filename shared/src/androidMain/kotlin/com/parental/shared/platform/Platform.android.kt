package com.parental.shared.platform

import android.content.Context
import android.content.SharedPreferences
import com.parental.shared.BuildConfig
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
    // Comes from BuildConfig, injected by shared/build.gradle.kts so the same
    // source builds for the deployed host and for a local backend:
    //   ./gradlew :androidApp:assembleDebug -PapiBaseUrl=... -PwsUrl=...
    //
    // The default is the deployed managed host (public HTTPS/WSS). A LAN address
    // such as 192.168.0.17 only works on a phone joined to the same Wi-Fi, so it
    // is no longer baked in: an APK installed elsewhere would talk to nothing.
    // Cleartext HTTP is still permitted by
    // androidApp/src/androidMain/res/xml/network_security_config.xml, which is
    // what makes a LAN override usable at all.
    actual val baseUrl: String = BuildConfig.API_BASE_URL
    actual val wsUrl: String = BuildConfig.WS_URL

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
