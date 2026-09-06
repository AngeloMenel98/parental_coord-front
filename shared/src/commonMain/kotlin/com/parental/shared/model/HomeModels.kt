package com.parental.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChildProfile(
    val id: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val coordinatorName: String? = null,
)

@Serializable
data class ComplianceData(
    val parent1Name: String,
    val parent1Percentage: Float,
    val parent2Name: String,
    val parent2Percentage: Float,
)

@Serializable
data class ActivitySummary(
    val id: String,
    val title: String,
    val type: String,          // "EVENT" | "OBLIGATION"
    val category: String,      // "SALUD" | "EDUCACION" | "FAMILIAR" | "SOCIAL" | "RECREACION"
    val status: String,        // "CREATED" | "ASSIGNED" | "IN_PROGRESS" | "PENDING_VERIFICATION" | "COMPLETED" | "OVERDUE"
    val assignedTo: String,
    val scheduledDate: String,
    val dueDate: String? = null,
    val priority: String,      // "URGENT" | "HIGH" | "MEDIUM" | "LOW"
)

@Serializable
data class HomeSummary(
    val child: ChildProfile?,
    val compliance: ComplianceData?,
    val pendingActivities: Int,
    val overdueActivities: Int,
    val pendingExpenses: Int,
    val upcomingActivities: List<ActivitySummary>,
    val unreadNotifications: Int,
) {
    companion object {
        val EMPTY = HomeSummary(
            child = null,
            compliance = null,
            pendingActivities = 0,
            overdueActivities = 0,
            pendingExpenses = 0,
            upcomingActivities = emptyList(),
            unreadNotifications = 0,
        )
    }
}
