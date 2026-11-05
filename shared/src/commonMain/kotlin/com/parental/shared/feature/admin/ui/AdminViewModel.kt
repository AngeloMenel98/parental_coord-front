package com.parental.shared.feature.admin.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminChild
import com.parental.shared.feature.admin.domain.model.AdminReport
import com.parental.shared.feature.admin.domain.model.AdminUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
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
    // F1 · S1/S2 — filas con una request en vuelo (por id, no un flag global).
    val usersInFlight: Set<String> = emptySet(),
    val bondsInFlight: Set<String> = emptySet(),
    // F1 · S3/S4 — edición inline de un hijo y confirmación de borrado.
    val editingChildId: String? = null,
    val editChildFirstName: String = "",
    val editChildLastName: String = "",
    /** `null` = el hijo no tiene fecha (o el admin la borró). */
    val editChildDob: String? = null,
    val isSavingChild: Boolean = false,
    val childPendingDeleteId: String? = null,
    val isDeletingChild: Boolean = false,
    // F2 · S5-S8 — pestaña de reportes.
    val reportBondId: String? = null,
    val reportDateFrom: String = adminMonthStartIso(),
    val reportDateTo: String = adminTodayIso(),
    val report: AdminBondReport? = null,
    val isLoadingReport: Boolean = false,
    val requestedReport: AdminReport? = null,
    val isRequestingReport: Boolean = false,
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

    // ── F1 · Activación de usuarios y vínculos ──────────────────────

    /** S1 — un cambio por fila: el switch queda bloqueado hasta la respuesta. */
    fun toggleUserActive(user: AdminUser) {
        if (user.id in _uiState.value.usersInFlight) return

        scope.launch {
            _uiState.value = _uiState.value.copy(
                usersInFlight = _uiState.value.usersInFlight + user.id,
                error = null,
            )
            when (val result = adminApi.setUserActive(token, user.id, !user.isActive)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        users = _uiState.value.users.map { if (it.id == user.id) result.data else it },
                        usersInFlight = _uiState.value.usersInFlight - user.id,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        usersInFlight = _uiState.value.usersInFlight - user.id,
                    )
                }
            }
        }
    }

    /** S2 — mismo contrato para el vínculo. */
    fun toggleBondActive(bond: AdminBond) {
        if (bond.id in _uiState.value.bondsInFlight) return

        scope.launch {
            _uiState.value = _uiState.value.copy(
                bondsInFlight = _uiState.value.bondsInFlight + bond.id,
                error = null,
            )
            when (val result = adminApi.setBondActive(token, bond.id, !bond.isActive)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        bonds = _uiState.value.bonds.map { if (it.id == bond.id) result.data else it },
                        bondsInFlight = _uiState.value.bondsInFlight - bond.id,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        bondsInFlight = _uiState.value.bondsInFlight - bond.id,
                    )
                }
            }
        }
    }

    // ── F1 · Edición y borrado de hijos ─────────────────────────────

    fun startEditingChild(child: AdminChild) {
        _uiState.value = _uiState.value.copy(
            editingChildId = child.id,
            editChildFirstName = child.firstName,
            editChildLastName = child.lastName,
            editChildDob = child.dateOfBirth,
            error = null,
        )
    }

    fun cancelEditingChild() {
        _uiState.value = _uiState.value.copy(
            editingChildId = null,
            editChildFirstName = "",
            editChildLastName = "",
            editChildDob = null,
        )
    }

    fun updateEditChildFirstName(value: String) {
        _uiState.value = _uiState.value.copy(editChildFirstName = value)
    }

    fun updateEditChildLastName(value: String) {
        _uiState.value = _uiState.value.copy(editChildLastName = value)
    }

    /** `null` cuando el admin elige "sin fecha". */
    fun updateEditChildDob(value: String?) {
        _uiState.value = _uiState.value.copy(editChildDob = value)
    }

    /** S3/S4 — manda lo que el formulario muestra; sin fecha = `dateOfBirth: null`. */
    fun saveChild() {
        val s = _uiState.value
        val childId = s.editingChildId ?: return
        if (s.editChildFirstName.isBlank() || s.editChildLastName.isBlank()) {
            _uiState.value = s.copy(error = "Child name is required")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isSavingChild = true, error = null)
            val result = adminApi.updateChild(
                token = token,
                childId = childId,
                firstName = s.editChildFirstName.trim(),
                lastName = s.editChildLastName.trim(),
                dateOfBirth = s.editChildDob,
                clearDateOfBirth = s.editChildDob == null,
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        bondChildren = _uiState.value.bondChildren.map {
                            if (it.id == childId) result.data else it
                        },
                        editingChildId = null,
                        editChildFirstName = "",
                        editChildLastName = "",
                        editChildDob = null,
                        isSavingChild = false,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSavingChild = false,
                    )
                }
            }
        }
    }

    fun requestDeleteChild(childId: String) {
        _uiState.value = _uiState.value.copy(childPendingDeleteId = childId, error = null)
    }

    fun cancelDeleteChild() {
        _uiState.value = _uiState.value.copy(childPendingDeleteId = null)
    }

    /** S3 — borra de verdad y recarga la lista del vínculo seleccionado. */
    fun confirmDeleteChild() {
        val s = _uiState.value
        val childId = s.childPendingDeleteId ?: return
        val bondId = s.selectedBondId

        scope.launch {
            _uiState.value = _uiState.value.copy(isDeletingChild = true, error = null)
            when (val result = adminApi.deleteChild(token, childId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        bondChildren = _uiState.value.bondChildren.filterNot { it.id == childId },
                        childPendingDeleteId = null,
                        editingChildId = if (_uiState.value.editingChildId == childId) {
                            null
                        } else {
                            _uiState.value.editingChildId
                        },
                        isDeletingChild = false,
                    )
                    bondId?.let { selectBond(it) }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isDeletingChild = false,
                    )
                }
            }
        }
    }

    // ── F2 · Reportes ───────────────────────────────────────────────

    /** S5 — sin bond elegido la pestaña explica qué falta en lugar de fallar. */
    fun selectReportBond(bondId: String) {
        _uiState.value = _uiState.value.copy(reportBondId = bondId, error = null)
    }

    fun updateReportFrom(iso: String) {
        _uiState.value = _uiState.value.copy(reportDateFrom = iso)
    }

    fun updateReportTo(iso: String) {
        _uiState.value = _uiState.value.copy(reportDateTo = iso)
    }

    /** S5/S6 — trae el JSON y lo deja listo para las cuatro secciones. */
    fun loadReport() {
        val s = _uiState.value
        val bondId = s.reportBondId
        if (bondId == null) {
            _uiState.value = s.copy(error = "Select a bond first")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingReport = true, error = null)
            when (val result = adminApi.getBondReport(token, bondId, s.reportDateFrom, s.reportDateTo)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(report = result.data, isLoadingReport = false)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message, isLoadingReport = false)
                }
            }
        }
    }

    /**
     * S7 — pide el PDF y espera a que el worker lo cierre.
     *
     * No hay WebSocket para el reporte: se consulta `GET /admin/reports/:id` con una
     * espera acotada (15 intentos de 2 s). Si no llega, la UI muestra el estado real
     * y queda el botón de reintentar; nunca se inventa un `fileUrl`.
     */
    fun requestReportPdf() {
        val s = _uiState.value
        val bondId = s.reportBondId
        if (bondId == null) {
            _uiState.value = s.copy(error = "Select a bond first")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isRequestingReport = true, error = null)
            when (val created = adminApi.requestReportPdf(token, bondId, s.reportDateFrom, s.reportDateTo)) {
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = created.message,
                        isRequestingReport = false,
                    )
                }
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(requestedReport = null)

                    var latest: AdminReport? = null
                    repeat(PDF_POLL_ATTEMPTS) { attempt ->
                        if (latest?.fileUrl != null) return@repeat
                        if (attempt > 0) delay(PDF_POLL_DELAY_MS)
                        when (val polled = adminApi.getReport(token, created.data.id)) {
                            is ApiResult.Success -> latest = polled.data
                            is ApiResult.Error -> {
                                _uiState.value = _uiState.value.copy(
                                    error = polled.message,
                                    isRequestingReport = false,
                                )
                                return@launch
                            }
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        requestedReport = latest,
                        isRequestingReport = false,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

private const val PDF_POLL_ATTEMPTS = 15
private const val PDF_POLL_DELAY_MS = 2_000L
