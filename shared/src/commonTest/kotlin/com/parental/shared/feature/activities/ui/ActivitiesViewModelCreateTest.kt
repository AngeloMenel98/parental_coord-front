package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.CompleteActivityResponse
import com.parental.shared.feature.activities.domain.model.CreateActividadRequest
import com.parental.shared.feature.activities.domain.model.CreatedActividad
import com.parental.shared.feature.activities.ui.create.CreateForm
import com.parental.shared.feature.activities.ui.create.TimeOfDay
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.bonds.domain.model.VinculoMemberUser
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-FE-13 — ViewModel del alta (`create-activity`, D17/D19/D20/D21 + R9).
 *
 * Sin `Dispatchers.setMain` y sin fake de `HttpClient` (D17/D18): el `dispatcher` se inyecta
 * con un `UnconfinedTestDispatcher` y las tres clases API son `open class`, así que aquí se
 * **heredan** y se sustituye sólo el método que importa.
 *
 * Lo que se fija, en una línea por test:
 * · un hijo ya preseleccionado al abrir · el guard de validación corta ANTES de la red ·
 * · un solo POST con doble `submitCreate` · `Error` conserva el formulario (D19) ·
 * · éxito ⇒ `Closed` + reset + refetch (D21) · una respuesta tardía NO escribe (R9) ·
 * · `criticality` no viaja en el cuerpo (AS-3) · "Finalizar" parchea con lo que dice el servidor.
 */
class ActivitiesViewModelCreateTest {

    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"
    private val otherUserId = "u-other"

    private fun child(id: String, first: String) =
        VinculoChild(id = id, firstName = first, lastName = "Apellido")

    private fun member(id: String, role: String, first: String) =
        VinculoMember(
            id = id,
            role = role,
            user = VinculoMemberUser(id = id, firstName = first, lastName = "Apellido"),
        )

    private fun bond(children: List<VinculoChild>) = Vinculo(
        id = bondId,
        title = "Familia",
        agreementType = "custody",
        members = listOf(member(userId, "parent", "Yo"), member(otherUserId, "parent", "Otro")),
        children = children,
    )

    /**
     * Doble de [ActivitiesApi]. ⚠ `client` no se usa: TODOS los métodos que el ViewModel llama
     * están sobrescritos, así que basta cualquier instancia para satisfacer el constructor.
     */
    private class FakeActivitiesApi(
        private val actividades: List<Actividad> = emptyList(),
        private val detalle: ActividadDetalle? = null,
    ) : ActivitiesApi(UNUSED_CLIENT, "http://unused") {

        var listCalls = 0
        var createCalls = 0
        var completeCalls = 0
        val requests = mutableListOf<CreateActividadRequest>()

        /** Si está presente, `createActividad` se queda suspendida hasta que se complete. */
        var createGate: CompletableDeferred<Unit>? = null

        /**
         * Ídem para `completeActivity`. ⚠ NECESARIO y no cosmético: con `UnconfinedTestDispatcher`
         * un `launch` sin suspensión se ejecuta ENTERO de forma síncrona, así que el `isCompleting`
         * vuelve a `false` antes de la siguiente llamada y la guarda single-flight nunca se
         * ejercita. Sin este gate, el test "dos clicks ⇒ un POST" pasaría sin comprobar nada.
         */
        var completeGate: CompletableDeferred<Unit>? = null

        var createResult: ApiResult<CreatedActividad> =
            ApiResult.Success(CreatedActividad(id = "act-1", title = "t", status = "created"))
        var completeResult: ApiResult<CompleteActivityResponse> =
            ApiResult.Success(CompleteActivityResponse(id = "act-1", status = "done", completedAt = "2026-10-01T12:00:00.000Z"))

        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> {
            listCalls++
            return ApiResult.Success(actividades)
        }

        override suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> {
            val found = detalle ?: return ApiResult.Error(404, "not found")
            return ApiResult.Success(found)
        }

        override suspend fun createActividad(
            token: String,
            bondId: String,
            request: CreateActividadRequest,
        ): ApiResult<CreatedActividad> {
            createCalls++
            requests += request
            createGate?.await()
            return createResult
        }

        override suspend fun completeActivity(
            token: String,
            id: String,
        ): ApiResult<CompleteActivityResponse> {
            completeCalls++
            completeGate?.await()
            return completeResult
        }
    }

    private class FakeCategoriesApi(private val categories: List<CategoryDto>) :
        CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(categories)
    }

    private class FakeBondsApi(private val vinculo: Vinculo) :
        BondsApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> =
            ApiResult.Success(listOf(vinculo))
    }

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private fun build(children: List<VinculoChild>, detalle: ActividadDetalle? = null): Pair<ActividadesViewModel, FakeActivitiesApi> {
        val api = FakeActivitiesApi(detalle = detalle)
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(
                listOf(
                    CategoryDto(id = "cat-salud", name = "Salud", criticality = "critical"),
                    CategoryDto(id = "cat-social", name = "Social", criticality = "low"),
                ),
            ),
            bondsApi = FakeBondsApi(bond(children)),
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

    private fun validForm(children: Set<String>) = CreateForm(
        title = "Ir al médico",
        description = "Revisión",
        categoryId = "cat-salud",
        childrenIds = children,
        startDate = LocalDate(2026, 10, 5),
        startTime = TimeOfDay(10, 0),
    )

    // ── apertura ──────────────────────────────────────────────────────────

    @Test
    fun `al abrir con un solo hijo ese hijo queda preseleccionado`() {
        val (vm, _) = build(children = listOf(child("c-1", "Ana")))

        vm.openCreate()

        assertEquals(setOf("c-1"), vm.uiState.value.createForm.childrenIds)
        assertTrue(vm.uiState.value.create is CreateUiState.Content)
    }

    @Test
    fun `al abrir con varios hijos no se presupone ninguno`() {
        val (vm, _) = build(children = listOf(child("c-1", "Ana"), child("c-2", "Beto")))

        vm.openCreate()

        assertTrue(vm.uiState.value.createForm.childrenIds.isEmpty())
    }

    @Test
    fun `createDirty arranca en false y se enciende al tocar un campo`() {
        val (vm, _) = build(children = listOf(child("c-1", "Ana")))

        vm.openCreate()
        assertFalse(vm.uiState.value.createDirty, "el hijo preseleccionado NO ensucia el formulario")

        vm.setCreateTitle("Ir al médico")
        assertTrue(vm.uiState.value.createDirty)

        vm.setCreateTitle("")
        assertFalse(vm.uiState.value.createDirty, "volver al valor inicial lo vuelve limpio")
    }

    @Test
    fun `los hijos y los candidatos se derivan sin peticiones nuevas`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana"), child("c-2", "Beto")))

        assertEquals(listOf("c-1", "c-2"), vm.uiState.value.children.map { it.id })
        // AS-9: "Sin asignar" es el `null` del desplegable; aquí van los miembros reales.
        assertEquals(setOf(userId, otherUserId), vm.uiState.value.memberCandidates.map { it.id }.toSet())
        assertEquals(1, api.listCalls, "loadAll() no se multiplica por el modal")
    }

    // ── guardas ───────────────────────────────────────────────────────────

    @Test
    fun `un formulario invalido no sale ni a la red`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        vm.openCreate()
        vm.setCreateTitle("AB") // < 3 caracteres

        vm.submitCreate()

        assertEquals(0, api.createCalls)
        assertTrue(vm.uiState.value.create is CreateUiState.Content, "no debe pasar a Loading")
    }

    @Test
    fun `el doble envio emite un solo POST`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        vm.openCreate()
        // ⚠ El gate se abre ANTES de rellenar: `createActividad` lo espera, así que el primer
        //   `submitCreate` se queda de verdad EN VOLO y `create is Loading` sigue siendo cierto
        //   para los siguientes. Sin esto el doble se prueba contra un POST ya resuelto.
        api.createGate = CompletableDeferred()

        vm.setCreateTitle("Ir al médico")
        vm.setCreateDescription("Revisión")
        vm.setCreateCategory("cat-salud")
        vm.setCreateDate(LocalDate(2026, 10, 5))
        vm.setCreateTime(TimeOfDay(10, 0))

        vm.submitCreate()
        assertTrue(vm.uiState.value.create is CreateUiState.Loading)
        vm.submitCreate()
        vm.submitCreate()

        assertEquals(1, api.createCalls, "doble envío ⇒ un único POST")
    }

    // ── éxito / error ────────────────────────────────────────────────────

    @Test
    fun `el exito cierra el modal, resetea el formulario y refetchea`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        vm.openCreate()
        vm.setCreateTitle("Ir al médico")
        vm.setCreateCategory("cat-salud")
        vm.setCreateDate(LocalDate(2026, 10, 5))

        vm.submitCreate()

        assertTrue(vm.uiState.value.create is CreateUiState.Closed)
        assertEquals(CreateForm(), vm.uiState.value.createForm)
        assertFalse(vm.uiState.value.createDirty)
        assertEquals("Actividad creada", vm.uiState.value.snackbarMessage)
        assertEquals(2, api.listCalls, "D21: refetch, no inserción optimista")
    }

    @Test
    fun `un error de red conserva el formulario integro (D19)`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        api.createResult = ApiResult.Error(0, "POST failed: no connection")
        vm.openCreate()
        vm.setCreateTitle("Ir al médico")
        vm.setCreateDescription("Revisión")
        vm.setCreateCategory("cat-salud")
        vm.setCreateDate(LocalDate(2026, 10, 5))
        val before = vm.uiState.value.createForm

        vm.submitCreate()

        val error = vm.uiState.value.create
        assertTrue(error is CreateUiState.Error, "debe quedar en Error, se esperaba $error")
        assertEquals("POST failed: no connection", error.message)
        assertEquals(before, vm.uiState.value.createForm, "⚠ D19: el error NO puede perder datos")
        assertEquals(1, api.listCalls, "un error NO dispara refetch")
    }

    @Test
    fun `criticality nunca viaja en el cuerpo (AS-3 y CC-5)`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        vm.openCreate()
        vm.setCreateTitle("Ir al médico")
        vm.setCreateCategory("cat-salud") // Salud ⇒ critical en el servidor
        vm.setCreateDate(LocalDate(2026, 10, 5))

        vm.submitCreate()

        val body = api.requests.single()
        // El cuerpo se construye con `CreateActividadRequest`, que NO declara el campo: la
        // criticidad es read-only en la UI y la deriva el backend (spec CC-5).
        assertEquals("event", body.type)
        assertEquals("cat-salud", body.categoryId)
        assertNull(body.assignedTo)
        assertTrue(body.scheduledStart.endsWith("Z"), "ISO-8601 con offset explícito obligatorio")
    }

    @Test
    fun `una respuesta tardia tras cerrar el modal no escribe nada (R9)`() {
        val (vm, api) = build(children = listOf(child("c-1", "Ana")))
        vm.openCreate()
        vm.setCreateTitle("Ir al médico")
        vm.setCreateCategory("cat-salud")
        vm.setCreateDate(LocalDate(2026, 10, 5))
        val gate = CompletableDeferred<Unit>()
        api.createGate = gate

        vm.submitCreate()          // sale en vuelo
        vm.closeCreate()           // el usuario descarta con el POST en vuelo
        gate.complete(Unit)      // llega la respuesta TARDE

        assertTrue(vm.uiState.value.create is CreateUiState.Closed, "no debe reabrir la hoja")
        assertNull(vm.uiState.value.snackbarMessage, "ni pintar un error fantasma en la lista")
        assertEquals(1, api.listCalls, "ni refetch: la respuesta es obsoleta")
    }

    // ── "Finalizar" (AL-4) ───────────────────────────────────────────────

    @Test
    fun `finalizar parchea el detalle con los valores del servidor`() {
        val detalle = ActividadDetalle(
            id = "act-1",
            categoryId = "cat-salud",
            criticality = "critical",
            title = "Ir al médico",
            type = "event",
            status = "assigned",
            createdBy = userId,
            createdAt = "2026-10-01T08:00:00.000Z",
        )
        val (vm, api) = build(children = listOf(child("c-1", "Ana")), detalle = detalle)
        vm.selectActividad("act-1")

        vm.completeActivity("act-1")

        val content = vm.uiState.value.detail
        assertTrue(content is DetailUiState.Content, "la hoja sigue viva tras finalizar")
        assertEquals("done", content.actividad.status)
        assertEquals("2026-10-01T12:00:00.000Z", content.actividad.completedAt)
        assertFalse(vm.uiState.value.isCompleting)
        assertEquals("Actividad finalizada", vm.uiState.value.snackbarMessage)
        assertEquals(1, api.completeCalls)
    }

    @Test
    fun `finalizar dos veces seguidas emite un solo POST`() {
        val detalle = ActividadDetalle(
            id = "act-1",
            categoryId = "cat-salud",
            criticality = "critical",
            title = "Ir al médico",
            type = "event",
            status = "assigned",
            createdBy = userId,
            createdAt = "2026-10-01T08:00:00.000Z",
        )
        val (vm, api) = build(children = listOf(child("c-1", "Ana")), detalle = detalle)
        vm.selectActividad("act-1")
        // ⚠ Gate ABIERTO: el primer `completeActivity` queda en vuelo de verdad. Con un doble que
        //   resolviese al instante, el segundo click vería `isCompleting == false` y la guarda no
        //   se probaría (ver la nota de `completeGate`).
        api.completeGate = CompletableDeferred()

        vm.completeActivity("act-1")
        assertTrue(vm.uiState.value.isCompleting)
        vm.completeActivity("act-1")

        assertEquals(1, api.completeCalls, "single-flight mientras `isCompleting`")
    }

    private companion object {
        /**
         * Cliente que NUNCA se usa: todos los métodos que el ViewModel invoca están sobrescritos
         * en los dobles. Existe sólo para satisfacer el constructor de las clases `open`.
         * ⚠ Nunca se emite una petición por él.
         */
        val UNUSED_CLIENT: HttpClient by lazy { HttpClient { } }
    }
}