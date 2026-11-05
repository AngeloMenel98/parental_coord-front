package com.parental.shared.feature.activities.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.session.data.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityTab(val label: String) {
    TODOS("Todos"),
    EVENTOS("Eventos"),
    OBLIGACIONES("Obligaciones"),
}

data class ActivitiesUiState(
    val isLoading: Boolean = true,
    val listError: String? = null,
    val actividades: List<Actividad> = emptyList(),
    val selectedTab: ActivityTab = ActivityTab.TODOS,
    val selectedActividad: ActividadDetalle? = null,
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    /** uuid → info resuelta de GET /categories + preferencias por nombre. */
    val categories: Map<String, CategoryInfo> = emptyMap(),
    /** userId → "Nombre Apellido" (del vínculo activo). */
    val memberNames: Map<String, String> = emptyMap(),
    val snackbarMessage: String? = null,
    /** True SOLO mientras la confirmación está en vuelo (single-flight global). */
    val isConfirming: Boolean = false,
)

class ActividadesViewModel(
    private val activitiesApi: ActivitiesApi,
    private val categoriesApi: CategoriesApi,
    private val bondsApi: BondsApi,
    private val sessionManager: SessionManager,
) : ViewModel() {
    /** Vínculo activo (la pantalla solo se muestra con bondId != null). */
    private val bondId: String get() = sessionManager.bondId.orEmpty()

    /**
     * userId del usuario autenticado, derivado del estado de sesión: si el
     * backfill vía /auth/me llega tarde, la UI se actualiza sin recrear el VM.
     */
    val currentUserId: StateFlow<String> = sessionManager.state
        .map { it.userId.orEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.userId.orEmpty())

    private val _uiState = MutableStateFlow(ActivitiesUiState())
    val uiState: StateFlow<ActivitiesUiState> = _uiState.asStateFlow()

    private var lastSelectedId: String? = null

    /** Requests en curso: uno nuevo cancela al anterior (sin respuestas fuera de orden). */
    private var loadJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadAll()
    }

    /** Carga lista + categorías + nombres de miembros en paralelo. */
    fun loadAll() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, listError = null) }

            coroutineScope {
                val listDeferred = async { activitiesApi.listActividades(bondId) }
                val categoriesDeferred = async { categoriesApi.getCategories() }
                val bondsDeferred = async { bondsApi.getMyBonds() }

                val listResult = listDeferred.await()
                val categoriesResult = categoriesDeferred.await()
                val bondsResult = bondsDeferred.await()

                // Fallas de categorías/miembros no bloquean la lista:
                // categorías desconocidas → chip neutral; asignatario → "Sin asignar".
                val categories = when (categoriesResult) {
                    is ApiResult.Success -> CategoryResolver.build(categoriesResult.data)
                    is ApiResult.Error -> emptyMap()
                }
                val memberNames = when (bondsResult) {
                    is ApiResult.Success -> bondsResult.data
                        .firstOrNull { it.id == bondId }
                        ?.members
                        ?.mapNotNull { member ->
                            val user = member.user ?: return@mapNotNull null
                            val name = listOfNotNull(user.firstName, user.lastName)
                                .joinToString(" ")
                                .trim()
                            if (name.isBlank()) null else user.id to name
                        }
                        ?.toMap()
                        ?: emptyMap()
                    is ApiResult.Error -> emptyMap()
                }

                when (listResult) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                actividades = listResult.data,
                                isLoading = false,
                                categories = categories,
                                memberNames = memberNames,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                listError = listResult.message,
                                isLoading = false,
                                categories = categories,
                                memberNames = memberNames,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Filtro puramente client-side sobre la lista ya cargada (sin refetch). */
    fun selectTab(tab: ActivityTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** Reintentar desde el estado de error de la lista → esqueleto + refetch. */
    fun retry() {
        loadAll()
    }

    fun selectActividad(id: String) {
        lastSelectedId = id
        // Dos taps rápidos: solo el último detalle llega a la UI.
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, detailError = null) }

            when (val result = activitiesApi.getActividad(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedActividad = result.data,
                            isDetailLoading = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    // Falló el detalle → permanece en la lista con banner de error.
                    _uiState.update {
                        it.copy(
                            detailError = result.message,
                            isDetailLoading = false,
                        )
                    }
                }
            }
        }
    }

    /** Reintentar el último detalle seleccionado (banner de error de detalle). */
    fun retryDetail() {
        lastSelectedId?.let { selectActividad(it) }
    }

    fun backToList() {
        _uiState.update { it.copy(selectedActividad = null, detailError = null) }
    }

    /** Dispara el snackbar para un acción inerte (placeholders). */
    fun onInertAction(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    /**
     * POST /activities/:id/confirm (única mutación permitida, R14).
     * Single-flight: mientras isConfirming, los taps siguientes se ignoran.
     * Success → aplicar estado del payload (NO refetch) + snackbar, sin navegar.
     * Error → snackbar con mensaje del backend (403 → texto localizado).
     */
    fun confirmAssignment(activityId: String) {
        if (_uiState.value.isConfirming) return
        // Se marca antes de lanzar: el flag queda puesto aunque el launch no corra inmediato.
        _uiState.update { it.copy(isConfirming = true) }
        viewModelScope.launch {
            when (val result = activitiesApi.confirmAssignment(activityId)) {
                is ApiResult.Success -> {
                    val r = result.data
                    _uiState.update { state ->
                        state.copy(
                            isConfirming = false,
                            actividades = state.actividades.map {
                                if (it.id == activityId) {
                                    it.copy(
                                        assignedConfirmed = r.assignedConfirmed,
                                        confirmedAt = r.confirmedAt,
                                    )
                                } else {
                                    it
                                }
                            },
                            selectedActividad = state.selectedActividad
                                ?.takeIf { it.id == activityId }
                                ?.copy(
                                    assignedConfirmed = r.assignedConfirmed,
                                    confirmedAt = r.confirmedAt,
                                )
                                ?: state.selectedActividad,
                            snackbarMessage = "Asignación confirmada",
                        )
                    }
                }
                is ApiResult.Error -> {
                    val text = if (result.code == 403) {
                        "Solo el asignado puede confirmar"
                    } else {
                        result.message
                    }
                    _uiState.update {
                        it.copy(
                            isConfirming = false,
                            snackbarMessage = text,
                        )
                    }
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
