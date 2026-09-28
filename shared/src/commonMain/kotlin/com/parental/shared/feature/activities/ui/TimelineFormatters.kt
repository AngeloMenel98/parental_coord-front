package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Tablas de texto en español y formateadores puros del timeline — `calendar-design-redesign`.
 *
 * NO importa `androidx.compose.*`: sin recomposición, sin estado, sin `@OptIn`.
 * Las declaraciones son `internal` (eran `private` en el monolito): un símbolo
 * top-level `private` es invisible entre archivos (R2-T4-2).
 *
 * ⚠ TECHO 170, SIN HOLGURA. Recibe adiciones de varios temas (T1-1/T1-2
 * declararon [ES_MONTHS_FULL]/[ES_WEEKDAYS_SPOKEN]; T2-2 las consume y añade
 * [formatLongDate]). Cuando hay que ganar líneas se COMPRIMEN comentarios, nunca
 * se suprime el porqué de una decisión.
 */

// ── Constantes de copia ───────────────────────────────────────────────
internal val ES_MONTHS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

/** Los 12 nombres COMPLETOS, para fechas largas. **No** sustituyen a [ES_MONTHS]
 *  ni se derivan de ella: `prd-v4:84` adopta las abreviaturas CLDR del código y
 *  las dos listas no son intercambiables. */
internal val ES_MONTHS_FULL = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

internal val ES_WEEKDAYS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Los 7 nombres completos: titular del día y etiqueta hablado. `R2-T1-2`. */
internal val ES_WEEKDAYS_SPOKEN = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

internal const val NEXT_MSG = "Próximamente"
internal const val NO_START_LABEL = "Sin fecha de inicio"

// ── Modelos del timeline ──────────────────────────────────────────────
internal data class TimelineRow(
    val actividad: Actividad,
    /** Solo scheduledStart válido; deadline nunca se convierte en inicio. */
    val scheduledStart: Instant?,
    val localStart: LocalDateTime?,
    val deadline: Instant?,
)

internal data class AgendaGroup(val date: LocalDate?, val rows: List<TimelineRow>)

internal data class AgendaSlot(val hour: Int, val minute: Int, val rows: List<TimelineRow>)

internal data class DayBucket(val date: LocalDate, val slots: List<AgendaSlot>)

// ── Formateadores ─────────────────────────────────────────────────────
internal fun weekdayLabel(date: LocalDate): String = ES_WEEKDAYS[date.dayOfWeek.ordinal]

internal fun formatMonthYear(date: LocalDate): String =
    "${ES_MONTHS[date.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${date.year}"

/** Día con cero a la izquierda — `"04"`, nunca `"4"`. FR-3 (`prd-v4:309`). Es la
 *  línea superior de la columna de fecha (`R1-T3-1`): su anatomía de dos líneas no
 *  tiene hueco para el año, y eso es lo que hace satisfacible `R2-T3-2`. */
internal fun formatAgendaDay(date: LocalDate): String =
    date.dayOfMonth.toString().padStart(2, '0')

/** Mes abreviado tal cual en [ES_MONTHS]: `sep`, no `sept`. `R2-T3-2` declara esa
 *  tabla intocable; [ES_MONTHS_FULL] es sólo para nombres largos y NO se filtra aquí. */
internal fun formatAgendaMonth(date: LocalDate): String =
    ES_MONTHS[date.monthNumber - 1]

/** Fecha larga. El año se añade **sólo cuando difiere del año en curso** (`R2-T3-2`,
 *  PRD `:82`) — una sola regla que gobierna la línea `Límite:`, el titular del día y
 *  la etiqueta accesible de la fila. El año en curso sale de [currentDateIn], la MISMA
 *  fuente que la pantalla ya usa, nunca de un valor fijo ni de un `LocalDate.now()`
 *  fuera de la zona: un desajuste sólo se equivoca cerca de Nocheviejo, el caso que
 *  nadie prueba. El `padStart` de [formatAgendaDay] sobrevive: la regla quita el año,
 *  no el cero. FR-3. */
internal fun formatAgendaDate(
    date: LocalDate,
    currentYear: Int = currentDateIn(TimeZone.currentSystemDefault()).year,
): String {
    val day = "${formatAgendaDay(date)} ${formatAgendaMonth(date)}"
    return if (date.year == currentYear) day else "$day ${date.year}"
}

/** Fecha larga del titular del día — `R2-T2-2`: `"Miércoles, 26 De Agosto"`. Semana
 *  completa, `", "`, día, `" De "` y mes COMPLETO, con el `De` capitalizado tal como lo
 *  escriben `prd-v4:112`/`:118`. El día **no** lleva cero a la izquierda: el `padStart`
 *  de [formatAgendaDay] es regla de la columna de lista (`FR-3`), y un titular dice
 *  "4 De Agosto", no "04 De Agosto". El año se añade **sólo cuando difiere del año en
 *  curso** — la MISMA regla y la MISMA fuente que [formatAgendaDate], no una segunda
 *  política. */
internal fun formatLongDate(
    date: LocalDate,
    currentYear: Int = currentDateIn(TimeZone.currentSystemDefault()).year,
): String {
    val weekday = ES_WEEKDAYS_SPOKEN[date.dayOfWeek.ordinal]
    val head = "$weekday, ${date.dayOfMonth} De ${ES_MONTHS_FULL[date.monthNumber - 1]}"
    return if (date.year == currentYear) head else "$head ${date.year}"
}

internal fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** Hereda la regla del año de [formatAgendaDate], con la zona que ya recibió. */
internal fun formatDeadline(deadline: Instant, zone: TimeZone): String =
    formatAgendaDate(deadline.toLocalDateTime(zone).date, currentDateIn(zone).year)

/** Etiqueta accesible de una fila — la ÚNICA frase que anuncia una actividad: fecha,
 *  hora, título, categoría, tipo, estado y límite. Si alguna vez se acorta, el estado
 *  debe seguir en el texto: los nodos decorativos que codifican categoría y estado
 *  están fuera del árbol de semántica. */
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
