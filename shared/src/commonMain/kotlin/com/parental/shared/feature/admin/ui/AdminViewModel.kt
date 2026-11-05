package com.parental.shared.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val users: List<AdminUser> = emptyList(),
    val bonds: List<AdminBond> = emptyList(),
    val selectedTab: Int = 0, // 0 = Users, 1 = Bonds
    // Form state for creating user
    val newUserEmail: String = "",
    val newUserPassword: String = "",
    val newUserFirstName: String = "",
    val newUserLastName: String = "",
    val isCreatingUser: Boolean = false,
    // Form state for creating bond
    val newBondTitle: String = "",
    val newBondAgreementType: String = "formal",
    val newBondUser1Id: String = "",
    val newBondUser2Id: String = "",
    val isCreatingBond: Boolean = false,
    // Children form
    val selectedBondId: String? = null,
    val bondChildren: List<AdminChild> = emptyList(),
    val newChildFirstName: String = "",
    val newChildLastName: String = "",
    val newChildDob: String = "",
    val isCreatingChild: Boolean = false,
)

class AdminViewModel(
    private val adminApi: AdminApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var childrenJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        loadUsers()
        loadBonds()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    // ── Users ────────────────────────────────────────────────────────

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = adminApi.listUsers()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            users = result.data,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun updateUserEmail(value: String) {
        _uiState.update { it.copy(newUserEmail = value) }
    }

    fun updateUserPassword(value: String) {
        _uiState.update { it.copy(newUserPassword = value) }
    }

    fun updateUserFirstName(value: String) {
        _uiState.update { it.copy(newUserFirstName = value) }
    }

    fun updateUserLastName(value: String) {
        _uiState.update { it.copy(newUserLastName = value) }
    }

    fun createUser() {
        val s = _uiState.value
        if (s.isCreatingUser) return // single-flight: un doble tap no crea el usuario dos veces
        if (s.newUserEmail.isBlank() || s.newUserPassword.isBlank() ||
            s.newUserFirstName.isBlank() || s.newUserLastName.isBlank()
        ) {
            _uiState.update { it.copy(error = "All fields are required") }
            return
        }

        _uiState.update { it.copy(isCreatingUser = true, error = null) }
        viewModelScope.launch {
            when (val result = adminApi.createUser(
                s.newUserEmail, s.newUserPassword,
                s.newUserFirstName, s.newUserLastName,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newUserEmail = "",
                            newUserPassword = "",
                            newUserFirstName = "",
                            newUserLastName = "",
                            isCreatingUser = false,
                        )
                    }
                    loadUsers()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message,
                            isCreatingUser = false,
                        )
                    }
                }
            }
        }
    }

    // ── Bonds ────────────────────────────────────────────────────────

    private fun loadBonds() {
        viewModelScope.launch {
            when (val result = adminApi.listBonds()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(bonds = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun updateBondTitle(value: String) {
        _uiState.update { it.copy(newBondTitle = value) }
    }

    fun updateBondAgreementType(value: String) {
        _uiState.update { it.copy(newBondAgreementType = value) }
    }

    fun updateBondUser1Id(value: String) {
        _uiState.update { it.copy(newBondUser1Id = value) }
    }

    fun updateBondUser2Id(value: String) {
        _uiState.update { it.copy(newBondUser2Id = value) }
    }

    fun createBond() {
        val s = _uiState.value
        if (s.isCreatingBond) return
        if (s.newBondTitle.isBlank() || s.newBondUser1Id.isBlank() || s.newBondUser2Id.isBlank()) {
            _uiState.update { it.copy(error = "Title and two user IDs are required") }
            return
        }

        _uiState.update { it.copy(isCreatingBond = true, error = null) }
        viewModelScope.launch {
            when (val result = adminApi.createBond(
                s.newBondTitle, s.newBondAgreementType,
                listOf(s.newBondUser1Id, s.newBondUser2Id),
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newBondTitle = "",
                            newBondUser1Id = "",
                            newBondUser2Id = "",
                            isCreatingBond = false,
                        )
                    }
                    loadBonds()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message,
                            isCreatingBond = false,
                        )
                    }
                }
            }
        }
    }

    // ── Children ─────────────────────────────────────────────────────

    fun selectBond(bondId: String) {
        _uiState.update { it.copy(selectedBondId = bondId) }
        // Cambiar de vínculo cancela la carga anterior: no se mezclan hijos de otro vínculo.
        childrenJob?.cancel()
        childrenJob = viewModelScope.launch {
            when (val result = adminApi.listBondChildren(bondId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(bondChildren = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun updateChildFirstName(value: String) {
        _uiState.update { it.copy(newChildFirstName = value) }
    }

    fun updateChildLastName(value: String) {
        _uiState.update { it.copy(newChildLastName = value) }
    }

    fun updateChildDob(value: String) {
        _uiState.update { it.copy(newChildDob = value) }
    }

    fun addChild() {
        val s = _uiState.value
        if (s.isCreatingChild) return
        val bondId = s.selectedBondId ?: return
        if (s.newChildFirstName.isBlank() || s.newChildLastName.isBlank()) {
            _uiState.update { it.copy(error = "Child name is required") }
            return
        }

        _uiState.update { it.copy(isCreatingChild = true, error = null) }
        viewModelScope.launch {
            when (val result = adminApi.addChildToBond(
                bondId, s.newChildFirstName, s.newChildLastName,
                s.newChildDob.ifBlank { null },
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            newChildFirstName = "",
                            newChildLastName = "",
                            newChildDob = "",
                            isCreatingChild = false,
                        )
                    }
                    selectBond(bondId)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message,
                            isCreatingChild = false,
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
