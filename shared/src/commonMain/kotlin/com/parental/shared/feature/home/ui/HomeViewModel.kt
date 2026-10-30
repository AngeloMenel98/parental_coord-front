package com.parental.shared.feature.home.ui

import com.parental.shared.core.error.onFailure
import com.parental.shared.core.error.onSuccess
import com.parental.shared.feature.home.data.remote.HomeApi
import com.parental.shared.feature.home.domain.model.HomeSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val token: String,
    private val userName: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(HomeUiState(userName = userName))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            homeApi.getHomeSummary(token)
                .onSuccess { summary ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        summary = summary,
                        complianceError = if (summary.bondId != null && summary.compliance == null) {
                            "No se pudo cargar el cumplimiento"
                        } else {
                            null
                        },
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load home data",
                    )
                }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
