package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ConfirmAssignmentResponse
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S11 · A-5…A-7 — estado del filtro de hijos en el ViewModel.
 */
class ActivitiesViewModelChildrenFilterTest {

    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun listActividades(
            token: String,
            bondId: String,
        ): ApiResult<List<Actividad>> = ApiResult.Success(emptyList())

        override suspend fun getActividad(
            token: String,
            id: String,
        ): ApiResult<ActividadDetalle> = throw NotImplementedError()

        override suspend fun confirmAssignment(
            token: String,
            activityId: String,
        ): ApiResult<ConfirmAssignmentResponse> = throw NotImplementedError()
    }

    private class FakeCategoriesApi : CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(emptyList())
    }

    private class FakeBondsApi(
        private val children: List<VinculoChild>,
        private val bondId: String,
    ) : BondsApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> =
            ApiResult.Success(
                listOf(
                    Vinculo(
                        id = bondId,
                        title = "Familia",
                        agreementType = "custody",
                        members = emptyList(),
                        children = children,
                    ),
                ),
            )
    }

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private fun build(children: List<VinculoChild>): ActividadesViewModel {
        val vm = ActividadesViewModel(
            activitiesApi = FakeActivitiesApi(),
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(children, bondId),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
        )
        viewModels += vm
        return vm
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `la seleccion inicia vacia`() {
        val vm = build(
            listOf(
                VinculoChild(id = "c1", firstName = "Ana", lastName = "García"),
                VinculoChild(id = "c2", firstName = "Luis", lastName = "García"),
            ),
        )

        assertTrue(vm.uiState.value.selectedChildren.isEmpty())
    }

    @Test
    fun `toggle agrega y quita un hijo`() {
        val vm = build(
            listOf(
                VinculoChild(id = "c1", firstName = "Ana", lastName = ""),
                VinculoChild(id = "c2", firstName = "Luis", lastName = ""),
            ),
        )

        vm.toggleChildFilter("c1")
        assertEquals(setOf("c1"), vm.uiState.value.selectedChildren)

        vm.toggleChildFilter("c2")
        assertEquals(setOf("c1", "c2"), vm.uiState.value.selectedChildren)

        vm.toggleChildFilter("c1")
        assertEquals(setOf("c2"), vm.uiState.value.selectedChildren)
    }

    @Test
    fun `clearChildFilter vacia la seleccion`() {
        val vm = build(
            listOf(
                VinculoChild(id = "c1", firstName = "Ana", lastName = ""),
                VinculoChild(id = "c2", firstName = "Luis", lastName = ""),
            ),
        )

        vm.toggleChildFilter("c1")
        vm.clearChildFilter()

        assertTrue(vm.uiState.value.selectedChildren.isEmpty())
    }

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
