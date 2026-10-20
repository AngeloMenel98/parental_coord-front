package com.parental.shared.core.network.realtime

import io.ktor.client.HttpClient
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealtimeConnectionManagerTest {
    /**
     * REQ-5 — backoff exponencial 1s → 30s + jitter ≤ 20% del base, TOPE a 30s, y
     * RESET a 1 s tras una conexión exitosa. Reemplaza al `assertTrue(true)` que había
     * aquí (W1 del verify): la sesión se simula SIN red sobre un `connectSession` falso
     * (cada intento falla de inmediato; el intento 9 conecta), y todo el tiempo es
     * VIRTUAL — los gaps entre invocaciones de `tokenProvider` son la duración exacta
     * de cada `delay(backoff)` que aplicó el manager.
     *
     * Contrato real de `computeBackoff`: base_n = min(1000·2^(n-1), 30000) y
     * jitter = random[0, base·2/10) ⇒ delay ∈ [base, base + base·2/10].
     */
    @Test
    fun `backoff computed between 1s and 30s with jitter`() = runTest {
        val marks = mutableListOf<Long>()
        val manager = object : RealtimeConnectionManager(
            http = HttpClient { },
            url = "ws://localhost:3000/realtime",
            tokenProvider = {
                marks += testScheduler.currentTime
                "tok-${marks.size}"
            },
            dispatcher = StandardTestDispatcher(testScheduler),
            random = Random(123),
            backoffBaseMs = 1_000L,
            backoffMaxMs = 30_000L,
        ) {
            override suspend fun connectSession(
                token: String,
                onConnected: () -> Unit,
                onMessage: suspend (ActivityChangedMessage) -> Unit,
                onClosed: (Throwable?) -> Unit,
            ) {
                if (token == "tok-9") {
                    // Intento 9: conexión EXITOSA → onConnected resetea el backoff (attempt=0).
                    onConnected()
                    onClosed(null)
                } else {
                    onClosed(IllegalStateException("connection refused"))
                }
            }
        }

        manager.start()
        // 10 obtenciones de token = 8 fallos + 1 éxito + 1 intento tras el reset.
        var guard = 0
        while (marks.size < 10 && guard < 1_000) {
            advanceTimeBy(1_000)
            runCurrent()
            guard++
        }
        manager.stop()

        assertEquals(10, marks.size, "se esperaban 10 intentos de conexión, llegaron ${marks.size}")
        val gaps = marks.zipWithNext { a, b -> b - a }

        // Exponencial con jitter acotado: 1s → 2 → 4 → 8 → 16 → 30s (tope) → 30s → 30s.
        val expectedBases = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L)
        expectedBases.forEachIndexed { i, base ->
            val jitterMax = base * 2 / 10 // jitter = random[0, jitterMax)
            val gap = gaps[i]
            assertTrue(
                gap in base..(base + jitterMax),
                "intento ${i + 1}: delay $gap ms fuera del rango esperado [$base, ${base + jitterMax}] ms",
            )
        }
        // Tope REAL en 30s: sin tope, los intentos 7 y 8 darían 64_000 y 128_000 ms.
        assertTrue(gaps[6] <= 36_000L && gaps[7] <= 36_000L, "el backoff no debe superar 30s + 20% de jitter")

        // Conexión exitosa ⇒ el siguiente delay vuelve al mínimo (NO sigue en el tope).
        assertTrue(
            gaps[8] in 1_000L..1_200L,
            "tras conectar, el backoff debe resetear a ~1s; fue ${gaps[8]} ms",
        )
    }

    /**
     * N5 (post-verify): la versión anterior solo alcanzaba a asertar `tok-1` porque usaba
     * el dispatcher real y hacía stop() tras el PRIMER intento. Ahora la sesión se simula
     * SIN red (todo intento falla de inmediato) sobre tiempo VIRTUAL: se avanza hasta el
     * segundo intento y se aserte que cada intento obtiene un token genuinamente fresco.
     */
    @Test
    fun `token provided fresh per attempt`() = runTest {
        val tokensUsed = mutableListOf<String>()
        val manager = object : RealtimeConnectionManager(
            http = HttpClient { },
            url = "ws://localhost:3000/realtime",
            tokenProvider = {
                val token = "tok-${tokensUsed.size + 1}"
                tokensUsed += token
                token
            },
            dispatcher = StandardTestDispatcher(testScheduler),
            random = Random(1),
        ) {
            override suspend fun connectSession(
                token: String,
                onConnected: () -> Unit,
                onMessage: suspend (ActivityChangedMessage) -> Unit,
                onClosed: (Throwable?) -> Unit,
            ) {
                onClosed(IllegalStateException("connection refused"))
            }
        }

        manager.start()
        // 1 fallo + backoff (~1 s) + retry: avanzar hasta la 2ª obtención de token.
        var guard = 0
        while (tokensUsed.size < 2 && guard < 100) {
            advanceTimeBy(1_000)
            runCurrent()
            guard++
        }
        manager.stop()

        assertTrue(tokensUsed.size >= 2, "se esperaban ≥2 intentos, llegaron ${tokensUsed.size}")
        assertEquals("tok-1", tokensUsed[0])
        assertEquals("tok-2", tokensUsed[1], "cada intento debe pedir un token fresco, no reutilizar tok-1")
    }

    /**
     * N3 (post-verify): la cancelación de `sessionJob` en stop() solo estaba verificada
     * estáticamente. Aquí la sesión "se conecta" y queda VIVA (bloqueada); tras stop()
     * debe cancelarse de verdad (finally del connectSession falso se ejecuta) y el loop
     * de reconexión no debe pedir más tokens.
     */
    @Test
    fun `stop cancels live session job`() = runTest {
        var sessionCancelled = false
        val tokensUsed = mutableListOf<String>()
        val manager = object : RealtimeConnectionManager(
            http = HttpClient { },
            url = "ws://localhost:3000/realtime",
            tokenProvider = {
                val token = "tok-${tokensUsed.size + 1}"
                tokensUsed += token
                token
            },
            dispatcher = StandardTestDispatcher(testScheduler),
            random = Random(1),
        ) {
            override suspend fun connectSession(
                token: String,
                onConnected: () -> Unit,
                onMessage: suspend (ActivityChangedMessage) -> Unit,
                onClosed: (Throwable?) -> Unit,
            ) {
                onConnected() // handshake OK → estado CONNECTED, sesión "viva"
                try {
                    kotlinx.coroutines.delay(Long.MAX_VALUE) // bloqueada hasta cancelación
                } finally {
                    sessionCancelled = true
                }
            }
        }

        manager.start()
        runCurrent() // connectJob + sessionJob arrancan; onConnected → CONNECTED
        assertEquals(RealtimeState.CONNECTED, manager.state.value)
        assertEquals(1, tokensUsed.size)

        manager.stop()
        runCurrent()
        assertTrue(sessionCancelled, "stop() debe cancelar el sessionJob vivo")
        assertEquals(RealtimeState.DISCONNECTED, manager.state.value)

        // Sin reconexiones fantasma: el loop del connectJob también murió con stop().
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, tokensUsed.size, "no debe haber intentos de conexión tras stop()")
    }

    @Test
    fun `stop is idempotent`() = runTest {
        val manager = RealtimeConnectionManager(
            http = HttpClient { },
            url = "ws://localhost:3000/realtime",
            tokenProvider = { "t" },
            random = Random(1),
        )
        manager.stop()
        manager.stop()
        assertEquals(RealtimeState.DISCONNECTED, manager.state.value)
    }
}
