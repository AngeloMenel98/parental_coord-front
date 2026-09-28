package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Tablas de texto en español y formateadores puros del timeline — cambio
 * `calendar-design-redesign`, tema T4 (paso 0).
 *
 * Este archivo NO importa `androidx.compose.*`: no hay superficie de recomposición,
 * ni estado, ni `@OptIn`. Es Kotlin puro y se puede testear sin Compose.
 *
 * Las declaraciones son `internal` (eran `private` dentro de ActivitiesScreen.kt):
 * un símbolo top-level `private` es invisible entre archivos, así que el paso que
 * mueve una declaración debe volverla visible en el mismo paso en que reescriben
 * sus consumidores (R2-T4-2).
 */

// ── Constantes de copia ───────────────────────────────────────────────

internal val ES_MONTHS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

internal val ES_WEEKDAYS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

internal const val NEXT_MSG = "Próximamente"
internal const val NO_START_LABEL = "Sin fecha de inicio"

/**
 * Ancho máximo de contenido como `Int` de 640, con `.dp` aplicado en el
 * call-site. Propietario del cambio: T4-9 lo borra a favor de
 * `Spacing.maxContentWidth` (`R6-T4-6b`). T4-1 sólo lo traslada; ningún valor
 * cambia.
 */

// ── Modelos del timeline ──────────────────────────────────────────────

internal data class TimelineRow(
    val actividad: Actividad,
    /** Solo scheduledStart válido; deadline nunca se convierte en inicio. */
    val scheduledStart: Instant?,
    val localStart: LocalDateTime?,
    val deadline: Instant?,
)

internal data class AgendaGroup(
    val date: LocalDate?,
    val rows: List<TimelineRow>,
)

internal data class AgendaSlot(
    val hour: Int,
    val minute: Int,
    val rows: List<TimelineRow>,
)

internal data class DayBucket(
    val date: LocalDate,
    val slots: List<AgendaSlot>,
)

// ── Formateadores ─────────────────────────────────────────────────────

internal fun weekdayLabel(date: LocalDate): String = ES_WEEKDAYS[date.dayOfWeek.ordinal]

internal fun formatMonthYear(date: LocalDate): String =
    "${ES_MONTHS[date.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${date.year}"

/**
 * Día con cero a la izquierda — `"04"`, nunca `"4"`. FR-3 (`prd-v4:309`).
 * Línea superior de la columna de fecha (`R1-T3-1`); la anatomía de dos líneas no
 * tiene hueco para el año, y eso es lo que hace satisfacible `R2-T3-2`.
 */
internal fun formatAgendaDay(date: LocalDate): String =
    date.dayOfMonth.toString().padStart(2, '0')

/**
 * Mes abreviado tal como está en [ES_MONTHS]: `sep`, no `sept`.
 * `R2-T3-2` declara esa tabla intocable; la tabla de 12 nombres completos que
 * añade `R1-T1-1` es sólo para nombres largos y NO puede filtrarse aquí.
 */
internal fun formatAgendaMonth(date: LocalDate): String =
    ES_MONTHS[date.monthNumber - 1]

/**
 * Fecha larga. El año se añade **sólo cuando difiere del año en curso**
 * (`R2-T3-2`, PRD `:82`) — una sola regla que gobierna la línea `Límite:`, el
 * titular del día y la etiqueta accesible de la fila.
 *
 * El año en curso sale de [currentDateIn], la MISMA fuente que la pantalla ya usa
 * (`zone = TimeZone.currentSystemDefault()`), nunca de un valor fijo ni de un
 * `LocalDate.now()` fuera de la zona: un desajuste sólo se equivoca cerca de
 * Nocheviejo, que es el caso que nadie prueba. `padStart(2, '0')` sobrevive en
 * [formatAgendaDay]: la regla quita el año, no el cero. FR-3.
 */
internal fun formatAgendaDate(
    date: LocalDate,
    currentYear: Int = currentDateIn(TimeZone.currentSystemDefault()).year,
): String {
    val day = "${formatAgendaDay(date)} ${formatAgendaMonth(date)}"
    return if (date.year == currentYear) day else "$day ${date.year}"
}

internal fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** Hereda la regla del año de [formatAgendaDate], con la zona que ya recibió. */
internal fun formatDeadline(deadline: Instant, zone: TimeZone): String =
    formatAgendaDate(deadline.toLocalDateTime(zone).date, currentDateIn(zone).year)

/**
 * Etiqueta accesible de una fila. Es la única frase que anuncia una actividad:
 * fecha, hora, título, categoría, tipo, estado y límite. Si alguna vez se
 * acorta, el estado debe seguir en el texto — los nodos decorativos que
 * codifican categoría y estado están fuera del árbol de semántica.
 */
internal fun activitySemanticLabel(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
): String {
    val dateTime = row.localStart?.let { localStart ->
        "${formatAgendaDate(localStart.date)} ${formatTime(localStart.hour, localStart.minute)}"
    } ?: NO_START_LABEL
    val category = info?.name?.takeIf { it.isNotBlank() } ?: "desconocida"
    val deadline = row.deadline?.let { "Límite ${formatDeadline(it, zone)}" }
    return listOfNotNull(
        dateTime,
        row.actividad.title,
        "Categoría $category",
        typeLabel(row.actividad.type),
        statusSemanticLabel(row.actividad.status),
        deadline,
    ).joinToString(", ")
}

internal fun typeLabel(type: String): String =
    if (type == "obligation") "Obligación" else "Evento"

internal fun statusSemanticLabel(status: String): String = when (status.uppercase()) {
    "CREATED" -> "Creada"
    "ASSIGNED" -> "Asignada"
    "IN_PROGRESS" -> "En progreso"
    "VERIFY", "PENDING_VERIFICATION" -> "Por verificar"
    "DONE", "COMPLETED" -> "Cumplida"
    "OVERDUE" -> "Vencida"
    "CANCELLED" -> "Cancelada"
    else -> "Estado ${status.ifBlank { "desconocido" }}"
}

internal fun formatDetailDate(raw: String?): String? {
    val instant = parseInstant(raw) ?: return null
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.dayOfMonth} ${ES_MONTHS[ldt.monthNumber - 1]} ${ldt.year}"
}

internal fun criticalityLabel(criticality: String): String = when (criticality) {
    "critical" -> "Urgente"
    "high" -> "Alta"
    "medium" -> "Media"
    "low" -> "Baja"
    else -> criticality
}
