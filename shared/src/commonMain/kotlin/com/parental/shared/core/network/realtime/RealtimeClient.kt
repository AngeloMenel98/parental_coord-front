package com.parental.shared.core.network.realtime

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class RealtimeClient(
    private val http: HttpClient,
    private val url: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun connect(
        token: String,
        onConnected: () -> Unit = {},
        onMessage: suspend (ActivityChangedMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ) {
        try {
            http.webSocket(urlString = url, request = {
                header(HttpHeaders.Authorization, "Bearer $token")
            }) {
                onConnected()
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            val msg = try {
                                json.decodeFromString<ActivityChangedMessage>(text)
                            } catch (_: Exception) {
                                null
                            }
                            if (msg != null) {
                                onMessage(msg)
                            }
                        }
                        is Frame.Close -> {
                            close()
                            break
                        }
                        else -> {}
                    }
                }
            }
            onClosed(null)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onClosed(t)
        }
    }
}
