package com.parental.shared.feature.expenses.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.realtime.RealtimeConnectionManager
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.PaginatedExpensesDto
import com.parental.shared.feature.expenses.domain.model.attachments.ConvertExpenseAttachmentReq
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val summary: ExpenseSummaryDto? = null,
    val items: List<ExpenseResponseDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = ExpensesApi.DEFAULT_PAGE_SIZE,
    val offset: Int = 0,
    val selectedStatus: ExpenseStatus? = null,
    val detail: DetailUiState = DetailUiState.Closed,
    val isCreateSheetOpen: Boolean = false,
    val isCreateSheetLoading: Boolean = false,
    val createInitialConcept: String? = null,
    val pendingActivityAttachmentId: String? = null,
    val categories: Map<String, CategoryInfo> = emptyMap(),
    val snackbarMessage: String? = null,
) {
    /** `total` es el total del backend, no el tamaño de [items] (S4). */
    val canLoadMore: Boolean get() = items.size < total
}

sealed class DetailUiState {
    data object Closed : DetailUiState()
    data class Loading(val id: String) : DetailUiState()
    data class Error(val id: String, val message: String) : DetailUiState()
    data class Content(val expense: ExpenseResponseDto) : DetailUiState()
}

/**
 * ViewModel de la pantalla de gastos (S4/T29 + S5/T19).
 *
 * Carga el resumen y la lista al iniciar. Las acciones del detalle refrescan
 * el detalle y el resumen para reflejar el nuevo saldo.
 */
class ExpensesViewModel(
    private val api: ExpensesApi,
    private val token: String,
    private val bondId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val categoriesApi: CategoriesApi = com.parental.shared.core.network.ApiClient.categoriesApi(),
    private val realtime: RealtimeConnectionManager? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Señal de recarga recibida mientras otra carga está en vuelo (mismo patrón
     * trailing-edge que `ActividadesViewModel`): en lugar de descartarla, se
     * rearma UNA recarga al terminar la que corre. El coalescer del realtime ya
     * acota la ráfaga, y esto evita dos cargas concurrentes pisándose.
     */
    private var pendingReload = false

    init {
        loadExpenses()
        loadCategories()
        // S8/T26: un `expense.changed` refresca resumen + lista por REST. El frame
        // es sólo una señal; la base sigue siendo la fuente de verdad.
        scope.launch {
            realtime?.expenseSignals?.collect {
                loadExpenses()
            }
        }
    }

    /** Carga las categorías activas para el selector del alta (S6/T27). */
    private fun loadCategories() {
        scope.launch {
            when (val result = categoriesApi.getCategories()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(categories = CategoryResolver.build(result.data))
                }
                is ApiResult.Error -> Unit // degrade silencioso: el picker queda vacío
            }
        }
    }

    /**
     * Carga resumen + primera página de la lista. Un fallo conserva lo ya cargado.
     *
     * Nunca dos cargas concurrentes: una señal (o un toque de filtro) que llega
     * con una carga en vuelo deja anotada exactamente una recarga pendiente.
     */
    fun loadExpenses() {
        if (loadJob?.isActive == true) {
            pendingReload = true
            return
        }
        loadJob = scope.launch {
            do {
                pendingReload = false
                performLoad()
            } while (pendingReload)
        }
    }

    private suspend fun performLoad() {
        _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }

        val summary = api.getSummary(token, bondId)
        val page = api.listExpenses(
            token = token,
            bondId = bondId,
            limit = _uiState.value.limit,
            offset = 0,
            status = _uiState.value.selectedStatus,
        )

        val error = listOfNotNull(
            (summary as? ApiResult.Error)?.message,
            (page as? ApiResult.Error)?.message,
        ).firstOrNull()

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                error = error,
                summary = if (summary is ApiResult.Success) summary.data else state.summary,
                items = if (page is ApiResult.Success) page.data.items else state.items,
                total = if (page is ApiResult.Success) page.data.total else state.total,
                offset = if (page is ApiResult.Success) page.data.offset else state.offset,
            )
        }
    }

    /** Refresca desde la primera página manteniendo el filtro activo. */
    fun refresh() = loadExpenses()

    /** Siguiente página; no duplica ids si el backend repitiese filas. */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return

        scope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            when (val result = api.listExpenses(
                token = token,
                bondId = bondId,
                limit = current.limit,
                offset = current.items.size,
                status = current.selectedStatus,
            )) {
                is ApiResult.Success -> _uiState.update { state ->
                    val known = state.items.mapTo(HashSet()) { it.id }
                    val appended = result.data.items.filterNot { it.id in known }
                    state.copy(
                        isLoadingMore = false,
                        items = state.items + appended,
                        total = result.data.total,
                        offset = result.data.offset,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingMore = false, snackbarMessage = result.message)
                }
            }
        }
    }

    /** Toca una tarjeta: alterna el filtro por status (FR-2). */
    fun selectStatusFilter(status: ExpenseStatus?) {
        val current = _uiState.value.selectedStatus
        val next = if (current == status) null else status
        _uiState.update { it.copy(selectedStatus = next) }
        loadExpenses()
    }

    /** Abre el detalle de un gasto. */
    fun openDetail(id: String) {
        _uiState.update { it.copy(detail = DetailUiState.Loading(id)) }
        scope.launch {
            when (val result = api.getExpense(token, id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(detail = DetailUiState.Content(result.data))
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(detail = DetailUiState.Error(id, result.message))
                }
            }
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detail = DetailUiState.Closed) }
    }

    /** Aprueba un gasto y refresca detalle + resumen. */
    fun approve(id: String) {
        performAction(id) { api.approveExpense(token, id) }
    }

    /** Rechaza un gasto con motivo opcional. */
    fun reject(id: String, reason: String? = null) {
        performAction(id) { api.rejectExpense(token, id, reason) }
    }

    /** Marca un gasto como pagado. */
    fun pay(id: String) {
        performAction(id) { api.payExpense(token, id) }
    }

    /** Cancela un gasto y lo saca de la lista. */
    fun cancel(id: String) {
        scope.launch {
            when (val result = api.deleteExpense(token, id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        items = state.items.filterNot { it.id == id },
                        total = (state.total - 1).coerceAtLeast(0),
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(snackbarMessage = result.message)
                }
            }
            refreshSummary()
        }
    }

    /** Edita un gasto y refresca el detalle. */
    fun update(id: String, request: ExpenseUpdateRequest) {
        scope.launch {
            when (val result = api.updateExpense(token, id, request)) {
                is ApiResult.Success -> {
                    refreshDetail(id)
                    refreshSummary()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(snackbarMessage = result.message)
                }
            }
        }
    }

    /** Abre la hoja de alta de gastos. */
    fun openCreateSheet(initialConcept: String? = null, activityAttachmentId: String? = null) {
        _uiState.update {
            it.copy(
                isCreateSheetOpen = true,
                createInitialConcept = initialConcept,
                pendingActivityAttachmentId = activityAttachmentId,
            )
        }
    }

    /** Cierra la hoja de alta. */
    fun closeCreateSheet() {
        _uiState.update {
            it.copy(
                isCreateSheetOpen = false,
                createInitialConcept = null,
                pendingActivityAttachmentId = null,
            )
        }
    }

    /** Crea un gasto y refresca la lista y el resumen (S6/T27). */
    fun submitCreateExpense(request: CreateExpenseRequest) {
        scope.launch {
            val activityAttachmentId = _uiState.value.pendingActivityAttachmentId
            _uiState.update { it.copy(isCreateSheetLoading = true) }
            when (val result = api.createExpense(token, bondId, request)) {
                is ApiResult.Success -> {
                    val conversion = activityAttachmentId?.let {
                        api.convertExpenseAttachment(
                            token,
                            result.data.id,
                            ConvertExpenseAttachmentReq(activityAttachmentId = it),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isCreateSheetOpen = false,
                            isCreateSheetLoading = false,
                            createInitialConcept = null,
                            pendingActivityAttachmentId = null,
                            snackbarMessage = when (conversion) {
                                is ApiResult.Error -> "Gasto creado, pero no se pudo vincular el comprobante. El archivo sigue en la actividad."
                                else -> "Gasto creado"
                            },
                        )
                    }
                    loadExpenses()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isCreateSheetLoading = false, snackbarMessage = result.message)
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun performAction(
        id: String,
        action: suspend () -> ApiResult<*>,
    ) {
        scope.launch {
            when (val result = action()) {
                is ApiResult.Success -> {
                    refreshDetail(id)
                    refreshSummary()
                    refreshList()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(snackbarMessage = result.message)
                }
            }
        }
    }

    private suspend fun refreshDetail(id: String) {
        when (val result = api.getExpense(token, id)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(detail = DetailUiState.Content(result.data))
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(snackbarMessage = result.message)
            }
        }
    }

    private suspend fun refreshSummary() {
        when (val result = api.getSummary(token, bondId)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(summary = result.data)
            }
            is ApiResult.Error -> Unit // conserva el último resumen conocido
        }
    }

    private suspend fun refreshList() {
        when (val result = api.listExpenses(
            token = token,
            bondId = bondId,
            limit = _uiState.value.limit,
            offset = 0,
            status = _uiState.value.selectedStatus,
        )) {
            is ApiResult.Success -> _uiState.update {
                it.copy(items = result.data.items, total = result.data.total, offset = result.data.offset)
            }
            is ApiResult.Error -> Unit
        }
    }
}
