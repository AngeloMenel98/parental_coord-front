package com.parental.shared.core.network.realtime

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException

class RealtimeClient(
    private val http: HttpClient,
    private val url: String,
) {
    suspend fun connect(
        token: String,
        onConnected: () -> Unit = {},
        onMessage: suspend (RealtimeMessage) -> Unit,
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
                            // El tipo lo decide el sobre; un frame desconocido o
                            // malformado se descarta sin cortar la conexión.
                            parseRealtimeMessage(frame.readText())?.let { onMessage(it) }
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
