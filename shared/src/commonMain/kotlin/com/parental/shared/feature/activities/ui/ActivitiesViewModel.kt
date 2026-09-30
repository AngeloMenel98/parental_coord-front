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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActivityTab(val label: String) {
    TODOS("Todos"),
    EVENTOS("Eventos"),
    OBLIGACIONES("Obligaciones"),
}

/** Estado del detalle de UNA actividad — R1. El **id es la única clave**: sustituye a
 *  `selectedActividad`/`isDetailLoading`/`detailError`/`lastSelectedId`, y clavear por
 *  *payload* haría el estado de carga irrepresentable (mientras `Loading` no hay payload
 *  que no sea `null`). Por eso la hoja se compone sobre `!= Closed`, no sobre "hay algo
 *  seleccionado". [Content.id] es siempre igual a `actividad.id`. */
sealed interface DetailUiState {
    /** Ninguna hoja abierta. Es el estado inicial y el único tras un descarte. */
    data object Closed : DetailUiState

    /** Petición en vuelo para [id] — la hoja pinta su esqueleto (R5). */
    data class Loading(val id: String) : DetailUiState

    /** Falló `GET /activities/:id` para [id] — la hoja pinta el error inline (R6). */
    data class Error(val id: String, val message: String) : DetailUiState

    /** Detalle cargado de [id]. */
    data class Content(val id: String, val actividad: ActividadDetalle) : DetailUiState
}

/** Id de la actividad abierta, o `null` si no hay ninguna — R1/R2. Existe porque
 *  [DetailUiState.Closed] no lleva `id` y una interfaz sellada no admite un smart cast
 *  "todo menos `Closed`": tras `if (current is Closed) return` el tipo sigue siendo
 *  `DetailUiState` y `current.id` no resuelve. ⚠ La misma regla aplica en la UI: `uiState`
 *  es una `val` delegada (`by collectAsState()`) y no admite smart cast, de modo que el `as`
 *  explícito de la rama `Content` en `ActivitiesScreen` es necesario, no redundante. */
internal val DetailUiState.openedId: String?
    get() = when (this) {
        DetailUiState.Closed -> null
        is DetailUiState.Loading -> id
        is DetailUiState.Error -> id
        is DetailUiState.Content -> id
    }

data class ActivitiesUiState(
    val isLoading: Boolean = true,
    val listError: String? = null,
    val actividades: List<Actividad> = emptyList(),
    val selectedTab: ActivityTab = ActivityTab.TODOS,
    /** Detalle por id — R1. `Closed` = no hay hoja; el resto lleva el id de la actividad. */
    val detail: DetailUiState = DetailUiState.Closed,
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

    /** Contador de generación del detalle — R1, y la GARANTÍA de que una respuesta obsoleta
     *  no escriba. ⚠ CONFINADO al hilo principal: los `++` salen del toque y del descarte, y
     *  la comparación de la respuesta va también en `Dispatchers.Main` ([selectActividad]);
     *  así la lectura entre hilos desaparece por construcción y no hace falta `@Volatile`.
     *  ⚠ `detailJob?.cancel()` NO basta: `getActividad` envuelve su `client.get` en
     *  `catch (e: Exception)` (`ActivitiesApi.kt:68`) y `CancellationException` ES una
     *  `Exception` ⇒ un job cancelado igual escribe. `cancel()` sólo libera el socket. */
    private var detailGeneration = 0

    /** Petición de detalle en vuelo. Sólo se cancela para liberar el socket. */
    private var detailJob: Job? = null

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

    /** Abre el detalle de [id] — R1. `Loading(id)` se escribe **fuera** del `launch` para
     *  que la hoja aparezca en el mismo frame del toque; [gen] se captura antes de lanzarlo
     *  y su comparación posterior es la que invalida la respuesta. */
    fun selectActividad(id: String) {
        val gen = ++detailGeneration
        _uiState.value = _uiState.value.copy(detail = DetailUiState.Loading(id))
        detailJob?.cancel()
        detailJob = scope.launch {
            val result = activitiesApi.getActividad(token, id)
            // ⚠ Comparación y escritura en el hilo principal, que es donde vive
            // [detailGeneration]: la carrera entre hilos desaparece por construcción (R-1).
            withContext(Dispatchers.Main) {
                // Obsoleta (descarte, o re-tap de otra): no escribe NADA, ni un campo.
                if (gen != detailGeneration) return@withContext
                _uiState.value = _uiState.value.copy(
                    detail = when (result) {
                        is ApiResult.Success -> DetailUiState.Content(id, result.data)
                        is ApiResult.Error -> DetailUiState.Error(id, result.message)
                    },
                )
            }
        }
    }

    /** Cierra el detalle de [id] — R1/R2, el único reseteo. Idempotente Y con guarda de id:
     *  un descarte de una apertura anterior (el `hide()` tardío) no toca la apertura viva, y
     *  el `detailGeneration++` deja obsoleta cualquier respuesta en vuelo para que no reabra
     *  la hoja ni pinte un error en la lista. */
    fun closeDetail(id: String) {
        if (_uiState.value.detail.openedId != id) return
        detailGeneration++
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(detail = DetailUiState.Closed)
    }

    /** Reintenta el detalle que falló — R6. Lee `Error.id` (ya no hay `lastSelectedId` que
     *  pueda quedar obsoleto) y reentra en `Loading(id)` → `Content`, sin casos especiales. */
    fun retryDetail() {
        val current = _uiState.value.detail
        if (current is DetailUiState.Error) selectActividad(current.id)
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
                    val detail = _uiState.value.detail
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
                        // R1: el `Content` se REESCRIBE EN SITU por id; sin esta rama el chip
                        // "Asignación confirmada" quedaría obsoleto (la hoja vive sobre él).
                        detail = if (detail is DetailUiState.Content && detail.id == activityId) {
                            DetailUiState.Content(
                                id = activityId,
                                actividad = detail.actividad.copy(
                                    assignedConfirmed = r.assignedConfirmed,
                                    confirmedAt = r.confirmedAt,
                                ),
                            )
                        } else {
                            detail
                        },
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
