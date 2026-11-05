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
    val complianceLoading: Boolean = false,
    val complianceError: String? = null,
)

class HomeViewModel(
    private val homeApi: HomeApi,
    sessionManager: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(userName = sessionManager.userName.orEmpty()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Carga en curso: un nuevo "Reintentar" la cancela en vez de correr en paralelo. */
    private var loadJob: Job? = null
    private var complianceJob: Job? = null

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        loadJob?.cancel()
        complianceJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            homeApi.getHomeSummary()
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                        )
                    }
                    // Fetch compliance for the first bond
                    val bondId = summary.bondId
                    if (bondId != null) {
                        fetchCompliance(bondId)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load home data",
                        )
                    }
                }
        }
    }

    private fun fetchCompliance(bondId: String) {
        complianceJob?.cancel()
        complianceJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    complianceLoading = true,
                    complianceError = null,
                )
            }

            homeApi.getCompliance(bondId)
                .onSuccess { compliance ->
                    _uiState.update {
                        it.copy(
                            complianceLoading = false,
                            complianceError = null,
                            // Leído dentro de update: el summary vigente, no una copia previa.
                            summary = it.summary?.copy(compliance = compliance),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            complianceLoading = false,
                            complianceError = e.message ?: "Failed to load compliance",
                        )
                    }
                }
        }
    }
}
