package com.parental.shared.core.network.realtime

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.datetime.Clock

/**
 * Coalescer de señales de refetch (REQ-2/REQ-6).
 *
 * Usa [sample] (throttle) y NO `debounce`: un stream CONTINUO de ofertas con huecos < [windowMs]
 * pasaría hambre a `debounce` (nunca emitiría). Cada ventana de [windowMs] emite como mucho UNA
 * señal con la oferta más reciente, y la última oferta de una ráfaga SIEMPRE se emite en el
 * siguiente tick (≤ [windowMs] ms después) — el estado final nunca se pierde.
 */
@OptIn(FlowPreview::class)
class RealtimeCoalescer(
    private val windowMs: Long = 500L,
) {
    private val _trigger = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 64)

    val signals: Flow<Unit> = _trigger
        .sample(windowMs)
        .map { Unit }

    fun offer(now: Long = Clock.System.now().toEpochMilliseconds()) {
        _trigger.tryEmit(now)
    }
}
