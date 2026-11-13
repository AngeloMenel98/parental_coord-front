package com.parental.shared.feature.thirdparty.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.CompleteActivityResponse
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.bonds.domain.model.VinculoMemberUser
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El estado de la vista del terciario: qué publica, qué degrada y qué recuerda.
 *
 * Cubre las reglas que la UI no puede probar por sí sola (S6, D3, L7) con las APIs
 * sustituidas, igual que `HomeViewModelTest`: `UnconfinedTestDispatcher` inyectado y
 * `destroy()` al final.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThirdPartyViewModelTest {

    private val token = "t-1"
    private val bondId = "bond-1"
    private val me = "user-me"
    private val viewModels = mutableListOf<ThirdPartyViewModel>()

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
        val TODAY: LocalDate = LocalDate(2026, 11, 3)
        val GRANT = ThirdPartyGrant(
            bondId = "bond-1",
            userId = "user-me",
            relationship = "FAMILY",
            startDate = "2026-10-01",
            endDate = "2026-12-12",
            authorizedBy = "p-1",
            authorizedByName = "Ángelo",
        )
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `solo publica las actividades asignadas al visitante`() {
        val activitiesApi = FakeActivitiesApi().apply {
            activities = ApiResult.Success(
                listOf(
                    actividad("a-1", "assisting", me),
                    actividad("a-2", "in_progress", me),
                    actividad("a-3", "assisting", "otro-usuario"),
                    actividad("a-4", "created", null),
                ),
            )
        }
        val viewModel = viewModel(activitiesApi = activitiesApi)

        viewModel.load()

        assertEquals(
            listOf("a-1", "a-2"),
            viewModel.state.value.activities.map { it.id },
            "🔴 S6: la lista del terciario sólo tiene lo asignado a él.",
        )
    }

    @Test
    fun `sin grant y sin progenitor derivable no hay tarjeta de permiso`() {
        val viewModel = viewModel(
            grantApi = FakeGrantApi().apply { result = ApiResult.Error(404, "Not found") },
            bondsApi = FakeBondsApi().apply { result = ApiResult.Error(500, "boom") },
        )

        viewModel.load()

        assertNull(
            viewModel.state.value.permit,
            "🔴 D3: sin fecha de vencimiento no se pinta 'acceso temporal'.",
        )
        assertFalse(
            viewModel.state.value.showPermitDialog,
            "🔴 L7: sin permiso no hay popup que anunciar.",
        )
    }

    @Test
    fun `un 403 del grant no cae en el progenitor derivado`() {
        val viewModel = viewModel(
            grantApi = FakeGrantApi().apply {
                result = ApiResult.Error(403, "Tu acceso temporal terminó")
            },
            bondsApi = FakeBondsApi().apply {
                result = ApiResult.Success(
                    listOf(
                        Vinculo(
                            id = bondId,
                            members = listOf(
                                VinculoMember(
                                    id = "m-1",
                                    role = "progenitor",
                                    user = VinculoMemberUser(id = "p-1", firstName = "Ángelo"),
                                ),
                            ),
                        ),
                    ),
                )
            },
        )

        viewModel.load()

        assertNull(
            viewModel.state.value.permit,
            "🔴 T10: con el acceso revocado no hay tarjeta de permiso que mostrar.",
        )
        assertFalse(
            viewModel.state.value.showPermitDialog,
            "🔴 L7: y tampoco se abre el popup de bienvenida.",
        )
    }

    @Test
    fun `el popup de permiso aparece la primera vez y no vuelve solo`() {
        val first = viewModel(loadFlag = { null })
        first.load()
        assertTrue(
            first.state.value.showPermitDialog,
            "🔴 L7: el permiso se explica en el primer ingreso.",
        )

        val seen = viewModel(loadFlag = { key ->
            if (key.endsWith(me)) "true" else null
        })
        seen.load()
        assertFalse(
            seen.state.value.showPermitDialog,
            "🔴 L7: con la marca guardada el popup no se repite.",
        )
    }

    @Test
    fun `los dias restantes salen de la fecha de fin del grant`() {
        val viewModel = viewModel()

        viewModel.load()

        val permit = assertNotNull(viewModel.state.value.permit, "🔴 D3: hay tarjeta con grant completo.")
        assertEquals(39, permit.daysLeft, "🔴 D3: del 3 de noviembre al 12 de diciembre son 39 días.")
        assertEquals("Ángelo", permit.authorizedByName, "🔴 S12: el permiso nombra a quien autorizó.")
        assertEquals("Familiar", permit.relationship, "🔴 D3: la relación se muestra en palabras.")
    }

    @Test
    fun `finalizar avisa y vuelve a pedir la lista`() {
        val activitiesApi = FakeActivitiesApi().apply {
            activities = ApiResult.Success(listOf(actividad("a-1", "verify", me, canComplete = true)))
        }
        val viewModel = viewModel(activitiesApi = activitiesApi)
        viewModel.load()

        viewModel.complete("a-1")

        assertEquals(1, activitiesApi.completeCalls, "🔴 S8: finalizar llama al endpoint de completar.")
        assertEquals(2, activitiesApi.listCalls, "🔴 S11: tras finalizar la lista se refresca.")
        assertNotNull(viewModel.state.value.message, "🔴 S11: el usuario recibe confirmación.")
        assertEquals(ThirdPartyDetail.Closed, viewModel.state.value.detail, "🔴 S11: la hoja se cierra.")
    }

    @Test
    fun `si finalizar falla se conserva la lista y se muestra el error`() {
        val activitiesApi = FakeActivitiesApi().apply {
            activities = ApiResult.Success(listOf(actividad("a-1", "verify", me, canComplete = true)))
            completeResult = ApiResult.Error(403, "No autorizado")
        }
        val viewModel = viewModel(activitiesApi = activitiesApi)
        viewModel.load()

        viewModel.complete("a-1")

        assertEquals("No autorizado", viewModel.state.value.message, "🔴 S13: el error del servidor se muestra.")
        assertEquals(1, activitiesApi.listCalls, "🔴 S13: un fallo no recarga la lista.")
        assertEquals(1, viewModel.state.value.activities.size, "🔴 S13: la tarea sigue en la lista.")
    }

    private fun viewModel(
        grantApi: FakeGrantApi = FakeGrantApi(),
        activitiesApi: FakeActivitiesApi = FakeActivitiesApi(),
        categoriesApi: FakeCategoriesApi = FakeCategoriesApi(),
        bondsApi: FakeBondsApi = FakeBondsApi(),
        loadFlag: (String) -> String? = { null },
        saveFlag: (String, String) -> Unit = { _, _ -> },
    ): ThirdPartyViewModel = ThirdPartyViewModel(
        thirdPartiesApi = grantApi,
        activitiesApi = activitiesApi,
        categoriesApi = categoriesApi,
        bondsApi = bondsApi,
        token = token,
        bondId = bondId,
        currentUserId = me,
        userName = "Ana",
        dispatcher = UnconfinedTestDispatcher(),
        loadFlag = loadFlag,
        saveFlag = saveFlag,
        today = { TODAY },
    ).also { viewModels += it }

    private fun actividad(
        id: String,
        status: String,
        assignedTo: String?,
        canComplete: Boolean = false,
    ) = Actividad(
        id = id,
        title = "Tarea $id",
        type = "obligation",
        status = status,
        categoryId = "cat-1",
        criticality = "medium",
        scheduledStart = "2026-11-03T08:30:00.000Z",
        assignedTo = assignedTo,
        canComplete = canComplete,
    )

    private class FakeGrantApi : ThirdPartiesApi(UNUSED_CLIENT, "http://unused") {
        var result: ApiResult<ThirdPartyGrant> = ApiResult.Success(GRANT)
        override suspend fun getMyGrant(token: String): ApiResult<ThirdPartyGrant> = result
    }

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        var activities: ApiResult<List<Actividad>> = ApiResult.Success(emptyList())
        var listCalls = 0
        var completeCalls = 0
        var completeResult: ApiResult<CompleteActivityResponse> =
            ApiResult.Success(CompleteActivityResponse(id = "a-1", status = "verify"))

        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> {
            listCalls++
            return activities
        }

        override suspend fun completeActivity(
            token: String,
            id: String,
        ): ApiResult<CompleteActivityResponse> {
            completeCalls++
            return completeResult
        }

        override suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> =
            ApiResult.Error(404, "no usado")

        override suspend fun listAttachments(
            token: String,
            activityId: String,
        ): ApiResult<List<ActivityAttachmentDto>> = ApiResult.Success(emptyList())
    }

    private class FakeCategoriesApi : CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(emptyList())
    }

    private class FakeBondsApi : BondsApi(UNUSED_CLIENT, "http://unused") {
        var result: ApiResult<List<Vinculo>> = ApiResult.Success(emptyList())
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> = result
    }
}
