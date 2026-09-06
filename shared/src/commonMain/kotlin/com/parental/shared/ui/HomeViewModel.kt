package com.parental.shared.ui

import android.util.Log
import com.parental.shared.api.HomeApi
import com.parental.shared.api.onFailure
import com.parental.shared.api.onSuccess
import com.parental.shared.model.HomeSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "HOME_DEBUG"

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: HomeSummary? = null,
    val userName: String = "",
    val selectedTab: String = "home",
)

class HomeViewModel(
    private val homeApi: HomeApi,
    private val token: String,
    private val userName: String,
    private val bondId: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(HomeUiState(userName = userName))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        val currentBondId = bondId
        Log.d(TAG, "loadHomeData called with bondId: $currentBondId")
        if (currentBondId == null) {
            Log.w(TAG, "No bondId available - showing 'No hay vínculo asociado'")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "No hay vínculo asociado",
            )
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "Fetching home summary for bondId: $currentBondId")

            homeApi.getHomeSummary(token, currentBondId)
                .onSuccess { summary ->
                    Log.d(TAG, "Home summary received: activities=${summary.upcomingActivities.size}, child=${summary.child?.firstName}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        summary = summary,
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load home: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load home data",
                    )
                }
        }
    }

    fun selectTab(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun destroy() {
        scope.cancel()
    }

    fun onLogout() {
        // Handled by App-level navigation
    }
}
