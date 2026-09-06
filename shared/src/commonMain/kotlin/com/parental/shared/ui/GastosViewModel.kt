package com.parental.shared.ui

import com.parental.shared.api.GastosApi
import com.parental.shared.model.ApproveGastoRequest
import com.parental.shared.model.CreateGastoRequest
import com.parental.shared.model.DisputeGastoRequest
import com.parental.shared.model.Gasto
import com.parental.shared.model.GastoDetalle
import com.parental.shared.model.GastoSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GastosUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val gastos: List<Gasto> = emptyList(),
    val summary: GastoSummary? = null,
    val selectedGasto: GastoDetalle? = null,
    val isDetailLoading: Boolean = false,
    // Form state
    val showCreateForm: Boolean = false,
    val newDescription: String = "",
    val newAmount: String = "",
    val newCategory: String = "FAMILIAR",
    val newExpenseDate: String = "",
    val newNotes: String = "",
    val isCreating: Boolean = false,
    // Dispute form
    val showDisputeForm: Boolean = false,
    val disputeReason: String = "",
    val disputeNotes: String = "",
    val isDisputing: Boolean = false,
    // Approve
    val isApproving: Boolean = false,
)

class GastosViewModel(
    private val gastosApi: GastosApi,
    private val token: String,
    private val bondId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(GastosUiState())
    val uiState: StateFlow<GastosUiState> = _uiState.asStateFlow()

    init {
        loadGastos()
        loadSummary()
    }

    fun loadGastos() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = gastosApi.listGastos(token, bondId)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        gastos = result.data,
                        isLoading = false,
                    )
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun loadSummary() {
        scope.launch {
            when (val result = gastosApi.getSummary(token, bondId)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(summary = result.data)
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    // Silently fail for summary
                }
            }
        }
    }

    fun selectGasto(id: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)

            when (val result = gastosApi.getGasto(token, id)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedGasto = result.data,
                        isDetailLoading = false,
                    )
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isDetailLoading = false,
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedGasto = null)
    }

    fun showCreateForm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateForm = show)
    }

    // ── Form field updates ─────────────────────────────────────────

    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(newDescription = value)
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(newAmount = value)
    }

    fun updateCategory(value: String) {
        _uiState.value = _uiState.value.copy(newCategory = value)
    }

    fun updateExpenseDate(value: String) {
        _uiState.value = _uiState.value.copy(newExpenseDate = value)
    }

    fun updateNotes(value: String) {
        _uiState.value = _uiState.value.copy(newNotes = value)
    }

    // ── Create ─────────────────────────────────────────────────────

    fun createGasto() {
        val s = _uiState.value
        if (s.newDescription.isBlank() || s.newAmount.isBlank() || s.newExpenseDate.isBlank()) {
            _uiState.value = s.copy(error = "Descripción, monto y fecha son requeridos")
            return
        }

        val amount = s.newAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.value = s.copy(error = "Monto debe ser un número positivo")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)

            val request = CreateGastoRequest(
                description = s.newDescription,
                amount = amount,
                category = s.newCategory,
                expenseDate = s.newExpenseDate,
                notes = s.newNotes.ifBlank { null },
                bondId = bondId,
            )

            when (val result = gastosApi.createGasto(token, bondId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newDescription = "",
                        newAmount = "",
                        newCategory = "FAMILIAR",
                        newExpenseDate = "",
                        newNotes = "",
                        showCreateForm = false,
                        isCreating = false,
                    )
                    loadGastos()
                    loadSummary()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isCreating = false,
                    )
                }
            }
        }
    }

    // ── Approve ────────────────────────────────────────────────────

    fun approveGasto(gastoId: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isApproving = true, error = null)

            when (val result = gastosApi.approveGasto(token, gastoId)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isApproving = false)
                    selectGasto(gastoId)
                    loadGastos()
                    loadSummary()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isApproving = false,
                    )
                }
            }
        }
    }

    // ── Dispute ────────────────────────────────────────────────────

    fun showDisputeForm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDisputeForm = show)
    }

    fun updateDisputeReason(value: String) {
        _uiState.value = _uiState.value.copy(disputeReason = value)
    }

    fun updateDisputeNotes(value: String) {
        _uiState.value = _uiState.value.copy(disputeNotes = value)
    }

    fun disputeGasto(gastoId: String) {
        val s = _uiState.value
        if (s.disputeReason.isBlank()) {
            _uiState.value = s.copy(error = "Motivo de disputa es requerido")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isDisputing = true, error = null)

            val request = DisputeGastoRequest(
                reason = s.disputeReason,
                notes = s.disputeNotes.ifBlank { null },
            )

            when (val result = gastosApi.disputeGasto(token, gastoId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showDisputeForm = false,
                        disputeReason = "",
                        disputeNotes = "",
                        isDisputing = false,
                    )
                    selectGasto(gastoId)
                    loadGastos()
                    loadSummary()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isDisputing = false,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun destroy() {
        scope.cancel()
    }
}
