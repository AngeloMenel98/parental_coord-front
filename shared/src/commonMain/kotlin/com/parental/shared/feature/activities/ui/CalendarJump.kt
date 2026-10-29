package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * Derivación pura de la hoja de calendario y de los rótulos del feed — T1 de
 * `odd/tasks/activities-cards-ui.md` (S3, S4, S5).
 *
 * ⚠ ¿POR QUÉ ESTE ARCHIVO Y NO `TimelineFormatters.kt`? Porque ese archivo está en su
 * TECHO de 170 líneas (lo dice su propia cabecera) y sus tablas (`ES_WEEKDAYS_SPOKEN`,
 * `ES_MONTHS_FULL`) ya están ocupadas por el cambio anterior. Meter aquí seis funciones
 * más lo habría dejado en ~250 y devuelto al monolito de archivos que ese cambio sacó
 * adelante. Este archivo es `internal` y sus tablas se REUSAN: no se duplica ni un mes.
 *
 * Kotlin puro: sin `androidx.compose.*`, sin estado, sin `@OptIn`. La UI de T2–T5
 * consume estas funciones; nadie las llama desde la UI con estado mutable.
 *
 * LA GRILLA ES DE LUNES A DOMINGO porque la agenda ya lo es (`mondayStart` en
 * `TimelineDerivation.kt`): el lunes es el primer día de la semana que la app usa, y una
 * grilla que empezara en domingo obligaría a traducir mentalmente el encabezado.
 */

/** Máximo de puntos de categoría por día en la hoja de calendario (arteboard: "máximo 3"). */
internal const val MAX_DAY_DOTS = 3

/**
 * Ítems que preceden al primer encabezado del feed: la pista "Deslizá una tarjeta…".
 *
 * Vive aquí —y no en `AgendaList.kt`— porque el índice del encabezado se cuenta en las dos
 * capas, y una constante en una sola es la única forma de que no se separen.
 */
internal const val AGENDA_HINT_ITEMS = 1

/**
 * Celdas del mes en grilla de lunes a domingo.
 *
 * Devuelve fechas REALES —incluidas las del mes anterior y del siguiente que completan
 * la primera y la última semana— porque la hoja necesita saber si una celda pertenece
 * al mes que se está mirando (para atenuarla) y no para descartarla.
 *
 * El número de celdas es múltiplo de 7 y cubre el mes entero: 35 (5 semanas) o 42 (6
 * semanas). Mayo 2026 arranca en viernes ⇒ 35; agosto 2026 arranca en sábado ⇒ 42.
 */
internal fun buildMonthGrid(year: Int, monthNumber: Int): List<LocalDate> {
    val firstOfMonth = LocalDate(year, monthNumber, 1)
    // El último día del mes es el día anterior al primero del mes siguiente.
    // `kotlinx-datetime` 0.6.1 no expone `daysInMonth`, y sumar un mes es su forma
    // canónica de decirlo (siempre válido porque arrancamos el día 1).
    val daysInMonth = firstOfMonth
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .dayOfMonth
    // `dayOfWeek.ordinal` es 0 para el lunes: los mismos offsets que usa `mondayStart`.
    val leadingDays = firstOfMonth.dayOfWeek.ordinal
    val cellCount = ((leadingDays + daysInMonth + 6) / 7) * 7
    val gridStart = mondayStart(firstOfMonth)
    return (0 until cellCount).map { gridStart.plus(it, DateTimeUnit.DAY) }
}

/**
 * Puntos de categoría por día, para la hoja de calendario.
 *
 * Un punto POR CATEGORÍA (no por actividad): dos actividades de Salud pintan un solo
 * punto. El orden es el de la hora de inicio más temprana de cada categoría, y el tope
 * es [MAX_DAY_DOTS] — más puntos no caben en la celda y el arteboard lo dice.
 *
 * ⚠ Una categoría que el backend no resolvió entra como `null` (no se inventa otra
 * categoría): la UI la pinta en `outline`, que es el fallback de `categoryTagTriple`.
 *
 * Las filas SIN fecha de inicio no cuentan: no hay día al que atribuirlas.
 */
internal fun buildDayDots(
    rows: List<TimelineRow>,
    resolve: (String) -> CategoryInfo?,
): Map<LocalDate, List<CategoryInfo?>> {
    val byDate = rows
        .filter { it.localStart != null }
        .groupBy { it.localStart!!.date }
    return byDate.toSortedMap().mapValues { (_, dayRows) ->
        dayRows
            .sortedWith(::compareTimedRows)
            .map { it.actividad.categoryId }
            .distinct()
            .take(MAX_DAY_DOTS)
            .map { resolve(it) }
    }
}

/**
 * Destino de un salto de día: el grupo de ese día exacto, o —si el día no tiene
 * actividades— el grupo siguiente más cercano; y si no hay grupo siguiente, el grupo
 * anterior más reciente.
 *
 * El grupo SIN fecha nunca es destino: saltaría a un sitio que no está en el mes que
 * el usuario miró en la hoja.
 */
internal fun jumpTargetDate(date: LocalDate, groups: List<AgendaGroup>): LocalDate? {
    val dated = groups.mapNotNull { it.date }
    if (dated.isEmpty()) return null
    if (dated.contains(date)) return date
    return dated.filter { it > date }.minOrNull()
        ?: dated.filter { it < date }.maxOrNull()
}

/**
 * Índice del encabezado de un día dentro de la lista del feed, o `null` si ese día no
 * tiene grupo.
 *
 * El feed de la agenda es una `LazyColumn` con esta forma:
 * `[pista, encabezado, tarjetas…, encabezado, tarjetas…]`. El índice se cuenta sobre ESA
 * forma, y [AGENDA_HINT_ITEMS] (la pista) está aquí para que el número y la lista no
 * puedan separarse: la prueba de `CalendarJumpTest` fija el índice ABSOLUTO, así que
 * quitar la pista sin actualizar esta cuenta rompe la prueba, no el scroll.
 */
internal fun agendaHeaderIndex(groups: List<AgendaGroup>, date: LocalDate): Int? {
    var index = AGENDA_HINT_ITEMS
    groups.forEach { group ->
        if (group.date == date) return index
        index += 1 + group.rows.size
    }
    return null
}

/** Contadores de las pestañas de filtro, derivados de la lista YA cargada (S5). */
internal fun tabCounts(actividades: List<Actividad>): Map<ActivityTab, Int> = mapOf(
    ActivityTab.TODOS to actividades.size,
    ActivityTab.EVENTOS to actividades.count { it.type == "event" },
    ActivityTab.OBLIGACIONES to actividades.count { it.type == "obligation" },
)

/** "lunes 12 de mayo" — día de la semana y mes en minúsculas, sin cero a la izquierda. */
internal fun formatDayTitle(date: LocalDate): String {
    val weekday = ES_WEEKDAYS_SPOKEN[date.dayOfWeek.ordinal].lowercase()
    val month = ES_MONTHS_FULL[date.monthNumber - 1].lowercase()
    return "$weekday ${date.dayOfMonth} de $month"
}

/** Título del grupo del feed: el día, y " · hoy" sólo cuando es el día actual (S4). */
internal fun formatGroupTitle(date: LocalDate, today: LocalDate): String =
    if (date == today) "${formatDayTitle(date)} · hoy" else formatDayTitle(date)

/**
 * Rótulo accesible de una celda del calendario.
 *
 * Es la ÚNICA frase que anuncia la celda (los puntos son decorativos): fecha, si es
 * hoy, cuántas actividades hay y de qué categoría. El plural usa dos puntos y el
 * singular una coma, tal como los dibuja el arteboard.
 */
internal fun dayCellSemanticLabel(
    date: LocalDate,
    today: LocalDate,
    categories: List<CategoryInfo?>,
): String {
    val marker = if (date == today) ", hoy" else ""
    val names = categories.map { category ->
        category?.name?.takeIf { it.isNotBlank() } ?: "desconocida"
    }
    val body = when {
        names.isEmpty() -> "sin actividades"
        names.size == 1 -> "1 actividad, ${names.first()}"
        else -> "${names.size} actividades: ${names.joinToString(", ")}"
    }
    return "${formatDayTitle(date)}$marker, $body"
}
