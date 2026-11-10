package com.parental.shared.core.network.realtime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeCoalescerTest {
    @Test
    fun `5 offers less than 500ms produce 1 signal`() = runTest {
        val coalescer = RealtimeCoalescer(windowMs = 500L)
        var count = 0
        val job = backgroundScope.launch {
            coalescer.signals.collect { count++ }
        }
        runCurrent() // let the collector subscribe
        coalescer.offer()
        coalescer.offer()
        coalescer.offer()
        coalescer.offer()
        coalescer.offer()
        advanceTimeBy(600)
        runCurrent()
        job.cancel()
        assertEquals(1, count)
    }

    /**
     * S3 del verify: con `debounce` un stream CONTINUO (huecos < 500 ms) nunca emitía —
     * la señal se quedaba sin dormir. Con throttle ([sample]) la ráfaga produce señales
     * en cada ventana Y la última oferta se emite en el siguiente tick (≤ 500 ms tras la
     * última oferta): el estado final siempre llega a loadAll().
     */
    @Test
    fun `stream continuo aún emite señal trailing tras la última oferta`() = runTest {
        val coalescer = RealtimeCoalescer(windowMs = 500L)
        var count = 0
        val job = backgroundScope.launch {
            coalescer.signals.collect { count++ }
        }
        runCurrent() // let the collector subscribe (aligns the sample ticker at t=0)
        // Ráfaga continua: 10 ofertas cada 100 ms durante 1 s — nunca hay 500 ms de silencio.
        repeat(10) {
            coalescer.offer()
            advanceTimeBy(100)
            runCurrent()
        }
        // Ofertas en t=0..900 ⇒ ticks de sample en t=500 y t=1000 → exactamente 2 señales.
        assertEquals(2, count, "throttle: 1 señal por ventana, sin quedarse sin señales")
        advanceTimeBy(600)
        runCurrent()
        assertEquals(2, count, "sin ofertas nuevas no hay emisiones extra")
        job.cancel()
    }
}