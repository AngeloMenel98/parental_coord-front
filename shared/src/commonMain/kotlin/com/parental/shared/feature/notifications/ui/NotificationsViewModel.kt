package com.parental.shared.feature.notifications.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.notifications.data.remote.NotificationsApi
import com.parental.shared.feature.notifications.domain.model.NotificationItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isMarkingAll: Boolean = false,
    val error: String? = null,
    val items: List<NotificationItem> = emptyList(),
    val total: Int = 0,
    val unreadCount: Int = 0,
    /** ids con un `PATCH /{id}/read` en vuelo — evita dobles taps. */
    val readingIds: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
) {
    /** `total` es el total del backend, no el tamaño de [items] (S4). */
    val canLoadMore: Boolean get() = items.size < total
}

/**
 * Estado de la lista de notificaciones (poll-only, slice 4).
 *
 * **La lista es una bandeja de NO leídas** (`unreadOnly=true`, decisión del
 * 2026-10-31): una notificación leída no vuelve a aparecer, ni siquiera al
 * refrescar. Por eso "eliminar" —que para el backend es marcar leída— y
 * "marcar leída" son la MISMA operación, y las dos sacan la fila de [items].
 *
 * El backend NO devuelve `unreadCount` en el listado, así que el contador sale de
 * su propio endpoint y se refleja en [UnreadBadge] — el mismo holder que pinta el
 * badge de Home. Así "borrar todas" en esta pantalla apaga el badge sin esperar
 * al siguiente poll.
 *
 * `dispatcher` es inyectable por la misma razón que en `ActividadesViewModel`:
 * los tests avanzan el reloj virtual con `runTest` sin `Dispatchers.setMain`.
 */
class NotificationsViewModel(
    private val api: NotificationsApi,
    private val token: String,
    private val badge: UnreadBadge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val pageSize: Int = NotificationsApi.DEFAULT_PAGE_SIZE,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Recarga desde la primera página. Un fallo conserva lo ya cargado (S7). */
    fun refresh() {
        scope.launch {
            _uiState.update {
                it.copy(isLoading = it.items.isEmpty(), error = null)
            }

            when (val result = api.list(token, pageSize, 0, unreadOnly = true)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        items = result.data.items,
                        total = result.data.total,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                    )
                }
            }

            syncUnreadCount()
        }
    }

    /** Siguiente página; no duplica ids si el backend repitiese filas. */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return

        scope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            when (val result = api.list(token, pageSize, current.items.size, unreadOnly = true)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val known = state.items.mapTo(HashSet()) { it.id }
                    val appended = result.data.items.filterNot { it.id in known }
                    state.copy(
                        isLoadingMore = false,
                        items = state.items + appended,
                        total = result.data.total,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingMore = false, snackbarMessage = result.message)
                }
            }
        }
    }

    /**
     * S5/S1: el toque del usuario sobre una fila. Para el backend es "marcar
     * leída" (`PATCH /{id}/read`) — no existe borrado, y el doc de la wave fija
     * que "eliminar" es exactamente esto. Como la lista es una bandeja de no
     * leídas, la fila DESAPARECE: sacarla de [items] y bajar [NotificationsUiState.total]
     * evita que "Cargar más" quede prendido por un total que ya no existe.
     * Si la request falla, la fila se queda y el error va al snackbar.
     */
    fun markRead(id: String) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        if (item.isRead || id in _uiState.value.readingIds) return

        scope.launch {
            _uiState.update { it.copy(readingIds = it.readingIds + id) }
            when (val result = api.markRead(token, id)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            readingIds = state.readingIds - id,
                            items = state.items.filterNot { it.id == id },
                            total = (state.total - 1).coerceAtLeast(0),
                            unreadCount = (state.unreadCount - 1).coerceAtLeast(0),
                        )
                    }
                    badge.decrement()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        readingIds = it.readingIds - id,
                        snackbarMessage = result.message,
                    )
                }
            }
        }
    }

    /**
     * S6: "Borrar todas". Marca TODAS como leídas de una (`PATCH /read-all`) y
     * vacía la vista: con la bandeja de no leídas no queda nada que mostrar.
     * Idempotente contra dobles taps mientras hay una en vuelo.
     */
    fun markAllRead() {
        if (_uiState.value.isMarkingAll) return

        scope.launch {
            _uiState.update { it.copy(isMarkingAll = true) }
            when (val result = api.markAllRead(token)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isMarkingAll = false,
                            items = emptyList(),
                            total = 0,
                            unreadCount = 0,
                        )
                    }
                    badge.set(0)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isMarkingAll = false, snackbarMessage = result.message)
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun destroy() {
        scope.cancel()
    }

    private suspend fun syncUnreadCount() {
        when (val result = api.unreadCount(token)) {
            is ApiResult.Success -> {
                badge.set(result.data)
                _uiState.update { it.copy(unreadCount = result.data) }
            }
            // Fallo del contador: el badge conserva el último valor conocido.
            is ApiResult.Error -> Unit
        }
    }
}
