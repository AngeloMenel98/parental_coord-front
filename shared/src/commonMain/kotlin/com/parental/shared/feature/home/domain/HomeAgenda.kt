package com.parental.shared.feature.home.domain

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.ui.formatTime
import com.parental.shared.feature.home.domain.model.ChildProfile
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/** Cuántas filas muestra la tarjeta "Hoy"; el resto se anuncia como conteo. */
internal const val TODAY_AGENDA_LIMIT = 2

/** El único estado que cuenta como "vencida" (el resto del alfabeto no suma acá). */
private const val OVERDUE_STATUS = "overdue"

/** Una fila de la agenda de hoy, ya resuelta para pintar (S2). */
@Serializable
data class TodayActivity(
    val id: String,
    val title: String,
    val status: String,
    /** Hora local de inicio en `HH:mm`, la misma zona con la que se filtró el día. */
    val timeLabel: String,
    val categoryName: String? = null,
    val categoryColorHex: String? = null,
)

/**
 * La agenda de hoy. `null` (en [com.parental.shared.feature.home.domain.model.HomeSummary])
 * significa "no se pudo cargar"; esta clase nunca representa ese caso con una lista vacía.
 */
@Serializable
data class TodayAgenda(
    val activities: List<TodayActivity>,
    /** Total de actividades de hoy, incluidas las que no entran en [activities]. */
    val total: Int,
)

/** Un hijo del vínculo con su carga vencida. */
@Serializable
data class ChildStatus(
    val child: ChildProfile,
    val overdue: Int,
)

/**
 * Filtra la lista del vínculo a las actividades de [today] en [timeZone].
 *
 * Reglas (S2):
 * - Sin lista (falló la carga) → `null`. Es un hueco, no un "no hay nada".
 * - Sólo cuentan las actividades con `scheduledStart` ISO-8601 que caiga en [today]
 *   **en [timeZone]**. Las sin fecha no son "de hoy".
 * - Orden ascendente por instante; se muestran [limit] y el resto se cuenta en `total`.
 */
internal fun selectTodayAgenda(
    activities: List<Actividad>?,
    today: LocalDate,
    timeZone: TimeZone,
    categories: Map<String, CategoryInfo>,
    limit: Int = TODAY_AGENDA_LIMIT,
): TodayAgenda? {
    if (activities == null) return null

    val ofToday = activities
        .mapNotNull { activity ->
            val start = activity.scheduledStart
                ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }
                ?: return@mapNotNull null
            val local = start.toLocalDateTime(timeZone)
            if (local.date != today) return@mapNotNull null
            Triple(start, local, activity)
        }
        .sortedBy { it.first }

    return TodayAgenda(
        activities = ofToday.take(limit).map { (_, local, activity) ->
            val info = categories[activity.categoryId]
            TodayActivity(
                id = activity.id,
                title = activity.title,
                status = activity.status,
                timeLabel = formatTime(local.hour, local.minute),
                categoryName = info?.name,
                categoryColorHex = info?.colorHex,
            )
        },
        total = ofToday.size,
    )
}

/**
 * Cuántas actividades vencidas tiene cada hijo (S5).
 *
 * Sin lista → lista vacía: la sección cae a "nombre sin estado" en vez de afirmar
 * "Al día" sobre un dato que no llegó. Un `0` acá sí es un dato: la lista se cargó y
 * ese hijo no tiene vencidas.
 */
internal fun selectChildStatuses(
    children: List<ChildProfile>,
    activities: List<Actividad>?,
): List<ChildStatus> {
    if (activities == null) return emptyList()

    val overdueByChild = mutableMapOf<String, Int>()
    activities.forEach { activity ->
        if (activity.status != OVERDUE_STATUS) return@forEach
        activity.childrenIds.distinct().forEach { childId ->
            overdueByChild[childId] = (overdueByChild[childId] ?: 0) + 1
        }
    }

    return children.map { child ->
        ChildStatus(child = child, overdue = overdueByChild[child.id] ?: 0)
    }
}
