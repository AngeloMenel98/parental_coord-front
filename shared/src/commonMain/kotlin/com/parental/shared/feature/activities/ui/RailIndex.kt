package com.parental.shared.feature.activities.ui

import kotlinx.datetime.LocalDate

/*
 * Índice de ítems del RIEL del tiempo — T4 de `odd/tasks/activity-rail.md` (S3, S5, S6).
 * **Kotlin puro: sin `androidx.compose.*`, sin estado, sin `@OptIn`.**
 *
 * Este archivo es la ÚNICA fuente de verdad de "cuántos ítems de lista pone una sección y
 * en qué índice cae su encabezado". `RailTimeline.kt` monta la `LazyColumn` con EXACTAMENTE
 * esa forma y el salto a un día (T6) pide el índice aquí, así que el número y la lista no
 * pueden separarse: si una de las dos cambia, la prueba `RailIndexTest` rompe.
 *
 * ── ESTO REEMPLAZA A `agendaHeaderIndex` PARA EL RIEL ─────────────────────────────
 * `agendaHeaderIndex` (`CalendarJump.kt`) cuenta la forma del feed de tarjetas:
 * `[pista, encabezado, tarjeta, tarjeta, encabezado, …]`, con un `AGENDA_HINT_ITEMS` fijo de
 * pista y **una tarjeta por actividad**. El riel tiene otra forma y otras dos diferencias
 * que hacen imposible reutilizarlo:
 *  1. el día es UN ítem de lista (el lienzo), no una tarjeta por actividad — el número de
 *     actividades de un día ya no dice cuántos ítems ocupa, así que `1 + rows.size` cuenta
 *     de más;
 *  2. un día PLEGADO aporta CERO ítems de contenido, y ese cero no existe en el feed de la
 *     agenda: allí los grupos nunca se pliegan (S5 introduce el plegado);
 *  3. la sección "sin fecha de inicio" sí aporta una fila por actividad, así que la regla
 *     "una tarjeta por fila" sobrevive sólo en ella.
 * Reutilizarlo habría exigido darle a `agendaHeaderIndex` un parámetro de forma (¿cuenta
 * pistas? ¿cuenta filas o días?) y volvería a mentir en el feed de la agenda. La agenda se
 * retira en T8, pero mientras conviva con el riel cada uno cuenta su propia forma.
 */

/**
 * Una sección del riel: un día ([date] no nulo, con su [railDay]) o la sección sin fecha
 * de inicio ([date] `null`, con sus [undatedRows]).
 *
 * INVARIANTE: `date == null` ⇔ `railDay == null` y `undatedRows` es la que se pinta; con
 * `date` no nulo `railDay` NUNCA es `null` (aunque el día no tenga bloques: el eje por
 * defecto de `buildRailDay` sigue haciendo falta para la canaleta de horas).
 *
 * `isExpanded` sólo tiene sentido en una sección CON fecha: la sección sin fecha no se
 * pliega (no tiene encabezado de día que la pliegue) y su valor se ignora.
 */
internal data class RailSection(
    /** Día de la sección, o `null` en la sección "sin fecha de inicio". */
    val date: LocalDate?,
    /** Día de riel ya derivado por `buildRailDay`, o `null` en la sección sin fecha. */
    val railDay: RailDay?,
    /** Filas sin fecha de inicio; sólo en la sección sin fecha. */
    val undatedRows: List<TimelineRow> = emptyList(),
    /** Estado de plegado del día; la sección sin fecha lo ignora. */
    val isExpanded: Boolean = true,
)

/**
 * Ítems de CONTENIDO de [section] — sin contar su encabezado, que siempre existe.
 *
 * · Día expandido → 1 (el lienzo del día), aunque el día no tenga ni un bloque: el lienzo
 *   vacío es justo lo que dibuja el "día libre", y un 0 ahí desalinearía el índice del día
 *   siguiente.
 * · Día plegado → 0.
 * · Sección sin fecha → una fila por actividad, y no se pliega nunca.
 */
internal fun railContentCount(section: RailSection): Int = when {
    section.date == null -> section.undatedRows.size
    section.isExpanded -> 1
    else -> 0
}

/**
 * Índice del ENCABEZADO de [date] dentro de la lista del riel, o `null` si ese día no es
 * una de las secciones.
 *
 * La lista del riel tiene esta forma:
 * `[encabezado, contenido…, encabezado, contenido…, encabezado sin fecha, fila, fila…]`.
 * El índice se cuenta sobre ESA forma con [railContentCount], así que vale también para un
 * día PLEGADO (su encabezado sigue existiendo; lo que no existe es su contenido) y no hace
 * falta desplegar nada antes de saltar.
 *
 * La sección sin fecha no es destino posible: su [RailSection.date] es `null` y [date] no.
 */
internal fun railItemIndexOf(sections: List<RailSection>, date: LocalDate): Int? {
    var index = 0
    sections.forEach { section ->
        if (section.date == date) return index
        // El encabezado de la sección, más su contenido.
        index += 1 + railContentCount(section)
    }
    return null
}

/** Por qué el riel se posó donde se posó — T10 (S15). */
internal enum class RailAnchorKind {
    /** Hay una actividad en curso AHORA: el riel abre en la línea de «ahora». */
    InProgress,

    /** Nada en curso: el riel abre en el primer bloque del primer día con contenido. */
    FirstBlock,
}

/**
 * Destino del anclaje inicial del riel — T10 (S15).
 *
 * Es la DECISIÓN, no la geometría: la pantalla la traduce a índice de lista con
 * [railItemIndexOf] (la única fuente de la forma) y a píxeles con `railOffsetDp`. Así la regla se
 * prueba en `commonTest`, sin Compose, sin reloj y sin layout.
 *
 * @param date día donde se posa el riel.
 * @param minuteOfDay minuto de día de la línea de destino dentro del eje de [date]: la hora
 *   actual si hay algo en curso, o el inicio del primer bloque.
 */
internal data class RailAnchor(
    val date: LocalDate,
    val minuteOfDay: Int,
    val kind: RailAnchorKind,
)

/**
 * Dónde abre el riel en su primera composición — S15: «anclar solo si hay algo en curso».
 *
 * REGLAS, en orden:
 *  1. Si la sección de [today] tiene un bloque `start <= nowMinute < end` (intervalo
 *     semiabierto: a la hora exacta de fin el bloque ya terminó), el anclaje es la línea de
 *     «ahora» de HOY. El `now` sólo se compara contra los bloques de [today]: un bloque de ayer
 *     a la misma hora no es «algo en curso».
 *  2. Si no hay nada en curso, el anclaje es el bloque MÁS TEMPRANO de la primera sección CON
 *     contenido. Un día sin bloques no es destino: posarse ahí deja al usuario mirando una
 *     franja vacía, que es justo lo que S15 evita.
 *  3. Si ninguna sección tiene bloques —o la única sección es la de «sin fecha de inicio», que
 *     no tiene eje horario— no hay anclaje y la lista abre donde estaba (el tope).
 *
 * No lee el reloj: `nowMinute` y `today` entran por parámetro (el llamador los saca del
 * `LaunchedEffect` de la pantalla) y un día plegado conserva sus bloques en la sección, así que
 * el anclaje no depende de que la vista esté desplegada.
 */
internal fun railInitialAnchor(
    sections: List<RailSection>,
    today: LocalDate,
    nowMinute: Int,
): RailAnchor? {
    val todayBlocks = sections.firstOrNull { section -> section.date == today }?.railDay?.blocks
    val inProgress = todayBlocks?.firstOrNull { block ->
        block.startMinute <= nowMinute && nowMinute < block.endMinute
    }
    if (inProgress != null) {
        return RailAnchor(date = today, minuteOfDay = nowMinute, kind = RailAnchorKind.InProgress)
    }
    val firstWithContent = sections.firstOrNull { section ->
        !section.railDay?.blocks.isNullOrEmpty()
    }
    val date = firstWithContent?.date ?: return null
    // `buildRailDay` ya ordena por inicio; `minByOrNull` lo deja explícito y hace que la regla no
    // dependa de ese orden (es el MÁS TEMPRANO, no "el primero de la lista").
    val firstBlock = firstWithContent.railDay?.blocks?.minByOrNull { block -> block.startMinute }
        ?: return null
    return RailAnchor(
        date = date,
        minuteOfDay = firstBlock.startMinute,
        kind = RailAnchorKind.FirstBlock,
    )
}

/** Largo de la abreviatura de día de semana del riel — `"MIÉ"`, tres letras. */
private const val RAIL_WEEKDAY_LENGTH = 3

/**
 * Rótulo compacto del encabezado de día: `"MIÉ 13 MAY"` — el `dname` del arteboard
 * (`design/activity-calendar-ui/03-riel-del-tiempo.html:427`).
 *
 * **No hay tabla nueva.** El día de semana sale de [ES_WEEKDAYS_SPOKEN] recortado a
 * [RAIL_WEEKDAY_LENGTH] (`ES_WEEKDAYS_CAPS` sólo tiene dos letras y se queda en `"MI"`), y
 * el mes de [formatAgendaMonth] — las tablas de `TimelineFormatters.kt`, que están en su
 * techo de líneas y por eso este rótulo vive aquí y no allí. El `MAYÚSCULAS` final es
 * presentación del riel: las tablas conservan su caja (`"may"`, `"Miércoles"`).
 *
 * El día NO lleva cero a la izquierda (`"5 MAY"`, no `"05 MAY"`): se usa `dayOfMonth` y no
 * [formatAgendaDay], cuyo `padStart` es la regla de la columna de lista (`FR-3`) y no la de
 * un titular — la misma distinción que documenta [formatLongDate].
 */
internal fun railDayLabel(date: LocalDate): String {
    val weekday = ES_WEEKDAYS_SPOKEN[date.dayOfWeek.ordinal]
        .take(RAIL_WEEKDAY_LENGTH)
        .uppercase()
    return "$weekday ${date.dayOfMonth} ${formatAgendaMonth(date).uppercase()}"
}
