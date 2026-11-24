package com.parental.shared.feature.admin.ui

import com.parental.shared.feature.activities.ui.ES_MONTHS
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.admin.domain.model.AdminBondActivity
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminReportMember
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/*
 * F7 · S20/S21 — contadores y filtros del reporte de vínculo (artboard `admin-ui/04`).
 *
 * Todo lo de acá es PURO: sin Compose, sin estado, sin red. Es la razón por la que el
 * número que muestra la tarjeta y el que muestra el detalle no pueden divergir.
 *
 * ⚠ LA REGLA CENTRAL: los contadores se componen SÓLO desde `byStatus`.
 *
 * El backend devuelve dos cosas con el mismo nombre y distinto significado:
 *   · `activities.overdue` — predicado por deadline sobre estados NO terminales;
 *   · `byStatus["overdue"]` — actividades cuyo ESTADO es `overdue`.
 * Además, las transiciones T14/T16 del backend limpian `deadline` al entrar en `overdue`,
 * así que una actividad vencida por estado casi nunca cae en el predicado. Leer los dos
 * producía la contradicción que reportó el usuario: "vencidas 3" junto a "overdue 4", y un
 * 20% que dividía por un total que ningún chip mostraba. Acá `overdue` del backend NO se
 * lee; el PDF sigue usándolo del lado del servidor.
 *
 * El total también sale de `byStatus`, no de `activities.total`: así la suma de la leyenda
 * es exactamente el denominador del porcentaje. Todo estado que el backend agregue en el
 * futuro cae en "Otros estados" en vez de desaparecer del total.
 */

internal const val FILTER_ALL = "all"

internal const val DONE_KEY = "done"
internal const val OPEN_KEY = "open"
internal const val OVERDUE_KEY = "overdue"
internal const val CLOSED_KEY = "closed"
internal const val OTHER_KEY = "others"

/** Estados del backend (`ActivityStatus`) agrupados por lo que significan para el admin. */
internal val COMPLETED_STATUSES = setOf("done", "completed")
internal val OVERDUE_STATUSES = setOf("overdue")
internal val OPEN_STATUSES =
    setOf("created", "assigned", "assisting", "in_progress", "verify", "pending_verification")
internal val CLOSED_STATUSES = setOf("cancelled", "not_assisting")

/** Un tramo de la barra y su fila de leyenda: `Cumplidas 31 · 73,81%`. */
internal data class ActivityBucket(
    val key: String,
    val label: String,
    val count: Int,
    val percentage: Double,
)

internal data class ActivityCounts(
    val total: Int,
    val completed: Int,
    val notCompleted: Int,
    val completionRate: Double,
    val notCompletedRate: Double,
    val buckets: List<ActivityBucket>,
)

private data class BucketDefinition(
    val key: String,
    val label: String,
    val statuses: Set<String>,
)

private val BUCKET_DEFINITIONS = listOf(
    BucketDefinition(DONE_KEY, "Cumplidas", COMPLETED_STATUSES),
    BucketDefinition(OPEN_KEY, "En curso", OPEN_STATUSES),
    BucketDefinition(OVERDUE_KEY, "Vencidas", OVERDUE_STATUSES),
    BucketDefinition(CLOSED_KEY, "Canceladas / no asistió", CLOSED_STATUSES),
)

internal fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

/** S1 · el CTA del detalle copia el artboard 04: «Ver las N actividades», con la cuenta. */
internal fun adminActivityDetailCta(total: Int): String = when {
    total <= 0 -> "Ver actividades"
    total == 1 -> "Ver la actividad"
    else -> "Ver las $total actividades"
}

/**
 * S1 · info de categoría de una actividad del reporte, con la MISMA vía de resolución
 * que la agenda: `categoryTagTriple` resuelve por nombre cuando no hay colorHex, así la
 * fila del detalle pinta el riel con los mismos tokens que la fila real de la agenda.
 */
internal fun adminCategoryInfo(categoryName: String?): CategoryInfo? =
    categoryName?.takeIf { it.isNotBlank() }?.let { CategoryInfo(name = it, colorHex = null, emoji = null) }

/** Porcentaje con el total como denominador, redondeado a dos decimales. */
internal fun percentageOf(value: Int, total: Int): Double =
    if (total <= 0) 0.0 else roundTo2(value * 100.0 / total)

internal fun buildActivityCounts(report: AdminBondReport): ActivityCounts =
    buildActivityCounts(report.activities.byStatus, report.activities.total)

internal fun buildActivityCounts(byStatus: Map<String, Int>, fallbackTotal: Int = 0): ActivityCounts {
    val counts = mutableMapOf<String, Int>()
    byStatus.forEach { (status, value) ->
        if (value > 0) {
            val key = status.lowercase()
            counts[key] = (counts[key] ?: 0) + value
        }
    }

    val counted = counts.values.sum()
    val total = if (counted > 0) counted else fallbackTotal
    val known = BUCKET_DEFINITIONS.flatMap { it.statuses }.toSet()
    val otherCount = counts.filterKeys { it !in known }.values.sum()

    val buckets = buildList {
        BUCKET_DEFINITIONS.forEach { definition ->
            val count = definition.statuses.sumOf { counts[it] ?: 0 }
            if (count > 0) {
                add(ActivityBucket(definition.key, definition.label, count, percentageOf(count, total)))
            }
        }
        if (otherCount > 0) {
            add(ActivityBucket(OTHER_KEY, "Otros estados", otherCount, percentageOf(otherCount, total)))
        }
    }

    val completed = buckets.firstOrNull { it.key == DONE_KEY }?.count ?: 0
    return ActivityCounts(
        total = total,
        completed = completed,
        notCompleted = total - completed,
        completionRate = percentageOf(completed, total),
        notCompletedRate = percentageOf(total - completed, total),
        buckets = buckets,
    )
}

/** Fila del desglose por progenitor: `Mario Paz 17 de 22 · 77,27%`. */
internal data class MemberCompletion(
    val userId: String,
    val name: String,
    val completed: Int,
    val assigned: Int,
    val percentage: Double,
)

internal fun buildMemberCompletions(members: List<AdminReportMember>): List<MemberCompletion> =
    members.map { member ->
        val name = listOf(member.firstName, member.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Progenitor" }
        MemberCompletion(
            userId = member.userId,
            name = name,
            completed = member.completed,
            assigned = member.assigned,
            percentage = percentageOf(member.completed, member.assigned),
        )
    }

internal fun bucketKeyOf(status: String): String {
    val key = status.lowercase()
    return when {
        key in COMPLETED_STATUSES -> DONE_KEY
        key in OVERDUE_STATUSES -> OVERDUE_KEY
        key in OPEN_STATUSES -> OPEN_KEY
        key in CLOSED_STATUSES -> CLOSED_KEY
        else -> OTHER_KEY
    }
}

internal fun isCompleted(status: String): Boolean = status.lowercase() in COMPLETED_STATUSES

internal fun filterReportActivities(
    activities: List<AdminBondActivity>,
    bucketKey: String = FILTER_ALL,
    assigneeId: String? = null,
): List<AdminBondActivity> = activities.filter { activity ->
    val matchesBucket = bucketKey == FILTER_ALL || bucketKeyOf(activity.status) == bucketKey
    val matchesAssignee = assigneeId == null || activity.assignedTo == assigneeId
    matchesBucket && matchesAssignee
}

/** Fechas de la fila. `zone` es parámetro para que los tests no dependan del huso del host. */
internal fun activityLocalStart(iso: String?, zone: TimeZone): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).toLocalDateTime(zone) }.getOrNull()
}

internal fun activityDayLabel(
    iso: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = activityLocalStart(iso, zone)?.dayOfMonth?.toString()?.padStart(2, '0') ?: "—"

internal fun activityMonthLabel(
    iso: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = activityLocalStart(iso, zone) ?: return "—"
    return ES_MONTHS[date.monthNumber - 1].uppercase()
}

internal fun activityTimeLabel(
    iso: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = activityLocalStart(iso, zone) ?: return ""
    val hour = date.hour.toString().padStart(2, '0')
    val minute = date.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

internal fun activityShortDate(
    iso: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = activityLocalStart(iso, zone) ?: return "—"
    val day = date.dayOfMonth.toString().padStart(2, '0')
    val month = date.monthNumber.toString().padStart(2, '0')
    return "$day/$month"
}

/** "venció 18/06" cuando ya pasó, "vence 02/07" cuando todavía no. */
internal fun activityDeadlineLabel(
    activity: AdminBondActivity,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val deadline = activity.deadline ?: return null
    val verb = if (bucketKeyOf(activity.status) == OVERDUE_KEY) "venció" else "vence"
    return "$verb ${activityShortDate(deadline, zone)}"
}
