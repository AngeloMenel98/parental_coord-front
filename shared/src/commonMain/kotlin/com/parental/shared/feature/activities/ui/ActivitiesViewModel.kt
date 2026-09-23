package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.bonds.data.remote.BondsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val token: String,
    private val bondId: String,
    /** userId del usuario autenticado (non-null: la pantalla está gated por login). */
    val currentUserId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ActivitiesUiState())
    val uiState: StateFlow<ActivitiesUiState> = _uiState.asStateFlow()

    private var lastSelectedId: String? = null

    init {
        loadAll()
    }

    /** Carga lista + categorías + nombres de miembros en paralelo. */
    fun loadAll() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, listError = null)

            coroutineScope {
                val listDeferred = async { activitiesApi.listActividades(token, bondId) }
                val categoriesDeferred = async { categoriesApi.getCategories() }
                val bondsDeferred = async { bondsApi.getMyBonds(token) }

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
                        _uiState.value = _uiState.value.copy(
                            actividades = listResult.data,
                            isLoading = false,
                            categories = categories,
                            memberNames = memberNames,
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
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

    /** Filtro puramente client-side sobre la lista ya cargada (sin refetch). */
    fun selectTab(tab: ActivityTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /** Reintentar desde el estado de error de la lista → esqueleto + refetch. */
    fun retry() {
        loadAll()
    }

    fun selectActividad(id: String) {
        lastSelectedId = id
        scope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, detailError = null)

            when (val result = activitiesApi.getActividad(token, id)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedActividad = result.data,
                        isDetailLoading = false,
                    )
                }
                is ApiResult.Error -> {
                    // Falló el detalle → permanece en la lista con banner de error.
                    _uiState.value = _uiState.value.copy(
                        detailError = result.message,
                        isDetailLoading = false,
                    )
                }
            }
        }
    }

    /** Reintentar el último detalle seleccionado (banner de error de detalle). */
    fun retryDetail() {
        lastSelectedId?.let { selectActividad(it) }
    }

    fun backToList() {
        _uiState.value = _uiState.value.copy(selectedActividad = null, detailError = null)
    }

    /** Dispara el snackbar para un acción inerte (placeholders). */
    fun onInertAction(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    /**
     * POST /activities/:id/confirm (única mutación permitida, R14).
     * Single-flight: mientras isConfirming, los taps siguientes se ignoran.
     * Success → aplicar estado del payload (NO refetch) + snackbar, sin navegar.
     * Error → snackbar con mensaje del backend (403 → texto localizado).
     */
    fun confirmAssignment(activityId: String) {
        if (_uiState.value.isConfirming) return
        scope.launch {
            _uiState.value = _uiState.value.copy(isConfirming = true)
            when (val result = activitiesApi.confirmAssignment(token, activityId)) {
                is ApiResult.Success -> {
                    val r = result.data
                    _uiState.value = _uiState.value.copy(
                        isConfirming = false,
                        actividades = _uiState.value.actividades.map {
                            if (it.id == activityId) {
                                it.copy(
                                    assignedConfirmed = r.assignedConfirmed,
                                    confirmedAt = r.confirmedAt,
                                )
                            } else {
                                it
                            }
                        },
                        selectedActividad = _uiState.value.selectedActividad
                            ?.takeIf { it.id == activityId }
                            ?.copy(
                                assignedConfirmed = r.assignedConfirmed,
                                confirmedAt = r.confirmedAt,
                            )
                            ?: _uiState.value.selectedActividad,
                        snackbarMessage = "Asignación confirmada",
                    )
                }
                is ApiResult.Error -> {
                    val text = if (result.code == 403) {
                        "Solo el asignado puede confirmar"
                    } else {
                        result.message
                    }
                    _uiState.value = _uiState.value.copy(
                        isConfirming = false,
                        snackbarMessage = text,
                    )
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun destroy() {
        scope.cancel()
    }
}
