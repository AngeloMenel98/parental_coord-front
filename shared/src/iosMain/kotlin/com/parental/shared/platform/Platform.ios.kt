package com.parental.shared.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSUserDefaults
import platform.Foundation.standardUserDefaults
import kotlin.experimental.ExperimentalNativeApi

/**
 * iOS actual implementation using NSUserDefaults for persistent storage
 * and Darwin engine for Ktor HTTP client.
 */
actual object Platform {
    private const val PREFS_PREFIX = "parental_coord_"

    // - iOS Simulator (debug): http://127.0.0.1:3000/api
    // - Real device: use your machine's IP (e.g., http://192.168.1.x:3000/api)
    private const val DEBUG_BASE_URL = "http://127.0.0.1:3000/api"
    private const val DEBUG_WS_URL = "ws://127.0.0.1:3000/realtime"

    // TODO: reemplazar por la URL HTTPS real de producción.
    private const val RELEASE_BASE_URL = "https://api.parental-coord.example/api"
    private const val RELEASE_WS_URL = "wss://api.parental-coord.example/realtime"

    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = kotlin.native.Platform.isDebugBinary

    actual val baseUrl: String = if (isDebug) DEBUG_BASE_URL else RELEASE_BASE_URL
    actual val wsUrl: String = if (isDebug) DEBUG_WS_URL else RELEASE_WS_URL

    actual fun createHttpEngine(): HttpClientEngine = Darwin.create()

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
