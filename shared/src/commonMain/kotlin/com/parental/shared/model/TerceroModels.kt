package com.parental.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tercero(
    @SerialName("id") val id: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("role") val role: String, // "COORDINATOR" | "THERAPIST" | "TUTOR" | "LEGAL" | "OTHER"
    @SerialName("status") val status: String, // "ACTIVE" | "INACTIVE" | "PENDING"
    @SerialName("scope") val scope: String, // "CHILD" | "BOTH_PARENTS" | "ONE_PARENT"
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TerceroDetalle(
    @SerialName("id") val id: String,
    @SerialName("nombre") val nombre: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("role") val role: String,
    @SerialName("status") val status: String,
    @SerialName("scope") val scope: String,
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("access_permissions") val accessPermissions: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CreateTerceroRequest(
    @SerialName("nombre") val nombre: String,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("role") val role: String,
    @SerialName("scope") val scope: String = "CHILD",
    @SerialName("bond_id") val bondId: String? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class UpdateTerceroRequest(
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class RevokeTerceroRequest(
    @SerialName("reason") val reason: String? = null,
)
