package com.parental.shared.model

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
