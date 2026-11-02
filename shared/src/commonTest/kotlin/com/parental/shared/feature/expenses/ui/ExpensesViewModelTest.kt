package com.parental.shared.feature.expenses.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import com.parental.shared.feature.expenses.domain.model.ExpenseActionResponse
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryCountsDto
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.PaginatedExpensesDto
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val UNUSED_CLIENT by lazy { HttpClient { } }

/**
 * ViewModel de gastos (S4/T29 + S5/T19).
 *
 * Patrón: API `open` + fake subclass + `UnconfinedTestDispatcher`, sin
 * `Dispatchers.setMain`. Cada VM se destruye en `tearDown`.
 */
class ExpensesViewModelTest {

    private val token = "t-1"
    private val bondId = "bond-1"
    private val viewModels = mutableListOf<ExpensesViewModel>()

    private class FakeExpensesApi : ExpensesApi(UNUSED_CLIENT, "http://unused") {
        var listCalls = 0
        var summaryCalls = 0
        var detailCalls = 0
        var approveCalls = 0
        var rejectCalls = 0
        var payCalls = 0
        var cancelCalls = 0
        var updateCalls = 0
        var createCalls = 0

        val requestedStatuses = mutableListOf<ExpenseStatus?>()
        val createRequests = mutableListOf<CreateExpenseRequest>()
        val requestedOffsets = mutableListOf<Int>()
        val rejectedReasons = mutableListOf<String?>()
        val updatedRequests = mutableListOf<ExpenseUpdateRequest>()

        var listResult: ApiResult<PaginatedExpensesDto> = ApiResult.Success(PaginatedExpensesDto())
        var summaryResult: ApiResult<ExpenseSummaryDto> = ApiResult.Success(summary())
        var detailResult: ApiResult<ExpenseResponseDto> = ApiResult.Success(expense("exp-1"))
        var actionResult: ApiResult<ExpenseActionResponse> = ApiResult.Success(ExpenseActionResponse("exp-1", ExpenseStatus.APPROVED))
        var updateResult: ApiResult<ExpenseResponseDto> = ApiResult.Success(expense("exp-1"))
        var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun listExpenses(
            token: String,
            bondId: String,
            limit: Int,
            offset: Int,
            status: ExpenseStatus?,
            categoryId: String?,
            from: String?,
            to: String?,
        ): ApiResult<PaginatedExpensesDto> {
            listCalls++
            requestedStatuses += status
            requestedOffsets += offset
            return listResult
        }

        override suspend fun getSummary(token: String, bondId: String): ApiResult<ExpenseSummaryDto> {
            summaryCalls++
            return summaryResult
        }

        override suspend fun getExpense(token: String, id: String): ApiResult<ExpenseResponseDto> {
            detailCalls++
            return detailResult
        }

        override suspend fun approveExpense(token: String, id: String): ApiResult<ExpenseActionResponse> {
            approveCalls++
            return actionResult
        }

        override suspend fun rejectExpense(token: String, id: String, reason: String?): ApiResult<ExpenseActionResponse> {
            rejectCalls++
            rejectedReasons += reason
            return actionResult
        }

        override suspend fun payExpense(token: String, id: String): ApiResult<ExpenseActionResponse> {
            payCalls++
            return actionResult
        }

        override suspend fun deleteExpense(token: String, id: String): ApiResult<Unit> {
            cancelCalls++
            return deleteResult
        }

        override suspend fun updateExpense(token: String, id: String, request: ExpenseUpdateRequest): ApiResult<ExpenseResponseDto> {
            updateCalls++
            updatedRequests += request
            return updateResult
        }

        override suspend fun createExpense(token: String, bondId: String, request: CreateExpenseRequest): ApiResult<ExpenseResponseDto> {
            createCalls++
            createRequests += request
            return updateResult
        }

        companion object {
            fun summary() = ExpenseSummaryDto(
                saldo = -25000.0,
                porPagar = 25000.0,
                porCobrar = 0.0,
                counts = ExpenseSummaryCountsDto(2, 1, 0, 0),
            )

            fun expense(id: String, status: ExpenseStatus = ExpenseStatus.PENDING) = ExpenseResponseDto(
                id = id,
                bondId = "bond-1",
                categoryId = "cat-1",
                categoryName = "Educación",
                concept = "Cuota",
                amount = 1000.0,
                expenseDate = "2026-04-01",
                splitRatioPayer = 50,
                splitRatioOther = 50,
                status = status,
                requestedBy = "user-a",
                counterpartId = "user-b",
                createdAt = "2026-04-01T10:00:00.000Z",
                updatedAt = "2026-04-01T10:00:00.000Z",
            )
        }
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    private fun build(api: FakeExpensesApi): ExpensesViewModel {
        val vm = ExpensesViewModel(
            api = api,
            token = token,
            bondId = bondId,
            dispatcher = UnconfinedTestDispatcher(),
        )
        viewModels += vm
        return vm
    }

    // ── S4 · carga inicial de resumen + lista ─────────────────────────────

    @Test
    fun `al abrir carga el resumen y la primera pagina`() {
        val api = FakeExpensesApi().apply {
            listResult = ApiResult.Success(PaginatedExpensesDto(
                items = listOf(FakeExpensesApi.expense("exp-1")),
                total = 1,
                limit = 20,
                offset = 0,
            ))
        }

        val vm = build(api)

        assertEquals(1, api.summaryCalls)
        assertEquals(1, api.listCalls)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(25000.0, vm.uiState.value.summary?.porPagar)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `la carga inicial pide la pagina 0 sin filtro`() {
        val api = FakeExpensesApi()
        build(api)

        assertEquals(listOf(0), api.requestedOffsets)
        assertEquals(listOf<ExpenseStatus?>(null), api.requestedStatuses)
    }

    @Test
    fun `error al cargar deja el mensaje y deja de cargar`() {
        val api = FakeExpensesApi().apply {
            listResult = ApiResult.Error(404, "Bond not found")
            summaryResult = ApiResult.Error(404, "Bond not found")
        }

        val vm = build(api)

        assertEquals("Bond not found", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ── S4 · paginacion ───────────────────────────────────────────────────

    @Test
    fun `cargar mas pide el siguiente offset y anexa`() {
        val api = FakeExpensesApi().apply {
            listResult = ApiResult.Success(PaginatedExpensesDto(
                items = listOf(FakeExpensesApi.expense("exp-1")),
                total = 2,
                limit = 20,
                offset = 0,
            ))
        }
        val vm = build(api)

        api.listResult = ApiResult.Success(PaginatedExpensesDto(
            items = listOf(FakeExpensesApi.expense("exp-2")),
            total = 2,
            limit = 20,
            offset = 1,
        ))
        vm.loadMore()

        assertEquals(listOf(0, 1), api.requestedOffsets)
        assertEquals(listOf("exp-1", "exp-2"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `no pide mas paginas cuando ya estan todas`() {
        val api = FakeExpensesApi().apply {
            listResult = ApiResult.Success(PaginatedExpensesDto(
                items = listOf(FakeExpensesApi.expense("exp-1")),
                total = 1,
                limit = 20,
                offset = 0,
            ))
        }
        val vm = build(api)

        vm.loadMore()

        assertEquals(1, api.listCalls)
    }

    // ── S4 · filtros por tarjeta ──────────────────────────────────────────

    @Test
    fun `tocar una tarjeta filtra por el status correspondiente`() {
        val api = FakeExpensesApi()
        val vm = build(api)

        vm.selectStatusFilter(ExpenseStatus.PENDING)

        assertEquals(ExpenseStatus.PENDING, vm.uiState.value.selectedStatus)
        assertEquals(listOf(null, ExpenseStatus.PENDING), api.requestedStatuses)
    }

    @Test
    fun `seleccionar el mismo filtro lo quita`() {
        val api = FakeExpensesApi()
        val vm = build(api)

        vm.selectStatusFilter(ExpenseStatus.PENDING)
        vm.selectStatusFilter(ExpenseStatus.PENDING)

        assertNull(vm.uiState.value.selectedStatus)
        assertEquals(listOf(null, ExpenseStatus.PENDING, null), api.requestedStatuses)
    }

    // ── S5 · detalle ──────────────────────────────────────────────────────

    @Test
    fun `abrir un gasto carga su detalle`() {
        val api = FakeExpensesApi().apply {
            detailResult = ApiResult.Success(FakeExpensesApi.expense("exp-1").copy(
                canApprove = true,
                canReject = true,
            ))
        }
        val vm = build(api)

        vm.openDetail("exp-1")

        assertEquals(1, api.detailCalls)
        val content = vm.uiState.value.detail
        assertIs<DetailUiState.Content>(content)
        assertTrue(content.expense.canApprove)
    }

    @Test
    fun `error al cargar detalle queda en estado de error`() {
        val api = FakeExpensesApi().apply {
            detailResult = ApiResult.Error(404, "Expense not found")
        }
        val vm = build(api)

        vm.openDetail("exp-1")

        val detail = vm.uiState.value.detail
        assertIs<DetailUiState.Error>(detail)
        assertEquals("Expense not found", detail.message)
    }

    // ── S5 · acciones refrescan detalle y resumen ─────────────────────────

    @Test
    fun `aprobar refresca el detalle y el resumen`() {
        val api = FakeExpensesApi().apply {
            detailResult = ApiResult.Success(FakeExpensesApi.expense("exp-1", ExpenseStatus.PENDING).copy(
                canApprove = true,
            ))
            actionResult = ApiResult.Success(ExpenseActionResponse("exp-1", ExpenseStatus.APPROVED))
        }
        val vm = build(api)
        vm.openDetail("exp-1")
        api.detailCalls = 0
        api.summaryCalls = 0

        vm.approve("exp-1")

        assertEquals(1, api.approveCalls)
        assertEquals(1, api.detailCalls)
        assertEquals(1, api.summaryCalls)
        assertIs<DetailUiState.Content>(vm.uiState.value.detail)
    }

    @Test
    fun `rechazar envia el motivo opcional`() {
        val api = FakeExpensesApi().apply {
            detailResult = ApiResult.Success(FakeExpensesApi.expense("exp-1", ExpenseStatus.PENDING).copy(
                canReject = true,
            ))
        }
        val vm = build(api)

        vm.reject("exp-1", "Falta comprobante")

        assertEquals(1, api.rejectCalls)
        assertEquals(listOf<String?>("Falta comprobante"), api.rejectedReasons)
    }

    @Test
    fun `marcar pagado refresca el detalle`() {
        val api = FakeExpensesApi().apply {
            detailResult = ApiResult.Success(FakeExpensesApi.expense("exp-1", ExpenseStatus.APPROVED).copy(
                canMarkPaid = true,
            ))
        }
        val vm = build(api)
        vm.openDetail("exp-1")
        api.detailCalls = 0

        vm.pay("exp-1")

        assertEquals(1, api.payCalls)
        assertEquals(1, api.detailCalls)
    }

    @Test
    fun `cancelar elimina el gasto de la lista`() {
        val api = FakeExpensesApi().apply {
            listResult = ApiResult.Success(PaginatedExpensesDto(
                items = listOf(FakeExpensesApi.expense("exp-1"), FakeExpensesApi.expense("exp-2")),
                total = 2,
                limit = 20,
                offset = 0,
            ))
        }
        val vm = build(api)

        vm.cancel("exp-1")

        assertEquals(1, api.cancelCalls)
        assertEquals(listOf("exp-2"), vm.uiState.value.items.map { it.id })
        assertEquals(1, vm.uiState.value.total)
    }

    @Test
    fun `editar envia el PATCH con los campos cambiados`() {
        val api = FakeExpensesApi()
        val vm = build(api)

        val request = ExpenseUpdateRequest(concept = "Nuevo concepto", amount = 2000.0)
        vm.update("exp-1", request)

        assertEquals(1, api.updateCalls)
        assertEquals("Nuevo concepto", api.updatedRequests.single().concept)
        assertEquals(2000.0, api.updatedRequests.single().amount)
    }

    // ── S6 · alta de gasto ────────────────────────────────────────────────

    @Test
    fun `crear un gasto cierra la hoja y refresca la lista y el resumen`() {
        val api = FakeExpensesApi().apply {
            updateResult = ApiResult.Success(FakeExpensesApi.expense("exp-new"))
        }
        val vm = build(api)
        api.summaryCalls = 0
        api.listCalls = 0

        vm.openCreateSheet()
        assertEquals(true, vm.uiState.value.isCreateSheetOpen)

        vm.submitCreateExpense(
            CreateExpenseRequest(
                concept = "Cuota",
                amount = 1500.0,
                categoryId = "cat-1",
                expenseDate = "2026-04-01",
            ),
        )

        assertEquals(1, api.createCalls)
        assertEquals("Cuota", api.createRequests.single().concept)
        assertEquals(false, vm.uiState.value.isCreateSheetOpen)
        assertEquals(1, api.summaryCalls)
        assertEquals(1, api.listCalls) // refresco tras crear
    }

    @Test
    fun `error al crear un gasto deja la hoja abierta y muestra snackbar`() {
        val api = FakeExpensesApi().apply {
            updateResult = ApiResult.Error(400, "Concepto requerido")
        }
        val vm = build(api)

        vm.openCreateSheet()
        vm.submitCreateExpense(
            CreateExpenseRequest(
                concept = "Cuota",
                amount = 1500.0,
                categoryId = "cat-1",
                expenseDate = "2026-04-01",
            ),
        )

        assertEquals(true, vm.uiState.value.isCreateSheetOpen)
        assertEquals("Concepto requerido", vm.uiState.value.snackbarMessage)
    }
}
