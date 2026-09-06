package com.parental.shared.ui

import com.parental.shared.api.ActividadesApi
import com.parental.shared.model.Actividad
import com.parental.shared.model.ActividadDetalle
import com.parental.shared.model.CreateActividadRequest
import com.parental.shared.model.UpdateActividadRequest
import com.parental.shared.model.UpdateStatusRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActividadesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val actividades: List<Actividad> = emptyList(),
    val selectedTab: Int = 0, // 0 = Todos, 1 = Eventos, 2 = Obligaciones
    val selectedActividad: ActividadDetalle? = null,
    val isDetailLoading: Boolean = false,
    // Form state
    val showCreateForm: Boolean = false,
    val newTitle: String = "",
    val newDescription: String = "",
    val newType: String = "EVENT",
    val newCategory: String = "FAMILIAR",
    val newPriority: String = "MEDIUM",
    val newScheduledDate: String = "",
    val newDueDate: String = "",
    val isCreating: Boolean = false,
    // Status action
    val isUpdatingStatus: Boolean = false,
)

class ActividadesViewModel(
    private val actividadesApi: ActividadesApi,
    private val token: String,
    private val bondId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ActividadesUiState())
    val uiState: StateFlow<ActividadesUiState> = _uiState.asStateFlow()

    init {
        loadActividades()
    }

    fun loadActividades() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val type = when (_uiState.value.selectedTab) {
                1 -> "EVENT"
                2 -> "OBLIGATION"
                else -> null
            }

            when (val result = actividadesApi.listActividades(token, bondId, type)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actividades = result.data,
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

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        loadActividades()
    }

    fun selectActividad(id: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)

            when (val result = actividadesApi.getActividad(token, id)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedActividad = result.data,
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
        _uiState.value = _uiState.value.copy(selectedActividad = null)
    }

    fun showCreateForm(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateForm = show)
    }

    // ── Form field updates ─────────────────────────────────────────

    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(newTitle = value)
    }

    fun updateDescription(value: String) {
        _uiState.value = _uiState.value.copy(newDescription = value)
    }

    fun updateType(value: String) {
        _uiState.value = _uiState.value.copy(newType = value)
    }

    fun updateCategory(value: String) {
        _uiState.value = _uiState.value.copy(newCategory = value)
    }

    fun updatePriority(value: String) {
        _uiState.value = _uiState.value.copy(newPriority = value)
    }

    fun updateScheduledDate(value: String) {
        _uiState.value = _uiState.value.copy(newScheduledDate = value)
    }

    fun updateDueDate(value: String) {
        _uiState.value = _uiState.value.copy(newDueDate = value)
    }

    // ── Create ─────────────────────────────────────────────────────

    fun createActividad() {
        val s = _uiState.value
        if (s.newTitle.isBlank() || s.newScheduledDate.isBlank()) {
            _uiState.value = s.copy(error = "Título y fecha son requeridos")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)

            val request = CreateActividadRequest(
                title = s.newTitle,
                description = s.newDescription.ifBlank { null },
                type = s.newType,
                category = s.newCategory,
                priority = s.newPriority,
                scheduledDate = s.newScheduledDate,
                dueDate = s.newDueDate.ifBlank { null },
                bondId = bondId,
            )

            when (val result = actividadesApi.createActividad(token, bondId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newTitle = "",
                        newDescription = "",
                        newType = "EVENT",
                        newCategory = "FAMILIAR",
                        newPriority = "MEDIUM",
                        newScheduledDate = "",
                        newDueDate = "",
                        showCreateForm = false,
                        isCreating = false,
                    )
                    loadActividades()
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

    // ── Status lifecycle ───────────────────────────────────────────

    fun updateStatus(actividadId: String, newStatus: String, notes: String? = null) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingStatus = true, error = null)

            val request = UpdateStatusRequest(newStatus = newStatus, notes = notes)

            when (val result = actividadesApi.updateStatus(token, actividadId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUpdatingStatus = false)
                    // Refresh the detail if viewing
                    selectActividad(actividadId)
                    loadActividades()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isUpdatingStatus = false,
                    )
                }
            }
        }
    }

    fun assignActividad(actividadId: String, assignedTo: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingStatus = true, error = null)

            val request = UpdateActividadRequest(assignedTo = assignedTo)

            when (val result = actividadesApi.updateActividad(token, actividadId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUpdatingStatus = false)
                    selectActividad(actividadId)
                    loadActividades()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isUpdatingStatus = false,
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
