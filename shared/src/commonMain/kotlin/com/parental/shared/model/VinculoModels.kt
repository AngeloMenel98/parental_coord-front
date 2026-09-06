package com.parental.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vinculo(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("agreement_type") val agreementType: String, // "formal" | "informal"
    @SerialName("status") val status: String, // "ACTIVE" | "INACTIVE" | "PENDING"
    @SerialName("members") val members: List<VinculoMember> = emptyList(),
    @SerialName("children_count") val childrenCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class VinculoDetalle(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("agreement_type") val agreementType: String,
    @SerialName("status") val status: String,
    @SerialName("members") val members: List<VinculoMember> = emptyList(),
    @SerialName("children") val children: List<VinculoChild> = emptyList(),
    @SerialName("compliance") val compliance: VinculoCompliance? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class VinculoMember(
    @SerialName("user_id") val userId: String,
    @SerialName("email") val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("role") val role: String, // "PARENT_1" | "PARENT_2"
)

@Serializable
data class VinculoChild(
    @SerialName("id") val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
)

@Serializable
data class VinculoCompliance(
    @SerialName("parent1_percentage") val parent1Percentage: Float = 0f,
    @SerialName("parent2_percentage") val parent2Percentage: Float = 0f,
    @SerialName("parent1_name") val parent1Name: String = "",
    @SerialName("parent2_name") val parent2Name: String = "",
)
