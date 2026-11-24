package com.parental.shared.feature.admin.ui

import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import kotlinx.datetime.LocalDate

/*
 * L21 · helpers puros del detalle del vínculo (artboard `admin-ui/01`, frame 2 rehecho).
 *
 * El detalle muestra la participación de cada progenitor y la actividad por hijo. Esos números
 * viven en el reporte (`GET /admin/bonds/:id/report`), que se carga en background al abrir el
 * detalle — la sección de Reporte ya no se renderiza acá (el usuario la pidió fuera porque estaba
 * duplicada), pero los DATOS siguen viniendo del mismo lugar. El merge con `bond.members` es
 * puro y testeado para que la fila del detalle y el reporte no puedan decir cosas distintas.
 */

/** Una fila de la sección Progenitores del detalle. */
internal data class DetailMember(
    val userId: String,
    val name: String,
    val roleLabel: String,
    val completed: Int,
    val assigned: Int,
    val percentage: Double,
)

/**
 * Combina los miembros del vínculo (rol, email) con la participación del reporte (nombre,
 * completadas/asignadas). Quien el reporte no conoce no desaparece: cae al prefijo del email
 * con números en cero, así la fila aparece igual y deja de ser un email crudo.
 */
internal fun buildDetailMembers(bond: AdminBond, report: AdminBondReport?): List<DetailMember> =
    bond.members.map { bondMember ->
        val reportMember = report?.members?.firstOrNull { it.userId == bondMember.id }
        val name = reportMember?.let { member ->
            listOf(member.firstName, member.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
        } ?: bondMember.email?.substringBefore('@')?.ifBlank { null } ?: "Miembro"

        val completed = reportMember?.completed ?: 0
        val assigned = reportMember?.assigned ?: 0
        DetailMember(
            userId = bondMember.id,
            name = name,
            roleLabel = memberRoleLabel(bondMember.role),
            completed = completed,
            assigned = assigned,
            percentage = percentageOf(completed, assigned),
        )
    }

/** Rol legible; lo desconocido se capitaliza en vez de mostrarse crudo. */
internal fun memberRoleLabel(role: String): String = when (role.lowercase()) {
    "parent" -> "Progenitor"
    "father" -> "Padre"
    "mother" -> "Madre"
    "tutor", "guardian" -> "Tutor"
    "" -> "Miembro"
    else -> role.replaceFirstChar { it.uppercase() }
}

/** Iniciales del avatar: primera letra de nombre y apellido. */
internal fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        .ifBlank { "?" }
}

/** `N actividades` del hijo según el período del reporte; null si el reporte no lo conoce. */
internal fun childActivityCount(report: AdminBondReport?, childId: String): Int? =
    report?.children?.firstOrNull { it.id == childId }?.activityCount

/** Edad en años a la fecha de hoy, con singular correcto; fecha inválida o ausente → null. */
internal fun childAgeLabel(dob: String?, today: LocalDate): String? {
    val date = dob?.let { value ->
        runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    } ?: return null

    var years = today.year - date.year
    if (today.monthNumber * 100 + today.dayOfMonth < date.monthNumber * 100 + date.dayOfMonth) {
        years -= 1
    }
    if (years < 0) return null
    return if (years == 1) "1 año" else "$years años"
}
