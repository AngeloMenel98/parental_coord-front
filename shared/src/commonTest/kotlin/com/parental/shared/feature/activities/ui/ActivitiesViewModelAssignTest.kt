package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.AssignActivityResponse
import com.parental.shared.feature.activities.domain.model.ReassignActivityResponse
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.bonds.domain.model.VinculoMemberUser
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T21 — ViewModel de "Asignar" y "Reasignar".
 *
 * Sin `Dispatchers.setMain`: el `dispatcher` se inyecta con `UnconfinedTestDispatcher` y
 * las clases API son `open`, así que se heredan y se sustituye sólo el método que importa.
 */
class ActivitiesViewModelAssignTest {

    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"
    private val otherUserId = "u-other"

    private fun member(id: String, first: String) = VinculoMember(
        id = id,
        role = "parent",
        user = VinculoMemberUser(id = id, firstName = first, lastName = "Apellido"),
    )

    private fun bond() = Vinculo(
        id = bondId,
        title = "Familia",
        agreementType = "custody",
        members = listOf(member(userId, "Yo"), member(otherUserId, "Otro")),
        children = emptyList(),
    )

    private class FakeActivitiesApi(
        private val actividades: List<Actividad> = emptyList(),
        detalle: ActividadDetalle? = null,
        private val currentUserId: String = "u-self",
    ) : ActivitiesApi(UNUSED_CLIENT, "http://unused") {

        data class AssignCall(val activityId: String, val assignedTo: String?)

        var listCalls = 0
        var assignCalls = mutableListOf<AssignCall>()
        var reassignCalls = mutableListOf<AssignCall>()

        /** El detalle que el servidor tiene ahora — la acción lo cambia, como el backend. */
        var currentDetalle: ActividadDetalle? = detalle

        /** Cuántas veces se le preguntó al servidor por el detalle. */
        var detailCalls = 0

        var assignGate: CompletableDeferred<Unit>? = null
        var reassignGate: CompletableDeferred<Unit>? = null

        var assignResult: ApiResult<AssignActivityResponse> =
            ApiResult.Success(AssignActivityResponse(id = "act-1", status = "assigned", assignedTo = "u-self"))
        var reassignResult: ApiResult<ReassignActivityResponse> =
            ApiResult.Success(ReassignActivityResponse(id = "act-1", status = "assigned", assignedTo = "u-other"))

        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> {
            listCalls++
            return ApiResult.Success(actividades)
        }

        override suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> {
            detailCalls++
            val found = currentDetalle ?: return ApiResult.Error(404, "not found")
            return ApiResult.Success(found)
        }

        override suspend fun assignActivity(
            token: String,
            id: String,
            assignedTo: String?,
        ): ApiResult<AssignActivityResponse> {
            assignCalls += AssignCall(id, assignedTo)
            assignGate?.await()
            val result = assignResult
            if (result is ApiResult.Success) applyServerTransition(result.data.status, result.data.assignedTo)
            return result
        }

        override suspend fun reassignActivity(
            token: String,
            id: String,
            assignedTo: String?,
        ): ApiResult<ReassignActivityResponse> {
            reassignCalls += AssignCall(id, assignedTo)
            reassignGate?.await()
            val result = reassignResult
            if (result is ApiResult.Success) applyServerTransition(result.data.status, result.data.assignedTo)
            return result
        }

        /**
         * Lo que hace el servidor tras la transición: el estado cambia, los flags se
         * recalculan PARA QUIEN PREGUNTA y el detalle guardado refleja los dos.
         */
        private fun applyServerTransition(status: String, assignedTo: String?) {
            currentDetalle = currentDetalle?.copy(
                status = status,
                assignedTo = assignedTo,
                canAssign = false,
                canReassign = false,
                canConfirm = assignedTo == currentUserId,
            )
        }
    }

    private class FakeCategoriesApi(private val categories: List<CategoryDto>) :
        CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> = ApiResult.Success(categories)
    }

    private class FakeBondsApi(private val vinculo: Vinculo) : BondsApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> =
            ApiResult.Success(listOf(vinculo))
    }

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private fun build(
        actividades: List<Actividad> = emptyList(),
        detalle: ActividadDetalle? = null,
    ): Pair<ActividadesViewModel, FakeActivitiesApi> {
        val api = FakeActivitiesApi(actividades = actividades, detalle = detalle, currentUserId = userId)
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(
                listOf(CategoryDto(id = "cat-1", name = "Social", criticality = "low")),
            ),
            bondsApi = FakeBondsApi(bond()),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
        )
        viewModels += vm
        return vm to api
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private fun createdDetalle() = ActividadDetalle(
        id = "act-1",
        categoryId = "cat-1",
        criticality = "low",
        title = "Actividad creada",
        type = "event",
        status = "created",
        createdBy = userId,
        createdAt = "2026-10-01T08:00:00.000Z",
        canAssign = true,
    )

    private fun notAssistingDetalle() = ActividadDetalle(
        id = "act-1",
        categoryId = "cat-1",
        criticality = "low",
        title = "Actividad declinada",
        type = "event",
        status = "not_assisting",
        createdBy = userId,
        createdAt = "2026-10-01T08:00:00.000Z",
        canReassign = true,
    )

    @Test
    fun `asignar envia el id y el destino correctos`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        vm.selectActividad("act-1")

        vm.assignActivity("act-1", otherUserId)

        assertEquals(1, api.assignCalls.size)
        assertEquals("act-1", api.assignCalls.single().activityId)
        assertEquals(otherUserId, api.assignCalls.single().assignedTo)
        assertEquals(0, api.reassignCalls.size)
    }

    @Test
    fun `asignar sin destino envia null`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        vm.selectActividad("act-1")

        vm.assignActivity("act-1", null)

        assertTrue(api.assignCalls.single().assignedTo == null)
    }

    @Test
    fun `asignar con exito vuelve a preguntarle al servidor y refetchea la lista`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        api.assignResult = ApiResult.Success(
            AssignActivityResponse(id = "act-1", status = "assigned", assignedTo = otherUserId),
        )
        vm.selectActividad("act-1")
        val detailCallsBefore = api.detailCalls

        vm.assignActivity("act-1", otherUserId)

        // El detalle NO se parchea en el cliente: se le vuelve a preguntar al
        // servidor, que es el que calcula los flags (R1/S2).
        assertEquals(detailCallsBefore + 1, api.detailCalls, "debe repreguntar el detalle")
        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content)
        assertEquals("assigned", content.actividad.status)
        assertEquals(otherUserId, content.actividad.assignedTo)
        assertFalse(content.actividad.canAssign)
        assertFalse(content.actividad.canReassign)
        assertFalse(content.actividad.canConfirm, "el que NO es el asignado no puede confirmar")
        assertEquals("Actividad asignada", vm.uiState.value.snackbarMessage)
        assertEquals(2, api.listCalls, "debe refetchear la lista")
    }

    @Test
    fun `asignarse a uno mismo deja el boton de confirmar del servidor`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        api.assignResult = ApiResult.Success(
            AssignActivityResponse(id = "act-1", status = "assigned", assignedTo = userId),
        )
        vm.selectActividad("act-1")

        vm.assignActivity("act-1", null)

        // El servidor dice que ahora soy el asignado: el botón "Confirmar asignación"
        // aparece SIN que el cliente se lo calcule.
        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content)
        assertTrue(content.actividad.canConfirm)
    }

    @Test
    fun `asignar con error avisa al usuario y no refresca`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        api.assignResult = ApiResult.Error(403, "No permitido")
        vm.selectActividad("act-1")

        vm.assignActivity("act-1", otherUserId)

        assertEquals("No permitido", vm.uiState.value.snackbarMessage)
        assertEquals(1, api.listCalls, "un error no debe refetchear")
        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content)
        assertEquals("created", content.actividad.status)
    }

    @Test
    fun `asignar dos veces seguidas emite un solo POST`() {
        val detalle = createdDetalle()
        val (vm, api) = build(detalle = detalle)
        api.assignGate = CompletableDeferred()
        vm.selectActividad("act-1")

        vm.assignActivity("act-1", otherUserId)
        assertTrue(vm.uiState.value.isAssigning)
        vm.assignActivity("act-1", otherUserId)

        assertEquals(1, api.assignCalls.size, "single-flight mientras `isAssigning`")
    }

    @Test
    fun `reasignar envia el id y el destino correctos`() {
        val detalle = notAssistingDetalle()
        val (vm, api) = build(detalle = detalle)
        vm.selectActividad("act-1")

        vm.reassignActivity("act-1", otherUserId)

        assertEquals(1, api.reassignCalls.size)
        assertEquals("act-1", api.reassignCalls.single().activityId)
        assertEquals(otherUserId, api.reassignCalls.single().assignedTo)
        assertEquals(0, api.assignCalls.size)
    }

    @Test
    fun `reasignar con exito vuelve a preguntarle al servidor y refetchea la lista`() {
        val detalle = notAssistingDetalle()
        val (vm, api) = build(detalle = detalle)
        api.reassignResult = ApiResult.Success(
            ReassignActivityResponse(id = "act-1", status = "assigned", assignedTo = otherUserId),
        )
        vm.selectActividad("act-1")
        val detailCallsBefore = api.detailCalls

        vm.reassignActivity("act-1", otherUserId)

        assertEquals(detailCallsBefore + 1, api.detailCalls, "debe repreguntar el detalle")
        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content)
        assertEquals("assigned", content.actividad.status)
        assertEquals(otherUserId, content.actividad.assignedTo)
        assertFalse(content.actividad.canAssign)
        assertFalse(content.actividad.canReassign)
        assertEquals("Actividad reasignada", vm.uiState.value.snackbarMessage)
        assertEquals(2, api.listCalls, "debe refetchear la lista")
    }

    @Test
    fun `reasignar con error avisa al usuario y no refresca`() {
        val detalle = notAssistingDetalle()
        val (vm, api) = build(detalle = detalle)
        api.reassignResult = ApiResult.Error(409, "Ya pasó")
        vm.selectActividad("act-1")

        vm.reassignActivity("act-1", otherUserId)

        assertEquals("Ya pasó", vm.uiState.value.snackbarMessage)
        assertEquals(1, api.listCalls, "un error no debe refetchear")
        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content)
        assertEquals("not_assisting", content.actividad.status)
    }

    @Test
    fun `reasignar dos veces seguidas emite un solo POST`() {
        val detalle = notAssistingDetalle()
        val (vm, api) = build(detalle = detalle)
        api.reassignGate = CompletableDeferred()
        vm.selectActividad("act-1")

        vm.reassignActivity("act-1", otherUserId)
        assertTrue(vm.uiState.value.isReassigning)
        vm.reassignActivity("act-1", otherUserId)

        assertEquals(1, api.reassignCalls.size, "single-flight mientras `isReassigning`")
    }

    private companion object {
        val UNUSED_CLIENT: HttpClient by lazy { HttpClient { } }
    }
}
