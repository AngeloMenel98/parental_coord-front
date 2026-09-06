package com.parental.shared.api

import com.parental.shared.model.HomeSummary
import com.parental.shared.model.VinculoDetalle
import com.parental.shared.model.VinculoCompliance
import com.parental.shared.model.VinculoChild
import com.parental.shared.model.ActivitySummary
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API layer for Home screen data.
 * Makes real API calls using BondsApi for bond summary, compliance, and activities.
 */
class HomeApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private val bondsApi = BondsApi(client, baseUrl)

    suspend fun getHomeSummary(token: String, bondId: String): ApiResult<HomeSummary> {
        return try {
            // Fetch bond summary, compliance, children, and activities in parallel
            val bondResult = bondsApi.getBondSummary(token, bondId)
            val complianceResult = bondsApi.getCompliance(token, bondId)
            val childrenResult = bondsApi.getChildren(token, bondId)

            // Fetch activities for the bond
            val activitiesResult = try {
                val response = client.get("$baseUrl/bonds/$bondId/activities") {
                    header("Authorization", "Bearer $token")
                    parameter("limit", "5")
                }
                if (response.status.isSuccess()) {
                    ApiResult.Success(response.body<List<ActivitySummary>>())
                } else {
                    ApiResult.Error(response.status.value, response.status.description)
                }
            } catch (e: Exception) {
                ApiResult.Error(0, e.message ?: "Network error")
            }

            // Fetch pending expenses count
            val pendingExpensesCount = try {
                val response = client.get("$baseUrl/bonds/$bondId/expenses/summary") {
                    header("Authorization", "Bearer $token")
                }
                if (response.status.isSuccess()) {
                    val summary = response.body<com.parental.shared.model.GastoSummary>()
                    summary.countPending
                } else {
                    0
                }
            } catch (e: Exception) {
                0
            }

            // Combine results into HomeSummary
            val child = when (childrenResult) {
                is ApiResult.Success -> childrenResult.data.firstOrNull()
                else -> null
            }

            val compliance = when (complianceResult) {
                is ApiResult.Success -> {
                    val comp = complianceResult.data
                    com.parental.shared.model.ComplianceData(
                        parent1Name = comp.parent1Name,
                        parent1Percentage = comp.parent1Percentage,
                        parent2Name = comp.parent2Name,
                        parent2Percentage = comp.parent2Percentage,
                    )
                }
                else -> null
            }

            val activities = when (activitiesResult) {
                is ApiResult.Success -> activitiesResult.data
                else -> emptyList()
            }

            val childProfile = child?.let {
                com.parental.shared.model.ChildProfile(
                    id = it.id,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    dateOfBirth = it.dateOfBirth ?: "",
                )
            }

            ApiResult.Success(
                HomeSummary(
                    child = childProfile,
                    compliance = compliance,
                    pendingActivities = activities.count {
                        it.status in listOf("CREATED", "ASSIGNED", "IN_PROGRESS")
                    },
                    overdueActivities = activities.count { it.status == "OVERDUE" },
                    pendingExpenses = pendingExpensesCount,
                    upcomingActivities = activities.take(5),
                    unreadNotifications = 0,
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}
