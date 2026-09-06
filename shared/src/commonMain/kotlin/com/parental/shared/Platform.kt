package com.parental.shared

import io.ktor.client.*

/**
 * Platform-specific declarations for API configuration, HTTP client, and persistent storage.
 * Each target (Android, iOS) provides its own `actual` implementation.
 */
expect object Platform {
    /** Base URL for the API, configured per platform/environment. */
    val baseUrl: String

    /** Create a platform-appropriate HTTP client. */
    fun createHttpClient(): HttpClient

    /** Save a string value to persistent storage. */
    fun saveString(key: String, value: String)

    /** Load a string value from persistent storage. Returns null if not found. */
    fun loadString(key: String): String?

    /** Remove a value from persistent storage. */
    fun remove(key: String)
}
