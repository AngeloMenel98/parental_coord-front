package com.parental.shared.feature.notifications.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import com.parental.shared.feature.notifications.domain.model.NotificationItem
import com.parental.shared.feature.notifications.domain.model.NotificationsPage
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S3–S7 · ViewModel de la lista de notificaciones.
 *
 * Mismo patrón que `ActivitiesViewModelSwipeTest` (D17): `UnconfinedTestDispatcher`
 * inyectado y la clase `open` de la API sustituida — sin `Dispatchers.setMain`.
 * El único caso que necesita asincronía real es el doble toque de "Marcar todas":
 * ahí un `CompletableDeferred` hace de compuerta mientras la request está en vuelo.
 */
class NotificationsViewModelTest {

    private val token = "t-1"
    private val badge = UnreadBadge()
    private val viewModels = mutableListOf<NotificationsViewModel>()

    private class FakeNotificationsApi : NotificationsApi(UNUSED_CLIENT, "http://unused") {
        var listCalls = 0
        val requestedOffsets = mutableListOf<Int>()
        var unreadCalls = 0
        var markReadCalls = 0
        var markAllCalls = 0
        val markedReadIds = mutableListOf<String>()

        var page: NotificationsPage = NotificationsPage(emptyList(), 0, 20, 0)
        var listResult: ApiResult<NotificationsPage>? = null
        var unreadResult: ApiResult<Int> = ApiResult.Success(0)
        var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var markAllResult: ApiResult<Int> = ApiResult.Success(0)

        /** Si está presente, "Marcar todas" queda suspendida hasta completarla. */
        var markAllGate: CompletableDeferred<Unit>? = null

        override suspend fun list(
            token: String,
            limit: Int,
            offset: Int,
        ): ApiResult<NotificationsPage> {
            listCalls++
            requestedOffsets += offset
            return listResult ?: ApiResult.Success(page)
        }

        override suspend fun unreadCount(token: String): ApiResult<Int> {
            unreadCalls++
            return unreadResult
        }

        override suspend fun markRead(token: String, id: String): ApiResult<Unit> {
            markReadCalls++
            markedReadIds += id
            return markReadResult
        }

        override suspend fun markAllRead(token: String): ApiResult<Int> {
            markAllCalls++
            markAllGate?.await()
            return markAllResult
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private fun build(api: FakeNotificationsApi): NotificationsViewModel {
        val viewModel = NotificationsViewModel(
            api = api,
            token = token,
            badge = badge,
            dispatcher = UnconfinedTestDispatcher(),
        )
        viewModels += viewModel
        return viewModel
    }

    private fun item(
        id: String = "n-1",
        isRead: Boolean = false,
        title: String = "Actividad asignada",
    ) = NotificationItem(
        id = id,
        type = "activity_assigned",
        title = title,
        body = "Te asignaron una actividad",
        refEntityType = "activity",
        refEntityId = "act-1",
        bondId = null,
        isRead = isRead,
        createdAt = "2026-10-01T12:00:00.000Z",
    )

    // ── S3 · carga al abrir ────────────────────────────────────────────────

    @Test
    fun `S3 al abrir carga la primera pagina y sincroniza el badge`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1"), item("n-2")), total = 2, limit = 20, offset = 0)
            unreadResult = ApiResult.Success(2)
        }

        val viewModel = build(api)

        assertEquals(listOf(0), api.requestedOffsets)
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals(2, viewModel.uiState.value.total)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(2, badge.count.value)
        assertEquals(2, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun `S3 respeta el orden del backend sin reordenar`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(
                listOf(item("n-nuevo"), item("n-viejo")),
                total = 2,
                limit = 20,
                offset = 0,
            )
        }

        val viewModel = build(api)

        assertEquals(listOf("n-nuevo", "n-viejo"), viewModel.uiState.value.items.map { it.id })
    }

    // ── S4 · paginación ────────────────────────────────────────────────────

    @Test
    fun `S4 cargar mas pide el offset siguiente y anexa`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1"), item("n-2")), total = 4, limit = 2, offset = 0)
        }
        val viewModel = build(api)

        api.page = NotificationsPage(listOf(item("n-3"), item("n-4")), total = 4, limit = 2, offset = 2)
        viewModel.loadMore()

        assertEquals(listOf(0, 2), api.requestedOffsets)
        assertEquals(listOf("n-1", "n-2", "n-3", "n-4"), viewModel.uiState.value.items.map { it.id })
        assertFalse(viewModel.uiState.value.isLoadingMore)
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun `S4 no pide una pagina cuando ya estan todas`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 1, limit = 20, offset = 0)
        }
        val viewModel = build(api)

        viewModel.loadMore()

        assertEquals(1, api.listCalls)
    }

    @Test
    fun `S4 no duplica ids si el backend repite filas`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1"), item("n-2")), total = 3, limit = 2, offset = 0)
        }
        val viewModel = build(api)

        api.page = NotificationsPage(listOf(item("n-2"), item("n-3")), total = 3, limit = 2, offset = 2)
        viewModel.loadMore()

        assertEquals(listOf("n-1", "n-2", "n-3"), viewModel.uiState.value.items.map { it.id })
    }

    // ── S5 · marcar una ────────────────────────────────────────────────────

    @Test
    fun `S5 marcar una la deja leida y baja el badge`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1"), item("n-2")), total = 2, limit = 20, offset = 0)
            unreadResult = ApiResult.Success(2)
        }
        val viewModel = build(api)

        viewModel.markRead("n-1")

        assertEquals(listOf("n-1"), api.markedReadIds)
        assertTrue(viewModel.uiState.value.items.first { it.id == "n-1" }.isRead)
        assertFalse(viewModel.uiState.value.items.first { it.id == "n-2" }.isRead)
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertEquals(1, badge.count.value)
    }

    @Test
    fun `S5 si falla la fila sigue sin leer y NO se pierde la lista`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 1, limit = 20, offset = 0)
            unreadResult = ApiResult.Success(1)
            markReadResult = ApiResult.Error(404, "Notification \"n-1\" not found")
        }
        val viewModel = build(api)

        viewModel.markRead("n-1")

        assertFalse(viewModel.uiState.value.items.single().isRead)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals(1, badge.count.value)
        assertEquals("Notification \"n-1\" not found", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `S5 una fila ya leida no vuelve a salir a la red`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1", isRead = true)), total = 1, limit = 20, offset = 0)
        }
        val viewModel = build(api)

        viewModel.markRead("n-1")

        assertEquals(0, api.markReadCalls)
    }

    @Test
    fun `S5 el badge nunca baja de cero`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 1, limit = 20, offset = 0)
            // El backend dice 0 pero la fila llega sin leer: el decremento no puede ir a -1.
            unreadResult = ApiResult.Success(0)
        }
        val viewModel = build(api)

        viewModel.markRead("n-1")

        assertEquals(0, badge.count.value)
    }

    // ── S6 · marcar todas ──────────────────────────────────────────────────

    @Test
    fun `S6 marcar todas apaga todas las filas y el badge`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(
                listOf(item("n-1"), item("n-2", isRead = true)),
                total = 2,
                limit = 20,
                offset = 0,
            )
            unreadResult = ApiResult.Success(1)
        }
        val viewModel = build(api)

        viewModel.markAllRead()

        assertTrue(viewModel.uiState.value.items.all { it.isRead })
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertEquals(0, badge.count.value)
        assertFalse(viewModel.uiState.value.isMarkingAll)
    }

    @Test
    fun `S6 dos toques seguidos mandan UNA sola request`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 1, limit = 20, offset = 0)
            markAllGate = CompletableDeferred()
        }
        val viewModel = build(api)

        viewModel.markAllRead()
        viewModel.markAllRead()
        api.markAllGate!!.complete(Unit)

        assertEquals(1, api.markAllCalls)
        assertFalse(viewModel.uiState.value.isMarkingAll)
    }

    // ── S7 · estados ───────────────────────────────────────────────────────

    @Test
    fun `S7 un fallo de red conserva lo ya cargado`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 1, limit = 20, offset = 0)
        }
        val viewModel = build(api)

        api.listResult = ApiResult.Error(0, "GET /notifications failed: timeout")
        viewModel.refresh()

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("GET /notifications failed: timeout", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `S7 el primer fallo deja la pantalla en error sin items`() {
        val api = FakeNotificationsApi().apply {
            listResult = ApiResult.Error(500, "Internal server error")
        }

        val viewModel = build(api)

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals("Internal server error", viewModel.uiState.value.error)
    }

    @Test
    fun `S7 consumir el snackbar lo limpia`() {
        val api = FakeNotificationsApi().apply {
            page = NotificationsPage(listOf(item("n-1")), total = 2, limit = 20, offset = 0)
        }
        val viewModel = build(api)

        api.listResult = ApiResult.Error(0, "boom")
        viewModel.loadMore()
        viewModel.consumeSnackbar()

        assertNull(viewModel.uiState.value.snackbarMessage)
    }

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
