package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.resolveChildNames
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Tablas de texto en español y formateadores puros del timeline — `calendar-design-redesign`.
 *
 * NO importa `androidx.compose.*`: sin recomposición, sin estado, sin `@OptIn`. Las
 * declaraciones son `internal` (eran `private`): un `private` top-level no cruza archivo (R2-T4-2).
 *
 * ⚠ TECHO 170, SIN HOLGURA. T1-1/T1-2 y T2-2 ya consumieron sus tablas. Cuando
 * hay que ganar líneas se COMPRIMEN comentarios, nunca el porqué de una decisión.
 */

// ── Constantes de copia ───────────────────────────────────────────────
internal val ES_MONTHS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

/** Los 12 nombres COMPLETOS. **No** sustituyen a [ES_MONTHS] ni se derivan: `prd-v4:84`
 *  adopta las abreviaturas CLDR y las dos listas no son intercambiables. */
internal val ES_MONTHS_FULL = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

/** Visual de la celda: DOS letras; el `Á` se conserva porque `labelSmall` no acentúa
 *  solo. `ES_WEEKDAYS` (3 letras) RETIRADA —código muerto—; habla [ES_WEEKDAYS_SPOKEN]. `R2-T1-2`. */
internal val ES_WEEKDAYS_CAPS = listOf("LU", "MA", "MI", "JU", "VI", "SÁ", "DO")

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
/** VISUAL: dos letras. La hablada la elige el call-site (`ES_WEEKDAYS_SPOKEN[…]` en la
 *  etiqueta combinada de F6); un intercambio ciego haría decir a TalkBack "MI, 26 ago
 *  2026, 2 actividades". `R2-T1-2`. */
internal fun weekdayLabel(date: LocalDate): String = ES_WEEKDAYS_CAPS[date.dayOfWeek.ordinal]

/** Mes COMPLETO + `" De "` + año — `R1-T1-1`: `"Agosto De 2026"`. El `De` mayúscula
 *  es la convención de `prd-v4:112` que ya usa [formatLongDate]; [ES_MONTHS_FULL] entra
 *  capitalizado y no se re-capitaliza. */
internal fun formatMonthYear(date: LocalDate): String =
    "${ES_MONTHS_FULL[date.monthNumber - 1]} De ${date.year}"

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
    memberNames: Map<String, String> = emptyMap(),
    children: List<VinculoChild> = emptyList(),
): String {
    val dateTime = row.localStart?.let { localStart ->
        "${formatAgendaDate(localStart.date)} ${formatTime(localStart.hour, localStart.minute)}"
    } ?: NO_START_LABEL
    val category = info?.name?.takeIf { it.isNotBlank() } ?: "desconocida"
    val assignee = row.actividad.assignedTo
        ?.let { memberNames[it]?.takeIf { name -> name.isNotBlank() } }
        ?.let { name -> "Asignada a: $name" }
    val childNames = resolveChildNames(children, row.actividad.childrenIds)
    val childrenLine = childNames.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?.let { names -> "Para: $names" }
    val deadline = row.deadline?.let { "Límite ${formatDeadline(it, zone)}" }
    return listOfNotNull(
        dateTime,
        row.actividad.title,
        "Categoría $category",
        typeLabel(row.actividad.type),
        statusSemanticLabel(row.actividad.status),
        assignee,
        childrenLine,
        deadline,
    ).joinToString(", ")
}

internal fun typeLabel(type: String): String =
    if (type == "obligation") "Obligación" else "Evento"

internal fun statusSemanticLabel(status: String): String = when (status.uppercase()) {
    "CREATED" -> "Creada"
    "ASSIGNED" -> "Asignada"
    "ASSISTING" -> "Asistiendo"
    "IN_PROGRESS" -> "En progreso"
    "VERIFY", "PENDING_VERIFICATION" -> "Por verificar"
    "DONE", "COMPLETED" -> "Cumplida"
    "OVERDUE" -> "Vencida"
    // A5 · Se retira el brazo `"OBJECTION"`: el enum ya no lo tiene y el backend
    // lo tira en `DropActivityStatusLegacyValues`. Dejarlo mapeado no rompe nada
    // (es un `when`), pero es un estado que ya no puede llegar desde la API, así que
    // su etiqueta es un segundo lugar donde editar si mañana se re-normaliza el copy.
    "NOT_ASSISTING" -> "No asiste"
    "CANCELLED" -> "Cancelada"
    else -> "Estado ${status.ifBlank { "desconocido" }}"
}

/**
 * Fecha del detalle de actividad: `"5 ago 2026"`.
 *
 * 🔴 EL AÑO SALE SIEMPRE (a diferencia de [formatLongDate], que lo omite en el año en
 *   curso): aquí la fecha es un dato de un hecho pasado, y un "5 de agosto" sin año
 *   no dice en qué año pasó. Y el mes sale COMPLETO y en minúsculas: la abreviatura
 *   `ES_MONTHS` (`ago`, `dic`) nunca se corta — un mes truncado a dos letras ("05 dé")
 *   es un dato ilegible, no una fecha.
 *
 * La zona es un parámetro con default de producción para que el test sea
 * determinista sin tocar el reloj del sistema.
 */
internal fun formatDetailDate(
    raw: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val instant = parseInstant(raw) ?: return null
    val ldt = instant.toLocalDateTime(zone)
    return "${ldt.dayOfMonth} ${ES_MONTHS[ldt.monthNumber - 1]} ${ldt.year}"
}

/**
 * "Cuándo" del detalle — `"5 ago 2026 · 16:30"`, o el RANGO cuando hay fin programado
 * ([S9] · D-4): `"5 ago 2026 · 16:30 – 18:00"`, y con el día repetido si el rango cruza
 * la medianoche (`"5 ago 2026 · 22:00 – 6 ago 2026 · 01:00"`), porque dos horas sin fecha
 * en un rango que cambia de día dicen menos que nada.
 *
 * La fecha sola NO alcanza para D-4: una actividad es un hecho con hora. El separador
 * `·` y el guion del rango son los del PRD.
 */
internal fun formatDetailDateTime(
    startRaw: String?,
    endRaw: String? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val start = parseInstant(startRaw)?.toLocalDateTime(zone) ?: return null
    val head = "${start.dayOfMonth} ${ES_MONTHS[start.monthNumber - 1]} ${start.year} · " +
        formatTime(start.hour, start.minute)
    // Un fin ilegible (o ausente) no invalida el inicio: se muestra la hora de comienzo.
    val end = parseInstant(endRaw)?.toLocalDateTime(zone) ?: return head
    val tail = if (end.date == start.date) {
        formatTime(end.hour, end.minute)
    } else {
        "${end.dayOfMonth} ${ES_MONTHS[end.monthNumber - 1]} ${end.year} · " +
            formatTime(end.hour, end.minute)
    }
    return "$head – $tail"
}

internal fun criticalityLabel(criticality: String): String = when (criticality) {
    "critical" -> "Alta"
    "high" -> "Alta"
    "medium" -> "Media"
    "low" -> "Baja"
    else -> criticality
}
