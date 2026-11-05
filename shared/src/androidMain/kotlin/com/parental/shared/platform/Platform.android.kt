package com.parental.shared.platform

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android actual implementation using SharedPreferences for persistent storage
 * and OkHttp engine for Ktor HTTP client.
 */
actual object Platform {
    private const val PREFS_NAME = "parental_coordination_prefs"

    private var prefs: SharedPreferences? = null
    private var configuredBaseUrl: String? = null
    private var debug: Boolean = false

    /** Base URL inyectada desde BuildConfig del módulo app (varía por build type). */
    actual val baseUrl: String
        get() = checkNotNull(configuredBaseUrl) { "Platform.init() must be called before using the API" }

    actual val isDebug: Boolean get() = debug

    /**
     * Initialize with Android Context and build config. Must be called before
     * any storage or network operation (ParentalApplication.onCreate).
     */
    fun init(context: Context, baseUrl: String, isDebug: Boolean) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configuredBaseUrl = baseUrl
        debug = isDebug
    }

    actual fun createHttpEngine(): HttpClientEngine = OkHttp.create()

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
