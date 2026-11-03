package com.parental.shared.feature.admin.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val token: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        loadUsers()
        loadBonds()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    // ── Users ────────────────────────────────────────────────────────

    private fun loadUsers() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = adminApi.listUsers(token)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        users = result.data,
                        isLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun updateUserEmail(value: String) {
        _uiState.value = _uiState.value.copy(newUserEmail = value)
    }

    fun updateUserPassword(value: String) {
        _uiState.value = _uiState.value.copy(newUserPassword = value)
    }

    fun updateUserFirstName(value: String) {
        _uiState.value = _uiState.value.copy(newUserFirstName = value)
    }

    fun updateUserLastName(value: String) {
        _uiState.value = _uiState.value.copy(newUserLastName = value)
    }

    fun createUser() {
        val s = _uiState.value
        if (s.newUserEmail.isBlank() || s.newUserPassword.isBlank() ||
            s.newUserFirstName.isBlank() || s.newUserLastName.isBlank()
        ) {
            _uiState.value = s.copy(error = "All fields are required")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreatingUser = true, error = null)
            when (val result = adminApi.createUser(
                token, s.newUserEmail, s.newUserPassword,
                s.newUserFirstName, s.newUserLastName,
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newUserEmail = "",
                        newUserPassword = "",
                        newUserFirstName = "",
                        newUserLastName = "",
                        isCreatingUser = false,
                    )
                    loadUsers()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isCreatingUser = false,
                    )
                }
            }
        }
    }

    // ── Bonds ────────────────────────────────────────────────────────

    private fun loadBonds() {
        scope.launch {
            when (val result = adminApi.listBonds(token)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(bonds = result.data)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun updateBondTitle(value: String) {
        _uiState.value = _uiState.value.copy(newBondTitle = value)
    }

    fun updateBondAgreementType(value: String) {
        _uiState.value = _uiState.value.copy(newBondAgreementType = value)
    }

    fun updateBondUser1Id(value: String) {
        _uiState.value = _uiState.value.copy(newBondUser1Id = value)
    }

    fun updateBondUser2Id(value: String) {
        _uiState.value = _uiState.value.copy(newBondUser2Id = value)
    }

    fun createBond() {
        val s = _uiState.value
        if (s.newBondTitle.isBlank() || s.newBondUser1Id.isBlank() || s.newBondUser2Id.isBlank()) {
            _uiState.value = s.copy(error = "Title and two user IDs are required")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreatingBond = true, error = null)
            when (val result = adminApi.createBond(
                token, s.newBondTitle, s.newBondAgreementType,
                listOf(s.newBondUser1Id, s.newBondUser2Id),
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newBondTitle = "",
                        newBondUser1Id = "",
                        newBondUser2Id = "",
                        isCreatingBond = false,
                    )
                    loadBonds()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isCreatingBond = false,
                    )
                }
            }
        }
    }

    // ── Children ─────────────────────────────────────────────────────

    fun selectBond(bondId: String) {
        _uiState.value = _uiState.value.copy(selectedBondId = bondId)
        scope.launch {
            when (val result = adminApi.listBondChildren(token, bondId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(bondChildren = result.data)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun updateChildFirstName(value: String) {
        _uiState.value = _uiState.value.copy(newChildFirstName = value)
    }

    fun updateChildLastName(value: String) {
        _uiState.value = _uiState.value.copy(newChildLastName = value)
    }

    fun updateChildDob(value: String) {
        _uiState.value = _uiState.value.copy(newChildDob = value)
    }

    fun addChild() {
        val s = _uiState.value
        val bondId = s.selectedBondId ?: return
        if (s.newChildFirstName.isBlank() || s.newChildLastName.isBlank()) {
            _uiState.value = s.copy(error = "Child name is required")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreatingChild = true, error = null)
            when (val result = adminApi.addChildToBond(
                token, bondId, s.newChildFirstName, s.newChildLastName,
                s.newChildDob.ifBlank { null },
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newChildFirstName = "",
                        newChildLastName = "",
                        newChildDob = "",
                        isCreatingChild = false,
                    )
                    selectBond(bondId)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isCreatingChild = false,
                    )
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
