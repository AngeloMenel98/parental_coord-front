package com.parental.shared.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parental.shared.core.error.onFailure
import com.parental.shared.core.error.onSuccess
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.home.domain.model.HomeSummary
import com.parental.shared.feature.session.data.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: HomeSummary? = null,
    val userName: String = "",
    val complianceError: String? = null,
)

class HomeViewModel(
    private val homeApi: HomeApi,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(userName = sessionManager.userName.orEmpty()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Carga en curso: un nuevo "Reintentar" la cancela en vez de correr en paralelo. */
    private var loadJob: Job? = null

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            homeApi.getHomeSummary(currentUserId = sessionManager.userId)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                            // El compliance viaja DENTRO del summary (main): si el vínculo
                            // existe pero el cumplimiento no vino, es un fallo parcial y la
                            // tarjeta de cumplimiento muestra su propio error.
                            complianceError = if (summary.bondId != null && summary.compliance == null) {
                                "No se pudo cargar el cumplimiento"
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // El error es el RuntimeException del onFailure (HTTP <code>:
                            // <mensaje>); su message ya es apto para el usuario.
                            error = error.message?.ifBlank { null } ?: "No se pudo cargar el resumen",
                        )
                    }
                }
        }
    }

    fun refresh() {
        loadHomeData()
    }
}
