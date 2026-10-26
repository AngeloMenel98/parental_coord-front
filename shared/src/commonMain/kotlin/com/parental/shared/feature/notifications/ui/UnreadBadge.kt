package com.parental.shared.feature.notifications.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Única fuente de verdad del contador de no leídas.
 *
 * Existe porque lo leen DOS pantallas (el badge de Home y la lista) y tres
 * emisores (el poller, `markRead` y `markAllRead`). Sin este holder, la lista
 * tendría que esperar hasta 30 s a que el poller se diera cuenta de que el
 * usuario marcó todo como leído. Sin estado Compose a propósito: el holder vive
 * en `App.kt` y las pantallas lo observan con `collectAsState()`.
 */
class UnreadBadge {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun set(value: Int) {
        _count.value = value.coerceAtLeast(0)
    }

    fun decrement() {
        _count.update { (it - 1).coerceAtLeast(0) }
    }
}

/**
 * Polling del contador mientras Home está visible (S1/S8: poll-only, sin push).
 *
 * El ciclo de vida lo posee `App.kt` — igual que `RealtimeConnectionManager`:
 * `start()` al entrar en Home, `stop()` al salir. `start()` es idempotente para
 * que una recomposición no deje dos bucles vivos.
 *
 * Un fallo de red NO pone el badge a 0: se conserva el último valor conocido
 * (ponerlo a 0 afirmaría "no hay nada sin leer", que es justo lo que no sabemos).
 */
class UnreadCountPoller(
    private val api: NotificationsApi,
    private val token: String,
    private val badge: UnreadBadge,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                fetchOnce()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    /** Fetch inmediato fuera del ciclo (p. ej. al volver de la lista). */
    fun refreshNow() {
        scope.launch { fetchOnce() }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private suspend fun fetchOnce() {
        when (val result = api.unreadCount(token)) {
            is ApiResult.Success -> badge.set(result.data)
            is ApiResult.Error -> Unit
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 30_000L
    }
}
