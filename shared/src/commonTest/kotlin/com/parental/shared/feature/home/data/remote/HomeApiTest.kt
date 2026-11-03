package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryCountsDto
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto
import com.parental.shared.feature.home.domain.model.ComplianceMember
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S2 · HomeApi debe devolver el cumplimiento dentro de HomeSummary en un solo viaje.
 *
 * Sigue el patrón de `NotificationsViewModelTest`: la API es `open`, los métodos son
 * `open suspend fun`, y `BondsApi` se inyecta para poder usar un fake sin HTTP.
 */
class HomeApiTest {

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }

    private class FakeBondsApi(var result: ApiResult<List<Vinculo>>) : BondsApi(UNUSED_CLIENT, "http://unused") {
        var calls = 0
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> {
            calls++
            return result
        }
    }

    private fun bond(
        id: String = "b1",
        children: List<VinculoChild> = emptyList(),
        members: List<VinculoMember> = emptyList(),
    ) = Vinculo(
        id = id,
        children = children,
        members = members,
    )

    private fun child(id: String, firstName: String, lastName: String) =
        VinculoChild(id = id, firstName = firstName, lastName = lastName)

    private fun member(id: String) = VinculoMember(id = id, role = "parent")

    private fun complianceResponse(bondId: String = "b1") = ComplianceResponse(
        bondId = bondId,
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
    fun `buildHomeSummary agrega hijos de todos los vinculos`() {
        val bonds = listOf(
            bond(id = "b1", children = listOf(child("c1", "Ana", "Pérez"))),
            bond(id = "b2", children = listOf(child("c2", "Luis", "García"))),
        )

        val summary = buildHomeSummary(bonds, compliance = null)

        assertEquals(listOf("c1", "c2"), summary.children.map { it.id })
        assertEquals(listOf("Ana", "Luis"), summary.children.map { it.firstName })
    }

    @Test
    fun `buildHomeSummary toma el primer vinculo para bondId y memberCount`() {
        val bonds = listOf(
            bond(id = "b1", members = listOf(member("m1"), member("m2"))),
            bond(id = "b2", members = listOf(member("m3"))),
        )

        val summary = buildHomeSummary(bonds, compliance = null)

        assertEquals("b1", summary.bondId)
        assertEquals(2, summary.memberCount)
    }

    @Test
    fun `buildHomeSummary incluye el cumplimiento cuando esta presente`() {
        val bonds = listOf(bond())
        val compliance = complianceResponse()

        val summary = buildHomeSummary(bonds, compliance)

        assertEquals(compliance, summary.compliance)
    }

    @Test
    fun `buildHomeSummary deja compliance nulo cuando el llamado fallo`() {
        val bonds = listOf(bond())

        val summary = buildHomeSummary(bonds, compliance = null)

        assertNull(summary.compliance)
        assertEquals("b1", summary.bondId)
    }

    @Test
    fun `getHomeSummary puebla compliance desde el primer vinculo`() = runTest {
        val bondsApi = FakeBondsApi(ApiResult.Success(listOf(bond())))
        val compliance = complianceResponse()
        val homeApi = object : HomeApi(UNUSED_CLIENT, "http://unused", bondsApi) {
            override suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
                assertEquals("b1", bondId)
                assertEquals("t-1", token)
                return ApiResult.Success(compliance)
            }
        }

        val result = homeApi.getHomeSummary("t-1")

        assertEquals(1, bondsApi.calls)
        assertTrue(result is ApiResult.Success)
        assertEquals(compliance, result.data.compliance)
        assertEquals("b1", result.data.bondId)
    }

    @Test
    fun `getHomeSummary devuelve el error de bonds sin llamar a compliance`() = runTest {
        val bondsApi = FakeBondsApi(ApiResult.Error(500, " bonds down"))
        var complianceCalled = false
        val homeApi = object : HomeApi(UNUSED_CLIENT, "http://unused", bondsApi) {
            override suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
                complianceCalled = true
                return ApiResult.Success(complianceResponse())
            }
        }

        val result = homeApi.getHomeSummary("t-1")

        assertEquals(1, bondsApi.calls)
        assertTrue(!complianceCalled)
        assertTrue(result is ApiResult.Error)
        assertEquals(500, (result as ApiResult.Error).code)
    }

    @Test
    fun `getHomeSummary no llama a compliance si no hay vinculos`() = runTest {
        val bondsApi = FakeBondsApi(ApiResult.Success(emptyList()))
        var complianceCalled = false
        val homeApi = object : HomeApi(UNUSED_CLIENT, "http://unused", bondsApi) {
            override suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
                complianceCalled = true
                return ApiResult.Success(complianceResponse())
            }
        }

        val result = homeApi.getHomeSummary("t-1")

        assertEquals(1, bondsApi.calls)
        assertTrue(!complianceCalled)
        assertTrue(result is ApiResult.Success)
        assertNull(result.data.bondId)
        assertNull(result.data.compliance)
    }

    // ── T20 · Las tres métricas del Home ────────────────────────────────────

    private class FakeActivitiesApi(var result: ApiResult<List<Actividad>>) :
        ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        var calls = 0
        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> {
            calls++
            return result
        }
    }

    private class FakeExpensesApi(var result: ApiResult<ExpenseSummaryDto>) :
        ExpensesApi(UNUSED_CLIENT, "http://unused") {
        var calls = 0
        override suspend fun getSummary(token: String, bondId: String): ApiResult<ExpenseSummaryDto> {
            calls++
            return result
        }
    }

    private fun activity(status: String, assignedTo: String? = "u1") = Actividad(
        id = "a-$status-$assignedTo",
        title = "Actividad",
        type = "event",
        status = status,
        categoryId = "cat-1",
        criticality = "medium",
        assignedTo = assignedTo,
    )

    private fun expenseSummary(saldo: Double) = ExpenseSummaryDto(
        saldo = saldo,
        porPagar = 0.0,
        porCobrar = 0.0,
        counts = ExpenseSummaryCountsDto(pendientes = 0, aprobados = 0, pagados = 0, rechazados = 0),
    )

    @Test
    fun `los conteos y el saldo llegan al resumen`() = runTest {
        val activitiesApi = FakeActivitiesApi(
            ApiResult.Success(
                listOf(
                    activity("assigned"),
                    activity("overdue"),
                    activity("done"),
                )
            )
        )
        val expensesApi = FakeExpensesApi(ApiResult.Success(expenseSummary(saldo = -120.5)))
        val homeApi = HomeApi(UNUSED_CLIENT, "http://unused", FakeBondsApi(ApiResult.Success(listOf(bond()))), activitiesApi, expensesApi)

        val result = homeApi.getHomeSummary("t-1", currentUserId = "u1")

        assertTrue(result is ApiResult.Success)
        assertEquals(2, result.data.metrics.assignedToMe)
        assertEquals(1, result.data.metrics.overdue)
        assertEquals(-120.5, result.data.metrics.balance)
        assertEquals(1, activitiesApi.calls)
        assertEquals(1, expensesApi.calls)
    }

    @Test
    fun `si la lista falla los conteos quedan sin dato y el saldo sigue`() = runTest {
        val activitiesApi = FakeActivitiesApi(ApiResult.Error(500, "activities down"))
        val expensesApi = FakeExpensesApi(ApiResult.Success(expenseSummary(saldo = 30.0)))
        val homeApi = HomeApi(UNUSED_CLIENT, "http://unused", FakeBondsApi(ApiResult.Success(listOf(bond()))), activitiesApi, expensesApi)

        val result = homeApi.getHomeSummary("t-1", currentUserId = "u1")

        assertTrue(result is ApiResult.Success)
        assertNull(result.data.metrics.assignedToMe)
        assertNull(result.data.metrics.overdue)
        assertEquals(30.0, result.data.metrics.balance)
    }

    @Test
    fun `si el resumen de gastos falla el saldo queda sin dato y los conteos siguen`() = runTest {
        val activitiesApi = FakeActivitiesApi(ApiResult.Success(listOf(activity("assigned"))))
        val expensesApi = FakeExpensesApi(ApiResult.Error(503, "expenses down"))
        val homeApi = HomeApi(UNUSED_CLIENT, "http://unused", FakeBondsApi(ApiResult.Success(listOf(bond()))), activitiesApi, expensesApi)

        val result = homeApi.getHomeSummary("t-1", currentUserId = "u1")

        assertTrue(result is ApiResult.Success)
        assertNull(result.data.metrics.balance)
        assertEquals(1, result.data.metrics.assignedToMe)
    }

    @Test
    fun `sin vinculo no hay metricas y no se llama a la lista ni al resumen`() = runTest {
        val activitiesApi = FakeActivitiesApi(ApiResult.Success(emptyList()))
        val expensesApi = FakeExpensesApi(ApiResult.Success(expenseSummary(saldo = 0.0)))
        val homeApi = HomeApi(UNUSED_CLIENT, "http://unused", FakeBondsApi(ApiResult.Success(emptyList())), activitiesApi, expensesApi)

        val result = homeApi.getHomeSummary("t-1", currentUserId = "u1")

        assertTrue(result is ApiResult.Success)
        assertEquals(0, activitiesApi.calls)
        assertEquals(0, expensesApi.calls)
        assertNull(result.data.metrics.assignedToMe)
        assertNull(result.data.metrics.balance)
    }
}
