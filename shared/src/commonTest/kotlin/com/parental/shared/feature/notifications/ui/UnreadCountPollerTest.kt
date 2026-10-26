package com.parental.shared.feature.notifications.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S1/S8 · contador de no leídas y su poller.
 *
 * El poller NO usa WebSocket ni push (S8): es un `while` con `delay`. El reloj es
 * virtual — `runTest` + `advanceTimeBy` — así que "30 segundos" es un dato del
 * test, no una espera real.
 */
class UnreadCountPollerTest {

    private class FakeNotificationsApi : NotificationsApi(UNUSED_CLIENT, "http://unused") {
        var unreadCalls = 0
        var result: ApiResult<Int> = ApiResult.Success(0)

        override suspend fun unreadCount(token: String): ApiResult<Int> {
            unreadCalls++
            return result
        }
    }

    // ── S1 · el holder ─────────────────────────────────────────────────────

    @Test
    fun `S1 el holder nunca queda negativo`() {
        val badge = UnreadBadge()

        badge.set(-5)
        assertEquals(0, badge.count.value)

        badge.set(2)
        badge.decrement()
        badge.decrement()
        badge.decrement()

        assertEquals(0, badge.count.value)
    }

    // ── S1/S8 · polling ────────────────────────────────────────────────────

    @Test
    fun `S1 el poller fetcha al arrancar y luego cada intervalo`() = runTest {
        val api = FakeNotificationsApi().apply { result = ApiResult.Success(3) }
        val badge = UnreadBadge()
        val poller = UnreadCountPoller(
            api = api,
            token = "t-1",
            badge = badge,
            intervalMs = 30_000,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        poller.start()
        assertEquals(3, badge.count.value)
        assertEquals(1, api.unreadCalls)

        api.result = ApiResult.Success(7)
        advanceTimeBy(30_001)

        assertEquals(7, badge.count.value)
        assertEquals(2, api.unreadCalls)

        poller.destroy()
    }

    @Test
    fun `S1 arrancar dos veces deja UN solo bucle`() = runTest {
        val api = FakeNotificationsApi()
        val poller = UnreadCountPoller(
            api = api,
            token = "t-1",
            badge = UnreadBadge(),
            intervalMs = 30_000,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        poller.start()
        poller.start()
        advanceTimeBy(30_001)

        assertEquals(2, api.unreadCalls)

        poller.destroy()
    }

    @Test
    fun `S1 un fallo de red conserva el ultimo valor conocido`() = runTest {
        val api = FakeNotificationsApi().apply { result = ApiResult.Success(4) }
        val badge = UnreadBadge()
        val poller = UnreadCountPoller(
            api = api,
            token = "t-1",
            badge = badge,
            intervalMs = 30_000,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        poller.start()
        api.result = ApiResult.Error(0, "GET /notifications/unread-count failed: offline")
        advanceTimeBy(30_001)

        // 0 afirmaría "no hay nada sin leer", que es exactamente lo que no sabemos.
        assertEquals(4, badge.count.value)

        poller.destroy()
    }

    @Test
    fun `S8 al parar no quedan requests en vuelo`() = runTest {
        val api = FakeNotificationsApi()
        val poller = UnreadCountPoller(
            api = api,
            token = "t-1",
            badge = UnreadBadge(),
            intervalMs = 1_000,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        poller.start()
        poller.stop()
        val callsAtStop = api.unreadCalls
        advanceTimeBy(10_000)

        assertEquals(callsAtStop, api.unreadCalls)

        poller.destroy()
    }

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
