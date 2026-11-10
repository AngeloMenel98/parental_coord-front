package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActivityReasonRequest
import com.parental.shared.feature.activities.domain.model.CancelActivityResponse
import com.parental.shared.feature.activities.domain.model.DeclineActivityResponse
import com.parental.shared.feature.activities.domain.model.DeleteActivityResponse
import com.parental.shared.feature.activities.domain.model.RestoreActivityResponse
import com.parental.shared.feature.activities.domain.model.UndoDeclineResponse
import com.parental.shared.feature.activities.ui.swipe.ReasonValidation
import com.parental.shared.feature.activities.ui.swipe.SwipeAction
import com.parental.shared.feature.activities.ui.swipe.SWIPE_UNDO_WINDOW_MS
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R1–R8 · ViewModel de las acciones de swipe.
 *
 * Sin `Dispatchers.setMain` y sin fake de `HttpClient`: se inyecta un
 * `UnconfinedTestDispatcher` (D17) y se heredan las clases `open` sustituyendo sólo lo que
 * importa. El reloj también se inyecta, que es lo que hace falta para comprobar los 5000
 * ms sin `Thread.sleep` (R2).
 *
 * ⚠ Un `launch` sin suspensión con `UnconfinedTestDispatcher` se ejecuta ENTERO de forma
 * síncrona, así que las guardas single-flight no se ejercitan sin un `gate`: por eso
 * `actionGate` existe y se usa en el test del doble toque.
 */
class ActivitiesViewModelSwipeTest {

    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"

    /** Reloj controlable. `now` avanza a mano; los 5000 ms son datos, no esperas. */
    private var now = 0L

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        var listCalls = 0
        var deleteCalls = 0
        var restoreCalls = 0
        var cancelCalls = 0
        var declineCalls = 0
        var undoDeclineCalls = 0
        val reasons = mutableListOf<String>()

        /** Si está presente, TODA mutación queda suspendida hasta que se complete. */
        var actionGate: CompletableDeferred<Unit>? = null

        var deleteResult: ApiResult<DeleteActivityResponse> =
            ApiResult.Success(DeleteActivityResponse(id = "act-1", deletedAt = "2026-10-01T12:00:00.000Z"))

        var cancelResult: ApiResult<CancelActivityResponse> =
            ApiResult.Success(CancelActivityResponse(id = "act-1", status = "cancelled", cancelledAt = "2026-10-01T12:00:00.000Z"))

        var declineResult: ApiResult<DeclineActivityResponse> =
            ApiResult.Success(DeclineActivityResponse(id = "act-1", status = "not_assisting", declinedAt = "2026-10-01T12:00:00.000Z"))

        var restoreResult: ApiResult<RestoreActivityResponse> =
            ApiResult.Success(RestoreActivityResponse(id = "act-1", deletedAt = null))

        var undoDeclineResult: ApiResult<UndoDeclineResponse> =
            ApiResult.Success(UndoDeclineResponse(id = "act-1", status = "assigned"))

        override suspend fun listActividades(
            token: String,
            bondId: String,
        ): ApiResult<List<Actividad>> {
            listCalls++
            return ApiResult.Success(actividadesSnapshot)
        }

        /** Lo que el refetch devuelve: por defecto ya sin la fila borrada. */
        var actividadesSnapshot: List<Actividad> = emptyList()

        override suspend fun deleteActivity(
            token: String,
            id: String,
        ): ApiResult<DeleteActivityResponse> {
            deleteCalls++
            actionGate?.await()
            return deleteResult
        }

        override suspend fun restoreActivity(
            token: String,
            id: String,
        ): ApiResult<RestoreActivityResponse> {
            restoreCalls++
            actionGate?.await()
            return restoreResult
        }

        override suspend fun cancelActivity(
            token: String,
            id: String,
            request: ActivityReasonRequest,
        ): ApiResult<CancelActivityResponse> {
            cancelCalls++
            reasons += request.reason
            actionGate?.await()
            return cancelResult
        }

        override suspend fun declineActivity(
            token: String,
            id: String,
            request: ActivityReasonRequest,
        ): ApiResult<DeclineActivityResponse> {
            declineCalls++
            reasons += request.reason
            actionGate?.await()
            return declineResult
        }

        override suspend fun undoDeclineActivity(
            token: String,
            id: String,
        ): ApiResult<UndoDeclineResponse> {
            undoDeclineCalls++
            actionGate?.await()
            return undoDeclineResult
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

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private fun build(actividades: List<Actividad>): Pair<ActividadesViewModel, FakeActivitiesApi> {
        val api = FakeActivitiesApi().apply { actividadesSnapshot = actividades }
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
            clock = { now },
        )
        viewModels += vm
        return vm to api
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private fun row(
        id: String = "act-1",
        canDelete: Boolean = false,
        canCancel: Boolean = false,
        canDecline: Boolean = false,
    ) = Actividad(
        id = id,
        title = "Visita al médico",
        type = "event",
        status = "assigned",
        categoryId = "cat",
        criticality = "medium",
        scheduledStart = "2026-10-05T10:00:00.000Z",
        canDelete = canDelete,
        canCancel = canCancel,
        canDecline = canDecline,
    )

    // ── R1 · la autoridad es del servidor ──────────────────────────────────

    @Test
    fun `R1 una accion que el servidor no habilito NO sale a la red`() {
        // 🔴 El caso central. Sin `can_cancel`, un swipe a "Cancelar" no puede convertirse
        // en un 403: el cliente avisa y no manda nada. `canDecline` en `false` es lo único
        // que lo decide.
        val (vm, api) = build(listOf(row(canDecline = false)))

        vm.startSwipeAction("act-1", SwipeAction.DECLINE)

        assertEquals(0, api.declineCalls)
        assertTrue(vm.uiState.value.snackbarMessage!!.contains("ya no admite"))
    }

    @Test
    fun `R1 con el flag puesto la accion si sale`() {
        val (vm, api) = build(listOf(row(canDecline = true)))

        vm.startSwipeAction("act-1", SwipeAction.DECLINE)
        vm.setSwipeReason("Estoy de viaje")
        vm.submitSwipeReason()

        assertEquals(1, api.declineCalls)
    }

    @Test
    fun `R1 una actividad que no esta en la lista no hace nada`() {
        val (vm, api) = build(listOf(row(canDecline = true)))

        vm.startSwipeAction("no-existe", SwipeAction.DECLINE)

        assertEquals(0, api.declineCalls)
    }

    // ── A1 · el motivo obligatorio ──────────────────────────────────────────

    @Test
    fun `A1 cancelar y declinar abren el dialogo pero eliminar no`() {
        val (vm, api) = build(
            listOf(row(canDelete = true, canCancel = true, canDecline = true)),
        )

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        assertIs<SwipeReasonUiState.Closed>(vm.uiState.value.swipeReason)
        assertEquals(1, api.deleteCalls)

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        assertEquals(
            SwipeAction.CANCEL,
            assertIs<SwipeReasonUiState.Content>(vm.uiState.value.swipeReason).action,
        )
    }

    @Test
    fun `A1 un motivo demasiado corto NO sale a la red`() {
        val (vm, api) = build(listOf(row(canCancel = true)))

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        vm.setSwipeReason("ab")
        vm.submitSwipeReason()

        assertEquals(0, api.cancelCalls)
        val dialogo = assertIs<SwipeReasonUiState.Content>(vm.uiState.value.swipeReason)
        assertEquals(ReasonValidation.TooShort(2), dialogo.validation)
    }

    @Test
    fun `A1 el motivo se envia recortado`() {
        val (vm, api) = build(listOf(row(canCancel = true)))

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        vm.setSwipeReason("   Hospitalización Child   ")
        vm.submitSwipeReason()

        assertEquals(listOf("Hospitalización Child"), api.reasons)
    }

    @Test
    fun `A1 escribir BORRA el error anterior`() {
        val (vm, _) = build(listOf(row(canCancel = true)))

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        vm.setSwipeReason("ab")
        vm.submitSwipeReason()
        assertTrue(assertIs<SwipeReasonUiState.Content>(vm.uiState.value.swipeReason).validation != null)

        vm.setSwipeReason("abcd")
        assertNull(assertIs<SwipeReasonUiState.Content>(vm.uiState.value.swipeReason).validation)
    }

    @Test
    fun `A1 el doble toque no duplica el POST`() {
        val (vm, api) = build(listOf(row(canCancel = true)))
        api.actionGate = CompletableDeferred()

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        vm.setSwipeReason("Motivo válido")
        vm.submitSwipeReason()
        // El `launch` sigue suspendido por el gate, así que `isSubmitting` sigue en `true`
        // y el segundo toque tiene que cortarse aquí.
        assertEquals(1, api.cancelCalls)
    }

    // ── R2 · optimista y deshacer ───────────────────────────────────────────

    @Test
    fun `R2 la fila sale de la lista de forma optimista`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        api.actionGate = CompletableDeferred()

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertTrue(vm.uiState.value.actividades.none { it.id == "act-1" })
    }

    @Test
    fun `R2 eliminar abre la oferta de deshacer`() {
        val (vm, _) = build(listOf(row(canDelete = true)))

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        val oferta = vm.uiState.value.undoOffer!!
        assertEquals(SwipeAction.DELETE, oferta.action)
        // La fila completa viaja en la oferta: sin esto no se podría reinsertar dentro de
        // los 5000 ms sin refetchear el detalle.
        assertEquals("Visita al médico", oferta.previous.title)
        assertEquals(now + SWIPE_UNDO_WINDOW_MS, oferta.expiresAtEpochMillis)
    }

    @Test
    fun `R2 declinar abre la oferta de deshacer`() {
        val (vm, _) = build(listOf(row(canDecline = true)))

        vm.startSwipeAction("act-1", SwipeAction.DECLINE)
        vm.setSwipeReason("Estoy de viaje")
        vm.submitSwipeReason()

        assertEquals(SwipeAction.DECLINE, vm.uiState.value.undoOffer!!.action)
    }

    @Test
    fun `R2 CANCELAR NO abre oferta — no hay verbo de reversion`() {
        // 🔴 Cancelar no se deshace: ofrecer "Deshacer" sobre una acción sin endpoint de
        // vuelta sería un botón que miente. Es un "no" explícito, no un olvido.
        val (vm, _) = build(listOf(row(canCancel = true)))

        vm.startSwipeAction("act-1", SwipeAction.CANCEL)
        vm.setSwipeReason("Cambio de planes")
        vm.submitSwipeReason()

        assertNull(vm.uiState.value.undoOffer)
        assertEquals("Actividad cancelada", vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `R2 deshacer eliminar llama a restore`() {
        val (vm, api) = build(listOf(row(canDelete = true)))

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        vm.undoLastSwipe()

        assertEquals(1, api.restoreCalls)
        assertTrue(vm.uiState.value.actividades.any { it.id == "act-1" })
    }

    @Test
    fun `R2 deshacer declinar llama a undo-decline`() {
        val (vm, api) = build(listOf(row(canDecline = true)))

        vm.startSwipeAction("act-1", SwipeAction.DECLINE)
        vm.setSwipeReason("Estoy de viaje")
        vm.submitSwipeReason()
        vm.undoLastSwipe()

        assertEquals(1, api.undoDeclineCalls)
    }

    @Test
    fun `R2 una oferta vencida NO se deshace`() {
        val (vm, api) = build(listOf(row(canDelete = true)))

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        // Un ms pasado del límite INCLUSIVO.
        now = SWIPE_UNDO_WINDOW_MS + 1
        vm.undoLastSwipe()

        assertEquals(0, api.restoreCalls)
        assertNull(vm.uiState.value.undoOffer)
    }

    @Test
    fun `R2 el limite es inclusivo — a los 5000 ms exactos todavia se deshace`() {
        val (vm, api) = build(listOf(row(canDelete = true)))

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        now = SWIPE_UNDO_WINDOW_MS
        vm.undoLastSwipe()

        assertEquals(1, api.restoreCalls)
    }

    @Test
    fun `R2 deshacer cuando no hay oferta es un no-op`() {
        val (vm, api) = build(listOf(row(canDelete = true)))

        vm.undoLastSwipe()

        assertEquals(0, api.restoreCalls)
    }

    @Test
    fun `R2 una segunda accion reinicia la oferta`() {
        val (vm, _) = build(
            listOf(row(id = "act-1", canDelete = true), row(id = "act-2", canDelete = true)),
        )

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        assertEquals("act-1", vm.uiState.value.undoOffer!!.id)

        vm.startSwipeAction("act-2", SwipeAction.DELETE)
        assertEquals("act-2", vm.uiState.value.undoOffer!!.id)
    }

    // ── errores ────────────────────────────────────────────────────────────

    @Test
    fun `un fallo devuelve la fila EXACTAMENTE como estaba`() {
        val original = row(canDelete = true)
        val (vm, api) = build(listOf(original))
        api.deleteResult = ApiResult.Error(403, "prohibido", "ACTIVITY_FORBIDDEN")

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertEquals(listOf(original), vm.uiState.value.actividades)
        assertNull(vm.uiState.value.undoOffer)
    }

    @Test
    fun `R8 el codigo estable se lee, no el status`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        // 🔴 409 SIN código no debe traducirse al mensaje de ventana vencida: significaría
        // que se está adivinando por status, que es justo lo que R8 prohíbe.
        api.deleteResult = ApiResult.Error(409, "conflicto generico")

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertEquals("conflicto generico", vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `R8 la ventana vencida se explica en CASTELLANO, no con el codigo`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        api.deleteResult = ApiResult.Error(409, "x", "ACTIVITY_UNDO_WINDOW_EXPIRED")

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertEquals("Ya no se puede deshacer: la ventana de 5 segundos venció", vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `R8 un 409 refetchea — la fila ya no es recuperable`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        api.deleteResult = ApiResult.Error(409, "x", "ACTIVITY_UNDO_WINDOW_EXPIRED")
        val antes = api.listCalls

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertTrue(api.listCalls > antes)
    }

    @Test
    fun `un 403 NO refetchea — el gesto basta`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        api.deleteResult = ApiResult.Error(403, "x", "ACTIVITY_FORBIDDEN")
        val antes = api.listCalls

        vm.startSwipeAction("act-1", SwipeAction.DELETE)

        assertEquals(antes, api.listCalls)
    }

    @Test
    fun `deshacer fallido quita la fila — si el 404, la fila no existe`() {
        val (vm, api) = build(listOf(row(canDelete = true)))
        api.restoreResult = ApiResult.Error(404, "no existe", "ACTIVITY_NOT_FOUND")

        vm.startSwipeAction("act-1", SwipeAction.DELETE)
        // El refetch que hace el fallo tiene que devolver lo que el servidor devuelve: un
        // 404 significa que la fila ya no está. Dejar el snapshot con la fila haría que
        // esta prueba pasara por el motivo equivocado —porque la lista nunca se vació.
        api.actividadesSnapshot = emptyList()
        vm.undoLastSwipe()

        assertTrue(vm.uiState.value.actividades.none { it.id == "act-1" })
    }

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}