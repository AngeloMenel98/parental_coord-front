package com.parental.shared.feature.admin.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.admin.data.remote.AdminApi
import com.parental.shared.feature.admin.data.remote.CreateBondChildRequest
import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondHealth
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminBondTrendPoint
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
    // F5 · wizard de creación de vínculo (artboard admin-forms-ui/03)
    val newBondTitle: String = "",
    val newBondAgreementType: String = "formal",
    val isCreatingBond: Boolean = false,
    val showBondWizard: Boolean = false,
    val bondWizardStep: Int = 0,
    val bondMemberIds: List<String> = emptyList(),
    val bondUserQuery: String = "",
    val bondChildrenDraft: List<BondChildDraft> = emptyList(),
    val bondChildFirstName: String = "",
    val bondChildLastName: String = "",
    val bondChildDob: String = "",
    // F5 · alta de progenitor en diálogo con validación (S13)
    val showNewUserDialog: Boolean = false,
    val newUserFromWizard: Boolean = false,
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
    // F3 · artboard admin-ui/01 — lista con salud, detalle y vista del PDF.
    val bondSearch: String = "",
    val healthByBond: Map<String, AdminBondHealth> = emptyMap(),
    val isLoadingHealth: Boolean = false,
    val openBondId: String? = null,
    val bondDetailTab: Int = 0, // 0 hijos · 1 miembros · 2 reporte
    val trend: List<AdminBondTrendPoint> = emptyList(),
    val isLoadingTrend: Boolean = false,
    val showPdfPreview: Boolean = false,
    val showAddChild: Boolean = false,
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
        loadHealth()
    }

    /**
     * F3 · la salud de la lista, siempre sobre el mes en curso.
     *
     * Es una sola request para todos los vínculos; pedir el reporte de cada uno
     * serían decenas de llamadas desde el teléfono.
     */
    fun loadHealth() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHealth = true)
            val result = adminApi.getBondsHealth(token, adminMonthStartIso(), adminTodayIso())
            _uiState.value = when (result) {
                is ApiResult.Success -> _uiState.value.copy(
                    healthByBond = result.data.associateBy { it.bondId },
                    isLoadingHealth = false,
                )
                is ApiResult.Error -> _uiState.value.copy(
                    error = result.message,
                    isLoadingHealth = false,
                )
            }
        }
    }

    // ── F5 · Wizard de creación de vínculo ──────────────────────────

    fun openBondWizard() {
        _uiState.value = _uiState.value.copy(
            showBondWizard = true,
            bondWizardStep = 0,
            newBondTitle = "",
            newBondAgreementType = "formal",
            bondMemberIds = emptyList(),
            bondUserQuery = "",
            bondChildrenDraft = emptyList(),
            bondChildFirstName = "",
            bondChildLastName = "",
            bondChildDob = "",
            error = null,
        )
    }

    fun closeBondWizard() {
        _uiState.value = _uiState.value.copy(showBondWizard = false, bondWizardStep = 0, error = null)
    }

    /**
     * F5 · el paso avanza sólo si el actual está completo.
     *
     * El artboard lo dibuja como un botón que se habilita; acá se deja el botón
     * habilitado y el paso explica qué falta, que es más útil que un botón mudo.
     */
    fun nextBondWizardStep() {
        val s = _uiState.value
        when (s.bondWizardStep) {
            0 -> _uiState.value = if (s.newBondTitle.isNotBlank()) {
                s.copy(bondWizardStep = 1, error = null)
            } else {
                s.copy(error = "Ponele un nombre al vínculo para seguir")
            }

            1 -> _uiState.value = if (s.bondMemberIds.size == 2) {
                s.copy(bondWizardStep = 2, error = null)
            } else {
                s.copy(error = "Se necesitan exactamente dos progenitores")
            }
        }
    }

    fun previousBondWizardStep() {
        _uiState.value = _uiState.value.copy(
            bondWizardStep = (_uiState.value.bondWizardStep - 1).coerceAtLeast(0),
            error = null,
        )
    }

    fun updateBondUserQuery(value: String) {
        _uiState.value = _uiState.value.copy(bondUserQuery = value)
    }

    /** F5 · alterna un progenitor; nunca deja más de dos. */
    fun toggleBondMember(userId: String) {
        val s = _uiState.value
        _uiState.value = when {
            userId in s.bondMemberIds -> s.copy(bondMemberIds = s.bondMemberIds - userId, error = null)
            s.bondMemberIds.size >= 2 -> s.copy(error = "Un vínculo tiene exactamente dos progenitores")
            else -> s.copy(bondMemberIds = s.bondMemberIds + userId, error = null)
        }
    }

    fun updateBondChildFirstName(value: String) {
        _uiState.value = _uiState.value.copy(bondChildFirstName = value)
    }

    fun updateBondChildLastName(value: String) {
        _uiState.value = _uiState.value.copy(bondChildLastName = value)
    }

    fun updateBondChildDob(value: String) {
        _uiState.value = _uiState.value.copy(bondChildDob = value)
    }

    fun addBondChildDraft() {
        val s = _uiState.value
        if (s.bondChildFirstName.isBlank() || s.bondChildLastName.isBlank()) {
            _uiState.value = s.copy(error = "El hijo necesita nombre y apellido")
            return
        }
        _uiState.value = s.copy(
            bondChildrenDraft = s.bondChildrenDraft + BondChildDraft(
                firstName = s.bondChildFirstName.trim(),
                lastName = s.bondChildLastName.trim(),
                dateOfBirth = s.bondChildDob.ifBlank { null },
            ),
            bondChildFirstName = "",
            bondChildLastName = "",
            bondChildDob = "",
            error = null,
        )
    }

    fun removeBondChildDraft(index: Int) {
        val drafts = _uiState.value.bondChildrenDraft
        if (index !in drafts.indices) return
        _uiState.value = _uiState.value.copy(
            bondChildrenDraft = drafts.filterIndexed { i, _ -> i != index },
        )
    }

    /** F5 · crea vínculo + progenitores + hijos en una sola request. */
    fun submitBondWizard() {
        val s = _uiState.value
        if (s.bondChildrenDraft.isEmpty()) {
            _uiState.value = s.copy(error = "El vínculo necesita al menos un hijo")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isCreatingBond = true, error = null)
            val result = adminApi.createBond(
                token = token,
                title = s.newBondTitle.trim(),
                agreementType = s.newBondAgreementType,
                userIds = s.bondMemberIds,
                children = s.bondChildrenDraft.map {
                    CreateBondChildRequest(it.firstName, it.lastName, it.dateOfBirth)
                },
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingBond = false,
                        showBondWizard = false,
                        bondWizardStep = 0,
                        newBondTitle = "",
                        bondMemberIds = emptyList(),
                        bondUserQuery = "",
                        bondChildrenDraft = emptyList(),
                    )
                    loadBonds()
                    loadHealth()
                    openBond(result.data.id)
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

    // ── F5 · Alta de progenitor en diálogo ──────────────────────────

    fun openNewUserDialog(fromWizard: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            showNewUserDialog = true,
            newUserFromWizard = fromWizard,
            error = null,
        )
    }

    fun closeNewUserDialog() {
        _uiState.value = _uiState.value.copy(showNewUserDialog = false, newUserFromWizard = false)
    }

    fun openAddChild() {
        _uiState.value = _uiState.value.copy(showAddChild = true, error = null)
    }

    fun closeAddChild() {
        _uiState.value = _uiState.value.copy(showAddChild = false)
    }

    fun updateBondSearch(value: String) {
        _uiState.value = _uiState.value.copy(bondSearch = value)
    }

    /** F3 · abre el detalle: hijos, tendencia y (al entrar a Reporte) el reporte. */
    fun openBond(bondId: String) {
        _uiState.value = _uiState.value.copy(
            openBondId = bondId,
            reportBondId = bondId,
            bondDetailTab = 0,
            editingChildId = null,
            error = null,
        )
        selectBond(bondId)
        loadTrend(bondId)
    }

    fun backToBondList() {
        _uiState.value = _uiState.value.copy(
            openBondId = null,
            bondDetailTab = 0,
            editingChildId = null,
            trend = emptyList(),
        )
    }

    fun setBondDetailTab(index: Int) {
        val bondId = _uiState.value.openBondId
        _uiState.value = _uiState.value.copy(bondDetailTab = index)
        if (index == REPORT_TAB && bondId != null) {
            val current = _uiState.value.report
            if (current == null || current.bondId != bondId) loadReport()
        }
    }

    private fun loadTrend(bondId: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTrend = true, trend = emptyList())
            _uiState.value = when (val result = adminApi.getBondTrend(token, bondId)) {
                is ApiResult.Success -> _uiState.value.copy(
                    trend = result.data.months,
                    isLoadingTrend = false,
                )
                is ApiResult.Error -> _uiState.value.copy(
                    error = result.message,
                    isLoadingTrend = false,
                )
            }
        }
    }

    /** F3 · carga el reporte sólo si el que hay no es ya el del vínculo elegido. */
    fun loadReportIfNeeded() {
        val bondId = _uiState.value.reportBondId ?: return
        val current = _uiState.value.report
        if (current == null || current.bondId != bondId) loadReport()
    }

    fun openPdfPreview() {
        _uiState.value = _uiState.value.copy(showPdfPreview = true)
    }

    fun closePdfPreview() {
        _uiState.value = _uiState.value.copy(showPdfPreview = false)
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
                    val created = result.data
                    _uiState.value = _uiState.value.let { st ->
                        // F5 · si la cuenta se creó desde el wizard, queda elegida:
                        // el admin no tiene que volver a buscarla.
                        val selected = if (st.newUserFromWizard && st.bondMemberIds.size < 2) {
                            st.bondMemberIds + created.id
                        } else {
                            st.bondMemberIds
                        }
                        st.copy(
                            bondMemberIds = selected,
                            users = if (st.users.any { it.id == created.id }) st.users else st.users + created,
                            newUserEmail = "",
                            newUserPassword = "",
                            newUserFirstName = "",
                            newUserLastName = "",
                            isCreatingUser = false,
                            showNewUserDialog = false,
                            newUserFromWizard = false,
                        )
                    }
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
                        showAddChild = false,
                    )
                    selectBond(bondId)
                    loadHealth()
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

/** F3 · índice de la solapa Reporte dentro del detalle del vínculo. */
const val REPORT_TAB = 2

private const val PDF_POLL_ATTEMPTS = 15
private const val PDF_POLL_DELAY_MS = 2_000L

/** F5 · hijo cargado dentro del wizard, antes de mandarse al backend. */
data class BondChildDraft(
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String? = null,
)
