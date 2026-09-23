package com.parental.shared.feature.home.domain.model

import kotlinx.serialization.SerialName
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
data class ComplianceMember(
    @SerialName("userId") val userId: String,
    @SerialName("firstName") val firstName: String,
    @SerialName("lastName") val lastName: String,
    @SerialName("completed") val completed: Int,
    @SerialName("total") val total: Int,
    @SerialName("percentage") val percentage: Double,
)

@Serializable
data class ComplianceResponse(
    @SerialName("bondId") val bondId: String,
    @SerialName("period") val period: String,
    @SerialName("members") val members: List<ComplianceMember>,
    @SerialName("bondActive") val bondActive: Boolean,
)

@Serializable
data class HomeSummary(
    @SerialName("children") val children: List<ChildProfile>,
    @SerialName("bondTitle") val bondTitle: String? = null,
    @SerialName("bondType") val bondType: String? = null,
    @SerialName("memberCount") val memberCount: Int = 0,
    @SerialName("bondId") val bondId: String? = null,
    @SerialName("compliance") val compliance: ComplianceResponse? = null,
)
