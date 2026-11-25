package com.parental.shared.feature.activities.ui

import androidx.lifecycle.viewModelScope
import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ConfirmAssignmentResponse
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.thirdparty.data.remote.ThirdPartiesApi
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyAccess
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S2/S6 (thirdparty-identity): los terciarios con grant ACTIVO del propio visor entran
 * al dropdown de asignatarios con su nombre declarado; los demás miembros conservan sus
 * candidatos y un back sin grants no cambia nada (S6). El servidor ya filtra por dueño,
 * así que acá no hay segunda barrera de identidad: sólo fusión de listas.
 */
class ActivitiesViewModelThirdPartyCandidatesTest {
    private val token = "t-1"
    private val bondId = "bond-1"
    private val userId = "u-self"
    private val THIRD_PARTY_ID = "u-third"

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT) {
        override suspend fun listActividades(
            bondId: String,
        ): ApiResult<List<Actividad>> = ApiResult.Success(emptyList())

        override suspend fun getActividad(
            id: String,
        ): ApiResult<ActividadDetalle> = throw NotImplementedError()

        override suspend fun confirmAssignment(
            activityId: String,
        ): ApiResult<ConfirmAssignmentResponse> = throw NotImplementedError()
    }

    private class FakeCategoriesApi : CategoriesApi(UNUSED_CLIENT) {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(emptyList())
    }

    private class FakeBondsApi : BondsApi(UNUSED_CLIENT) {
        override suspend fun getMyBonds(): ApiResult<List<Vinculo>> = ApiResult.Success(
            listOf(
                Vinculo(
                    id = "bond-1",
                    title = "Familia",
                    agreementType = "custody",
                    members = emptyList(),
                ),
            ),
        )
    }

    /** Grants del VISOR: como los produce el back (sólo los que él otorgó). */
    private class FakeThirdPartiesApi(
        private val grants: List<ThirdPartyAccess>,
        var calls: Int = 0,
    ) : ThirdPartiesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun listForBond(
            token: String,
            bondId: String,
        ): ApiResult<List<ThirdPartyAccess>> {
            calls++
            return ApiResult.Success(grants)
        }
    }

    private fun access(
        id: String = "g-1",
        userId: String = THIRD_PARTY_ID,
        status: String? = "active",
        firstName: String? = "Pablo",
        lastName: String? = "Charles",
        displayName: String? = null,
    ) = ThirdPartyAccess(
        id = id,
        bondId = bondId,
        userId = userId,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        status = status,
    )

    private fun build(thirdPartiesApi: ThirdPartiesApi?): ActividadesViewModel {
        val vm = ActividadesViewModel(
            activitiesApi = FakeActivitiesApi(),
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
            thirdPartiesApi = thirdPartiesApi,
        )
        viewModels += vm
        return vm
    }

    @Test
    fun `un grant activo agrega al terciario con su nombre declarado`() {
        val api = FakeThirdPartiesApi(listOf(access()))
        // El init del VM ya dispara loadAll(): sin llamada extra, una sola vuelta.
        val vm = build(api)

        val candidates = vm.uiState.value.memberCandidates
        assertTrue(
            candidates.any { it.id == THIRD_PARTY_ID && it.name == "Pablo Charles" },
            "S2: el terciario es opción del dropdown con su nombre completo.",
        )
        assertEquals(1, api.calls, "S2: una consulta de grants por load, no por candidato.")
    }

    @Test
    fun `firstName sin lastName construye con el nombre solo y displayName fallback`() {
        val api = FakeThirdPartiesApi(
            listOf(
                access(id = "g-1", userId = "u-third", firstName = "Pablo", lastName = null),
                access(
                    id = "g-2",
                    userId = "u-2",
                    firstName = null,
                    lastName = null,
                    displayName = "Ana Sosa",
                ),
                access(id = "g-3", userId = "u-3", firstName = null, lastName = null),
            ),
        )
        val vm = build(api)

        val byId = vm.uiState.value.memberCandidates.associateBy { it.id }
        assertEquals("Pablo", byId[THIRD_PARTY_ID]?.name, "S4: sin apellido, el nombre solo.")
        assertEquals("Ana Sosa", byId["u-2"]?.name, "S6: grants previos sin identidad degradan a displayName.")
        assertTrue(
            "u-3" !in byId,
            "S3: sin NINGÚN nombre resolvible no hay candidato (nunca " +
                "mostrar un label sin persona).",
        )
    }

    @Test
    fun `grants vencidos revocados o suspendidos no son asignables`() {
        val api = FakeThirdPartiesApi(
            listOf(
                access(status = "expired"),
                access(status = "revoked"),
                access(status = "suspended"),
            ),
        )
        val vm = build(api)
        vm.loadAll()

        assertEquals(
            emptyList(),
            vm.uiState.value.memberCandidates.filter { it.id == THIRD_PARTY_ID },
            "S2: sólo el grant ACTIVO es elegible.",
        )
    }

    @Test
    fun `sin API de grants el dropdown queda con los miembros - S6`() {
        val vm = build(null)

        assertEquals(
            emptyList(),
            vm.uiState.value.memberCandidates,
            "S6: back viejo / API ausente → candidatos de miembros, sin crash.",
        )
    }

    companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
