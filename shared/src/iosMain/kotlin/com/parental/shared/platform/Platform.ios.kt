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
    private const val SECRET_MARKER_PREFIX = "secret_marker_"

    // - iOS Simulator (debug): http://127.0.0.1:3000/api
    // - Real device: use your machine's IP (e.g., http://192.168.1.x:3000/api)
    private const val DEBUG_BASE_URL = "http://127.0.0.1:3000/api"

    // TODO: reemplazar por la URL HTTPS real de producción.
    private const val RELEASE_BASE_URL = "https://api.parental-coord.example/api"

    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = kotlin.native.Platform.isDebugBinary

    actual val baseUrl: String = if (isDebug) DEBUG_BASE_URL else RELEASE_BASE_URL

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

    actual fun saveSecureString(key: String, value: String) {
        if (KeychainStorage.save(key, value)) {
            saveString(SECRET_MARKER_PREFIX + key, "1")
        } else {
            removeSecureString(key)
        }
    }

    /**
     * El Keychain sobrevive a la desinstalación de la app; NSUserDefaults no.
     * Sin marcador, el secreto es de una instalación anterior y se descarta.
     */
    actual fun loadSecureString(key: String): String? {
        if (loadString(SECRET_MARKER_PREFIX + key) == null) {
            KeychainStorage.remove(key)
            return null
        }
        return KeychainStorage.load(key)
    }

    actual fun removeSecureString(key: String) {
        KeychainStorage.remove(key)
        remove(SECRET_MARKER_PREFIX + key)
    }
}
