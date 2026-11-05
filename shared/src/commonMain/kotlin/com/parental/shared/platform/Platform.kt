package com.parental.shared.platform

import io.ktor.client.engine.HttpClientEngine

/**
 * Platform-specific declarations for API configuration, HTTP engine, and persistent storage.
 * Each target (Android, iOS) provides its own `actual` implementation.
 * The HttpClient itself (plugins, JSON, auth) is built once in commonMain.
 */
expect object Platform {
    /** Base URL for the API, configured per platform/build type. */
    val baseUrl: String

    /** WebSocket endpoint for the realtime channel (Main added it; integrated here). */
    val wsUrl: String

    /** True on debug builds: enables HTTP logging. */
    val isDebug: Boolean

    /** Create the platform-appropriate Ktor engine (OkHttp / Darwin). */
    fun createHttpEngine(): HttpClientEngine

    /** Save a string value to persistent storage. */
    fun saveString(key: String, value: String)

    /** Load a string value from persistent storage. Returns null if not found. */
    fun loadString(key: String): String?

    /** Remove a value from persistent storage. */
    fun remove(key: String)

    /** Save a secret encrypted at rest (Android Keystore / iOS Keychain). */
    fun saveSecureString(key: String, value: String)

    /** Load a secret. Returns null if not found or if it can no longer be decrypted. */
    fun loadSecureString(key: String): String?

    /** Remove a secret. */
    fun removeSecureString(key: String)
}
