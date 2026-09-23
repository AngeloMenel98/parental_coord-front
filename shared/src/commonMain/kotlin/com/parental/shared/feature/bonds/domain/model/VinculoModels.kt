package com.parental.shared.feature.bonds.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vinculo(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("agreementType") val agreementType: String,
    @SerialName("courtCaseRef") val courtCaseRef: String? = null,
    @SerialName("startDate") val startDate: String? = null,
    @SerialName("endDate") val endDate: String? = null,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("members") val members: List<VinculoMember> = emptyList(),
    @SerialName("children") val children: List<VinculoChild> = emptyList(),
)

@Serializable
data class VinculoMember(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("joinedAt") val joinedAt: String? = null,
    @SerialName("user") val user: VinculoMemberUser? = null,
)

@Serializable
data class VinculoMemberUser(
    @SerialName("id") val id: String,
    @SerialName("firstName") val firstName: String? = null,
    @SerialName("lastName") val lastName: String? = null,
)

@Serializable
data class VinculoChild(
    @SerialName("id") val id: String,
    @SerialName("firstName") val firstName: String,
    @SerialName("lastName") val lastName: String,
)
