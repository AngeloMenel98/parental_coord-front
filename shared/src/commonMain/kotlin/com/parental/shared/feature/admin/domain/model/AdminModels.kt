package com.parental.shared.feature.admin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AdminUser(
    val id: String,
    val email: String,
    val systemRole: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null,
)

@Serializable
data class AdminBond(
    val id: String,
    val title: String,
    val agreementType: String,
    val isActive: Boolean = true,
    val members: List<BondMemberInfo> = emptyList(),
    val childrenCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class BondMemberInfo(
    val id: String,
    val email: String? = null,
    val role: String,
)

@Serializable
data class AdminChild(
    val id: String,
    val bondId: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String? = null,
    val createdAt: String? = null,
)

// ── F2 · Reportes ────────────────────────────────────────────────────
// Los nombres calcados del JSON del backend: `GET /admin/bonds/:id/report`.
// Todos los campos tienen default para que un backend viejo no rompa el parseo.

@Serializable
data class AdminReportActivitySection(
    val total: Int = 0,
    val completed: Int = 0,
    val overdue: Int = 0,
    val completionRate: Double = 0.0,
    val byStatus: Map<String, Int> = emptyMap(),
    val byCriticality: Map<String, Int> = emptyMap(),
)

@Serializable
data class AdminReportExpenseStatusBucket(
    val count: Int = 0,
    val amount: Double = 0.0,
)

@Serializable
data class AdminReportExpenseCategory(
    val categoryId: String? = null,
    val categoryName: String? = null,
    val count: Int = 0,
    val amount: Double = 0.0,
)

@Serializable
data class AdminReportExpenseSection(
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val paidAmountPercentage: Double = 0.0,
    val paidCountPercentage: Double = 0.0,
    val byStatus: Map<String, AdminReportExpenseStatusBucket> = emptyMap(),
    val byCategory: List<AdminReportExpenseCategory> = emptyList(),
)

@Serializable
data class AdminReportChild(
    val id: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String? = null,
    val ageInYears: Int? = null,
    val activityCount: Int = 0,
)

@Serializable
data class AdminReportMember(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val assigned: Int = 0,
    val completed: Int = 0,
    val completionPercentage: Double = 0.0,
)

@Serializable
data class AdminBondReport(
    val bondId: String,
    val bondTitle: String,
    val dateFrom: String,
    val dateTo: String,
    val generatedAt: String,
    val activities: AdminReportActivitySection = AdminReportActivitySection(),
    val expenses: AdminReportExpenseSection = AdminReportExpenseSection(),
    val children: List<AdminReportChild> = emptyList(),
    val members: List<AdminReportMember> = emptyList(),
)

/** `POST /admin/bonds/:id/reports` — 202 con la fila recién creada. */
@Serializable
data class AdminReportRequest(
    val id: String,
    val status: String,
    val bondId: String,
    val dateFrom: String,
    val dateTo: String,
)

/** `GET /admin/reports/:id` — `fileUrl` firmada sólo cuando ya está completado. */
@Serializable
data class AdminReport(
    val id: String,
    val bondId: String,
    val status: String,
    val dateFrom: String,
    val dateTo: String,
    val fileUrl: String? = null,
    val generatedAt: String? = null,
    val createdAt: String? = null,
)

// ── F3 · Salud y tendencia (artboard admin-ui/01) ────────────────────

/** `GET /admin/bonds/health` — una fila por vínculo, con ceros si no hay datos. */
@Serializable
data class AdminBondHealth(
    val bondId: String,
    val title: String,
    val agreementType: String,
    val isActive: Boolean = true,
    val membersCount: Int = 0,
    val childrenCount: Int = 0,
    val activitiesTotal: Int = 0,
    val completionRate: Double = 0.0,
    val paidAmountPercentage: Double = 0.0,
)

/** `GET /admin/bonds/:id/trend` — serie mensual, más vieja primero. */
@Serializable
data class AdminBondTrendPoint(
    val month: String,
    val activitiesTotal: Int = 0,
    val completionRate: Double = 0.0,
)

@Serializable
data class AdminBondTrend(
    val bondId: String,
    val months: List<AdminBondTrendPoint> = emptyList(),
)
