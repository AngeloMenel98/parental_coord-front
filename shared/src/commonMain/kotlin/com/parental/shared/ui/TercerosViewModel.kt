package com.parental.shared.ui

import com.parental.shared.api.TercerosApi
import com.parental.shared.model.CreateTerceroRequest
import com.parental.shared.model.RevokeTerceroRequest
import com.parental.shared.model.Tercero
import com.parental.shared.model.TerceroDetalle
import com.parental.shared.model.UpdateTerceroRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TercerosUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val terceros: List<Tercero> = emptyList(),
    val selectedTercero: TerceroDetalle? = null,
    val isDetailLoading: Boolean = false,
    // Form state
    val showCreateForm: Boolean = false,
    val isEditing: Boolean = false,
    val editingTerceroId: String? = null,
    val newName: String = "",
    val newEmail: String = "",
    val newPhone: String = "",
    val newRole: String = "OTHER",
    val newScope: String = "CHILD",
    val newNotes: String = "",
    val isCreating: Boolean = false,
    // Revoke
    val showRevokeDialog: Boolean = false,
    val revokeReason: String = "",
    val isRevoking: Boolean = false,
)

class TercerosViewModel(
    private val tercerosApi: TercerosApi,
    private val token: String,
    private val bondId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(TercerosUiState())
    val uiState: StateFlow<TercerosUiState> = _uiState.asStateFlow()

    init {
        loadTerceros()
    }

    fun loadTerceros() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = tercerosApi.listTerceros(token, bondId)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        terceros = result.data,
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

    fun selectTercero(id: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)

            when (val result = tercerosApi.getTercero(token, id)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedTercero = result.data,
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
        _uiState.value = _uiState.value.copy(selectedTercero = null)
    }

    fun showCreateForm(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showCreateForm = show,
            isEditing = false,
            editingTerceroId = null,
            newName = "",
            newEmail = "",
            newPhone = "",
            newRole = "OTHER",
            newScope = "CHILD",
            newNotes = "",
        )
    }

    fun startEdit(tercero: TerceroDetalle) {
        _uiState.value = _uiState.value.copy(
            showCreateForm = true,
            isEditing = true,
            editingTerceroId = tercero.id,
            newName = tercero.nombre,
            newEmail = tercero.email ?: "",
            newPhone = tercero.phone ?: "",
            newRole = tercero.role,
            newScope = tercero.scope,
            newNotes = tercero.notes ?: "",
        )
    }

    // ── Form field updates ─────────────────────────────────────────

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(newName = value)
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(newEmail = value)
    }

    fun updatePhone(value: String) {
        _uiState.value = _uiState.value.copy(newPhone = value)
    }

    fun updateRole(value: String) {
        _uiState.value = _uiState.value.copy(newRole = value)
    }

    fun updateScope(value: String) {
        _uiState.value = _uiState.value.copy(newScope = value)
    }

    fun updateNotes(value: String) {
        _uiState.value = _uiState.value.copy(newNotes = value)
    }

    // ── Create / Update ────────────────────────────────────────────

    fun saveTercero() {
        val s = _uiState.value
        if (s.newName.isBlank()) {
            _uiState.value = s.copy(error = "Nombre es requerido")
            return
        }

        if (s.isEditing && s.editingTerceroId != null) {
            updateTercero(s.editingTerceroId)
        } else {
            createTercero()
        }
    }

    private fun createTercero() {
        val s = _uiState.value

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)

            val request = CreateTerceroRequest(
                nombre = s.newName,
                email = s.newEmail.ifBlank { null },
                phone = s.newPhone.ifBlank { null },
                role = s.newRole,
                scope = s.newScope,
                bondId = bondId,
                notes = s.newNotes.ifBlank { null },
            )

            when (val result = tercerosApi.createTercero(token, bondId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newName = "",
                        newEmail = "",
                        newPhone = "",
                        newRole = "OTHER",
                        newScope = "CHILD",
                        newNotes = "",
                        showCreateForm = false,
                        isCreating = false,
                    )
                    loadTerceros()
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

    private fun updateTercero(id: String) {
        val s = _uiState.value

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)

            val request = UpdateTerceroRequest(
                nombre = s.newName,
                email = s.newEmail.ifBlank { null },
                phone = s.newPhone.ifBlank { null },
                role = s.newRole,
                scope = s.newScope,
                notes = s.newNotes.ifBlank { null },
            )

            when (val result = tercerosApi.updateTercero(token, id, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newName = "",
                        newEmail = "",
                        newPhone = "",
                        newRole = "OTHER",
                        newScope = "CHILD",
                        newNotes = "",
                        showCreateForm = false,
                        isEditing = false,
                        editingTerceroId = null,
                        isCreating = false,
                    )
                    loadTerceros()
                    selectTercero(id)
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

    // ── Revoke ─────────────────────────────────────────────────────

    fun showRevokeDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showRevokeDialog = show)
    }

    fun updateRevokeReason(value: String) {
        _uiState.value = _uiState.value.copy(revokeReason = value)
    }

    fun revokeTercero(terceroId: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isRevoking = true, error = null)

            val request = RevokeTerceroRequest(
                reason = _uiState.value.revokeReason.ifBlank { null },
            )

            when (val result = tercerosApi.revokeTercero(token, terceroId, request)) {
                is com.parental.shared.api.ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showRevokeDialog = false,
                        revokeReason = "",
                        isRevoking = false,
                    )
                    loadTerceros()
                    clearSelection()
                }
                is com.parental.shared.api.ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isRevoking = false,
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
