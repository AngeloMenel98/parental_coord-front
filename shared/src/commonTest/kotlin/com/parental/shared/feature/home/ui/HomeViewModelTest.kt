package com.parental.shared.feature.home.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceMember
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeSummary
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S2 · HomeViewModel debe renderizar el cumplimiento que ya viene en el summary,
 * sin hacer un segundo viaje a la API.
 *
 * Sigue el patrón de `NotificationsViewModelTest`: `UnconfinedTestDispatcher`
 * inyectado y la API sustituida por un fake.
 */
class HomeViewModelTest {

    private val token = "t-1"
    private val userName = "Juan"
    private val viewModels = mutableListOf<HomeViewModel>()

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }

    private class FakeHomeApi : HomeApi(UNUSED_CLIENT, "http://unused") {
        var calls = 0
        var result: ApiResult<HomeSummary> = ApiResult.Success(
            HomeSummary(children = emptyList())
        )

        override suspend fun getHomeSummary(token: String, currentUserId: String?): ApiResult<HomeSummary> {
            calls++
            return result
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private fun build(api: FakeHomeApi): HomeViewModel {
        val viewModel = HomeViewModel(
            homeApi = api,
            token = token,
            userName = userName,
            dispatcher = UnconfinedTestDispatcher(),
        )
        viewModels += viewModel
        return viewModel
    }

    private fun compliance() = ComplianceResponse(
        bondId = "b1",
        period = "2026-10",
        members = listOf(
            ComplianceMember(
                userId = "u1",
                firstName = "Juan",
                lastName = "Pérez",
                completed = 7,
                total = 10,
                percentage = 0.7,
            )
        )
    )

    @Test
    fun `carga el summary con cumplimiento en un solo viaje`() {
        val summary = HomeSummary(
            children = listOf(ChildProfile("c1", "Ana", "Pérez", "")),
            memberCount = 2,
            bondId = "b1",
            compliance = compliance(),
        )
        val api = FakeHomeApi().apply { result = ApiResult.Success(summary) }

        val viewModel = build(api)

        assertEquals(1, api.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(summary, viewModel.uiState.value.summary)
        assertEquals(compliance(), viewModel.uiState.value.summary?.compliance)
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.complianceError)
    }

    @Test
    fun `muestra error de cumplimiento cuando hay vinculo pero compliance es nulo`() {
        val summary = HomeSummary(
            children = emptyList(),
            memberCount = 2,
            bondId = "b1",
            compliance = null,
        )
        val api = FakeHomeApi().apply { result = ApiResult.Success(summary) }

        val viewModel = build(api)

        assertEquals(1, api.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("b1", viewModel.uiState.value.summary?.bondId)
        assertEquals("No se pudo cargar el cumplimiento", viewModel.uiState.value.complianceError)
    }

    @Test
    fun `no muestra error de cumplimiento cuando no hay vinculo`() {
        val summary = HomeSummary(
            children = emptyList(),
            memberCount = 0,
            bondId = null,
            compliance = null,
        )
        val api = FakeHomeApi().apply { result = ApiResult.Success(summary) }

        val viewModel = build(api)

        assertEquals(1, api.calls)
        assertNull(viewModel.uiState.value.summary?.bondId)
        assertNull(viewModel.uiState.value.complianceError)
    }

    @Test
    fun `propaga el error general del summary`() {
        val api = FakeHomeApi().apply {
            result = ApiResult.Error(500, "home unavailable")
        }

        val viewModel = build(api)

        assertEquals(1, api.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("HTTP 500: home unavailable", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `retry vuelve a pedir el summary`() {
        val summary = HomeSummary(
            children = emptyList(),
            bondId = "b1",
            compliance = compliance(),
        )
        val api = FakeHomeApi().apply {
            result = ApiResult.Error(500, "fail")
        }
        val viewModel = build(api)

        api.result = ApiResult.Success(summary)
        viewModel.loadHomeData()

        assertEquals(2, api.calls)
        assertEquals(summary, viewModel.uiState.value.summary)
        assertNull(viewModel.uiState.value.error)
    }
}
