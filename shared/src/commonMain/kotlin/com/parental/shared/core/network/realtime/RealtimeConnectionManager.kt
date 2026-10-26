package com.parental.shared.core.network.realtime

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

enum class RealtimeState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BACKOFF,
}

open class RealtimeConnectionManager(
    private val http: HttpClient,
    private val url: String,
    private val tokenProvider: () -> String?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
    private val backoffBaseMs: Long = 1_000L,
    private val backoffMaxMs: Long = 30_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val client = RealtimeClient(http, url)
    private val activityCoalescer = RealtimeCoalescer(windowMs = 500L)
    private val notificationCoalescer = RealtimeCoalescer(windowMs = 500L)

    private val _state = MutableStateFlow(RealtimeState.DISCONNECTED)
    val state: StateFlow<RealtimeState> = _state.asStateFlow()

    private var connectJob: Job? = null
    private var sessionJob: Job? = null
    private var attempt = 0
    private var started = false
    private var stopped = false

    fun start() {
        if (started) return
        started = true
        stopped = false
        attempt = 0
        connectJob?.cancel()
        connectJob = scope.launch {
            while (isActive && !stopped) {
                val token = tokenProvider()
                if (token == null) {
                    _state.value = RealtimeState.DISCONNECTED
                    delay(backoffBaseMs)
                    continue
                }
                _state.value = RealtimeState.CONNECTING
                attempt++
                val sessionEnded = CompletableDeferred<Unit>()
                sessionJob = scope.launch {
                    try {
                        connectSession(
                            token = token,
                            onConnected = {
                                attempt = 0
                                _state.value = RealtimeState.CONNECTED
                                // REQ-5: cada conexión exitosa dispara un resync de
                                // actividades (vía coalescer). Un reconnect NO dispara el
                                // refresco de notificaciones: el badge tiene su propio poll
                                // y la lista se recarga al abrirse.
                                activityCoalescer.offer()
                            },
                            onMessage = { message ->
                                // Un canal, varios tipos: cada frame alimenta SÓLO la
                                // señal de su dominio. Antes todo frame movía el
                                // coalescer de actividades, así que una notificación
                                // nueva terminaba en un loadAll() innecesario.
                                when (message) {
                                    is ActivityChangedMessage -> activityCoalescer.offer()
                                    is NotificationCreatedMessage -> notificationCoalescer.offer()
                                }
                            },
                            onClosed = { _ ->
                                sessionEnded.complete(Unit)
                            },
                        )
                    } catch (e: CancellationException) {
                        sessionEnded.complete(Unit)
                        throw e
                    } catch (_: Exception) {
                        sessionEnded.complete(Unit)
                    }
                }
                sessionJob?.join()
                if (stopped) break
                _state.value = RealtimeState.BACKOFF
                val backoff = computeBackoff(attempt)
                delay(backoff)
            }
            _state.value = RealtimeState.DISCONNECTED
        }
    }

    fun stop() {
        if (!started && connectJob == null) {
            _state.value = RealtimeState.DISCONNECTED
            return
        }
        stopped = true
        started = false
        connectJob?.cancel()
        connectJob = null
        // Una sesión viva también debe morir con stop(): sin esto, el WS siguiendo
        // abierto sobreviviría al dispose (foreground-only, REQ-6) y un start() posterior
        // acumularía sesiones. cancel() es idempotente: un segundo stop() no hace nada.
        sessionJob?.cancel()
        sessionJob = null
        _state.value = RealtimeState.DISCONNECTED
        attempt = 0
    }

    /**
     * Punto de conexión con el servidor — único lugar que toca [RealtimeClient].
     * `open` para que los tests puedan simular el ciclo de vida de la sesión SIN red
     * (fallos/conexiones deterministas para el backoff REQ-5, y captura del callback
     * de frames para el camino mensaje→refetch REQ-2/REQ-6).
     */
    protected open suspend fun connectSession(
        token: String,
        onConnected: () -> Unit,
        onMessage: suspend (RealtimeMessage) -> Unit,
        onClosed: (Throwable?) -> Unit,
    ) {
        client.connect(
            token = token,
            onConnected = onConnected,
            onMessage = onMessage,
            onClosed = onClosed,
        )
    }

    private fun computeBackoff(attempt: Int): Long {
        var exp = backoffBaseMs
        repeat((attempt - 1).coerceIn(0, 32)) {
            exp = min(exp * 2, backoffMaxMs)
        }
        val base = exp.coerceIn(backoffBaseMs, backoffMaxMs)
        val jitterRange = (base * 2) / 10
        val jitter = if (jitterRange > 0) random.nextLong(0, jitterRange) else 0L
        return base + jitter
    }

    open val resyncSignals: Flow<Unit> = activityCoalescer.signals

    /**
     * Señal coalescida de "hay una notificación nueva" (`notification.created`).
     *
     * Separada de [resyncSignals] a propósito: refrescar el badge no debe
     * recargar la lista de actividades, ni al revés.
     */
    open val notificationSignals: Flow<Unit> = notificationCoalescer.signals
}
