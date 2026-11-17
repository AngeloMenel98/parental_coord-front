package com.parental.shared.feature.home.domain.model

import com.parental.shared.feature.home.domain.ChildStatus
import com.parental.shared.feature.home.domain.TodayAgenda
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
    @SerialName("bondActive") val bondActive: Boolean = true,
)

/**
 * Las tres métricas del Home (T20).
 *
 * `assignedToMe` y `overdue` los calcula el CLIENTE sobre la lista del vínculo
 * (R11). El saldo lo manda el servidor (R1) y el cliente NUNCA lo recalcula: el
 * dinero se calcula en un solo lugar.
 *
 * Cada campo es nullable a propósito: `null` = "no se pudo obtener" (la lista o el
 * resumen fallaron) y la tarjeta lo muestra como no disponible. Un `0` de mentira es
 * peor que un hueco — el usuario lo lee como un dato.
 */
@Serializable
data class HomeMetrics(
    val assignedToMe: Int? = null,
    val overdue: Int? = null,
    val balance: Double? = null,
)

@Serializable
data class HomeSummary(
    @SerialName("children") val children: List<ChildProfile>,
    @SerialName("memberCount") val memberCount: Int = 0,
    @SerialName("bondId") val bondId: String? = null,
    @SerialName("compliance") val compliance: ComplianceResponse? = null,
    @SerialName("metrics") val metrics: HomeMetrics = HomeMetrics(),
    /**
     * S2 · la agenda de HOY, derivada en el cliente de la lista del vínculo (ningún
     * request extra). `null` = no se pudo cargar: la tarjeta lo dice, no finge vacío.
     */
    val today: TodayAgenda? = null,
    /**
     * S5 · la carga vencida de cada hijo. Vacía = no se pudo calcular (la sección cae a
     * nombres sin estado); `overdue = 0` sí significa "al día".
     */
    val childStatuses: List<ChildStatus> = emptyList(),
)
