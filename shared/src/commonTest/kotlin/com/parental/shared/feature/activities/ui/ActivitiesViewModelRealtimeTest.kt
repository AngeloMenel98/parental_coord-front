package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.core.network.realtime.ActivityChangedMessage
import com.parental.shared.core.network.realtime.RealtimeMessage
import com.parental.shared.core.network.realtime.RealtimeConnectionManager
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivitiesViewModelRealtimeTest {
    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        var listCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> {
            listCalls++
            gate?.await()
            return ApiResult.Success(emptyList())
        }
    }

    private class FakeCategoriesApi : CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(listOf(CategoryDto(id = "cat", name = "Salud", criticality = "medium")))
    }

    private class FakeBondsApi : BondsApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> =
            ApiResult.Success(
                listOf(
                    Vinculo(
                        id = "b-1",
                        title = "Familia",
                        agreementType = "custody",
                        members = emptyList(),
                        children = emptyList(),
                    ),
                ),
            )
    }

    private class FakeRealtimeManager : RealtimeConnectionManager(
        http = HttpClient { },
        url = "ws://localhost",
        tokenProvider = { null },
    ) {
        val resyncFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        override val resyncSignals: Flow<Unit> = resyncFlow
    }

    /**
     * Manager SIN red cuya sesión "se abre" y captura el callback de frames con el que el
     * [com.parental.shared.core.network.realtime.RealtimeClient] entregaría cada
     * `Frame.Text` recibido. [emitFrame] recorre la MISMA ruta de producción:
     * onMessage → coalescer (500 ms) → resyncSignals → loadAll() del ViewModel.
     * La sesión queda viva hasta `stop()`, que cancela el job de sesión.
     */
    private class ConnectedFrameManager(
        dispatcher: CoroutineDispatcher,
    ) : RealtimeConnectionManager(
        http = UNUSED_CLIENT,
        url = "ws://test",
        tokenProvider = { "tok" },
        dispatcher = dispatcher,
    ) {
        private var frameSink: (suspend (RealtimeMessage) -> Unit)? = null

        override suspend fun connectSession(
            token: String,
            onConnected: () -> Unit,
            onMessage: suspend (RealtimeMessage) -> Unit,
            onClosed: (Throwable?) -> Unit,
        ) {
            frameSink = onMessage
            onConnected() // handshake exitoso → resync post-connect (REQ-5)
            awaitCancellation()
        }

        suspend fun emitFrame(msg: RealtimeMessage) {
            val sink = frameSink ?: error("el manager no está conectado: llama a start() antes")
            sink(msg)
        }
    }

    private fun frame(i: Int) = ActivityChangedMessage(
        v = 1,
        type = "activity.changed",
        eventId = "ev-$i",
        activityId = "act-$i",
        bondId = bondId,
        from = "assigned",
        to = "assisting",
    )

    private val viewModels = mutableListOf<ActividadesViewModel>()

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `single flight loadAll not concurrent`() = runTest {
        val api = FakeActivitiesApi()
        val realtime = FakeRealtimeManager()
        api.gate = CompletableDeferred() // set gate BEFORE VM construction so init's loadAll blocks
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
            realtime = realtime,
        )
        viewModels += vm
        // init's loadAll is now blocked on gate (listCalls = 1)
        // further loadAll() calls should be no-ops because loadJob is active
        vm.loadAll()
        vm.loadAll()
        delay(10)
        assertEquals(1, api.listCalls)
        api.gate?.complete(Unit)
    }

    /**
     * C1 del verify (REQ-2/REQ-6): antes el collector de `changes` del ViewModel era un
     * no-op y los frames NUNCA llegaban al coalescer — el refetch en vivo no existía.
     * Ahora: frame → coalescer → UN loadAll por ventana (5 frames rápidos ⇒ 1 recarga),
     * y el resync de conexión exitosa (REQ-5) pasa por el MISMO camino.
     */
    @Test
    fun `frames de actividad recargan la lista — una sola recarga por ráfaga`() = runTest {
        val api = FakeActivitiesApi()
        val realtime = ConnectedFrameManager(UnconfinedTestDispatcher(testScheduler))
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            realtime = realtime,
        )
        viewModels += vm
        assertEquals(1, api.listCalls, "la carga inicial corre en init")

        realtime.start() // handshake OK → oferta al coalescer (resync REQ-5)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(2, api.listCalls, "REQ-5: cada conexión exitosa dispara un loadAll")

        // REQ-6: 5 frames en la MISMA ventana (<500 ms) → UN solo loadAll.
        repeat(5) { i ->
            realtime.emitFrame(frame(i))
        }
        advanceTimeBy(600)
        runCurrent()
        assertEquals(3, api.listCalls, "REQ-2/REQ-6: ráfaga de 5 frames ⇒ UN solo loadAll")

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, api.listCalls, "sin frames no hay recargas espurias")

        // Ventanas separadas → recargas separadas (el coalescer no fusiona lo que no es ráfaga).
        realtime.emitFrame(frame(99))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(4, api.listCalls, "un frame en ventana nueva ⇒ una recarga nueva")

        vm.destroy()
        // N4: el manager lo posee el caller (como en App.kt) — destroy() ya no lo detiene.
        realtime.stop() // cancela la sesión viva (sin bucles huérfanos)
    }

    /**
     * W3 del verify: una señal que llega mientras loadAll() está en vuelo NO se descarta
     * — se rearma EXACTAMENTE una recarga pendiente (trailing-edge). Varias señales
     * durante la misma carga coalescen en esa única recarga; single-flight intacto.
     */
    @Test
    fun `señal durante una carga en vuelo reprograma UNA recarga tras terminar`() = runTest {
        val api = FakeActivitiesApi()
        api.gate = CompletableDeferred() // la carga inicial queda EN VUELO
        val realtime = ConnectedFrameManager(UnconfinedTestDispatcher(testScheduler))
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            realtime = realtime,
        )
        viewModels += vm
        assertEquals(1, api.listCalls)

        // Tres señales distintas (resync de conexión + 2 frames) caen en la ventana de la
        // carga en curso: ninguna debe disparar una recarga CONCURRENTE.
        realtime.start()
        advanceTimeBy(600)
        runCurrent()
        realtime.emitFrame(frame(1))
        advanceTimeBy(600)
        runCurrent()
        realtime.emitFrame(frame(2))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(1, api.listCalls, "single-flight: ninguna recarga concurrente")

        api.gate?.complete(Unit) // termina la carga en vuelo
        advanceTimeBy(600)
        runCurrent()
        assertEquals(2, api.listCalls, "W3: exactamente UNA recarga pendiente tras la carga")

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, api.listCalls, "las señales no se acumulan en recargas extra")

        vm.destroy()
        realtime.stop() // el caller (como App.kt) detiene el manager inyectado
    }

    companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
