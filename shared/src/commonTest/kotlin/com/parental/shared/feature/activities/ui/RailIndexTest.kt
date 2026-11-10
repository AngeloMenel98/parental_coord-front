package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T4 de `odd/tasks/activity-rail.md` — índice de ítems del riel (S3, S5, S6).
 *
 * TEST-FIRST: este archivo se escribió ANTES que `RailIndex.kt`; la primera corrida dio
 * ROJO por compilación (las funciones todavía no existían) y ése es el RED registrado.
 *
 * Todo es Kotlin puro: ni Compose ni estado. La forma de la lista que estas pruebas fijan
 * es la MISMA que monta `RailTimeline.kt`: por cada sección un encabezado y, debajo, sus
 * ítems de contenido (el lienzo del día o una fila por actividad sin fecha).
 */
class RailIndexTest {

    // ── Utilidades de fixture ────────────────────────────────────────────

    private fun row(id: String, date: LocalDate? = null): TimelineRow {
        val localStart = date?.let { LocalDateTime(it, LocalTime(9, 0)) }
        val actividad = Actividad(
            id = id,
            title = "Actividad $id",
            type = "event",
            status = "created",
            categoryId = "cat-1",
            criticality = "low",
        )
        return TimelineRow(
            actividad = actividad,
            scheduledStart = localStart?.toInstant(TimeZone.UTC),
            localStart = localStart,
            deadline = null,
        )
    }

    /** Día de riel sin bloques: el eje por defecto de `buildRailDay`. */
    private fun railDay(date: LocalDate, blocks: List<RailBlock> = emptyList()): RailDay =
        RailDay(
            date = date,
            axis = RailAxis(startMinute = 7 * 60, endMinute = 21 * 60),
            blocks = blocks,
            hourTicks = emptyList(),
        )

    private fun datedSection(
        date: LocalDate,
        isExpanded: Boolean = true,
        blocks: List<RailBlock> = emptyList(),
    ): RailSection = RailSection(
        date = date,
        railDay = railDay(date, blocks),
        isExpanded = isExpanded,
    )

    private fun undatedSection(count: Int, isExpanded: Boolean = true): RailSection = RailSection(
        date = null,
        railDay = null,
        undatedRows = List(count) { index -> row("sin-fecha-$index") },
        isExpanded = isExpanded,
    )

    private val day12 = LocalDate(2026, 5, 12)
    private val day13 = LocalDate(2026, 5, 13)

    // ── S3 · railItemIndexOf ─────────────────────────────────────────────

    @Test
    fun `una seccion expandida pone su encabezado en el indice cero`() {
        val sections = listOf(datedSection(day12))

        assertEquals(0, railItemIndexOf(sections, day12))
    }

    @Test
    fun `dos secciones expandidas ponen el segundo encabezado en el indice dos`() {
        // [encabezado 12, lienzo 12, encabezado 13, lienzo 13]
        val sections = listOf(datedSection(day12), datedSection(day13))

        assertEquals(0, railItemIndexOf(sections, day12))
        assertEquals(2, railItemIndexOf(sections, day13))
    }

    @Test
    fun `una primera seccion plegada corre el segundo encabezado`() {
        // [encabezado 12, encabezado 13, lienzo 13] — el lienzo del 12 no existe.
        val sections = listOf(datedSection(day12, isExpanded = false), datedSection(day13))

        assertEquals(0, railItemIndexOf(sections, day12))
        assertEquals(1, railItemIndexOf(sections, day13))
    }

    @Test
    fun `la seccion sin fecha cuenta una fila por actividad`() {
        // [encabezado sin fecha, fila, fila, fila, encabezado 13, lienzo 13]
        val sections = listOf(undatedSection(3), datedSection(day13))

        assertEquals(4, railItemIndexOf(sections, day13))
    }

    @Test
    fun `la seccion sin fecha no se pliega y conserva sus filas`() {
        val section = undatedSection(count = 2, isExpanded = false)

        assertEquals(2, railContentCount(section))
    }

    @Test
    fun `una fecha ausente no tiene encabezado`() {
        val sections = listOf(datedSection(day12), undatedSection(1))

        assertNull(railItemIndexOf(sections, LocalDate(2026, 5, 20)))
    }

    @Test
    fun `el encabezado de una seccion plegada se encuentra igual`() {
        val sections = listOf(datedSection(day12, isExpanded = false))

        assertEquals(0, railItemIndexOf(sections, day12))
    }

    @Test
    fun `una lista vacia no tiene ningun encabezado`() {
        assertNull(railItemIndexOf(emptyList(), day12))
    }

    // ── S3 · railContentCount ────────────────────────────────────────────

    @Test
    fun `un dia sin bloques sigue contando un item de contenido`() {
        val sections = listOf(datedSection(day12), datedSection(day13))

        assertEquals(1, railContentCount(sections[0]))
        // El lienzo del 12 existe aunque esté vacío, así que el 13 arranca en 2.
        assertEquals(2, railItemIndexOf(sections, day13))
    }

    @Test
    fun `una seccion plegada no aporta items de contenido`() {
        assertEquals(0, railContentCount(datedSection(day12, isExpanded = false)))
    }

    // ── S15 · anclaje inicial del riel ───────────────────────────────────

    private fun block(startMinute: Int, endMinute: Int, id: String = "b-$startMinute"): RailBlock =
        RailBlock(
            row = row(id),
            startMinute = startMinute,
            endMinute = endMinute,
            lane = 0,
            laneCount = 1,
        )

    @Test
    fun `hay algo en curso ahora y el riel se ancla en la linea de ahora`() {
        // 10:00–11:40 con el reloj en 10:30: el bloque que contiene `now` manda.
        val sections = listOf(datedSection(day13, blocks = listOf(block(600, 700))))

        assertEquals(
            RailAnchor(date = day13, minuteOfDay = 630, kind = RailAnchorKind.InProgress),
            railInitialAnchor(sections, day13, 630),
        )
    }

    @Test
    fun `sin nada en curso el anclaje cae en el primer bloque del primer dia con contenido`() {
        // El 12 no tiene bloques: el anclaje salta al 13, en su bloque MÁS TEMPRANO.
        val sections = listOf(
            datedSection(day12),
            datedSection(day13, blocks = listOf(block(600, 660), block(540, 580, id = "temprano"))),
        )

        assertEquals(
            RailAnchor(date = day13, minuteOfDay = 540, kind = RailAnchorKind.FirstBlock),
            railInitialAnchor(sections, day13, 400),
        )
    }

    @Test
    fun `un bloque de otro dia que contiene el minuto de ahora no esta en curso`() {
        // 10:00–11:40 del día 12 contiene el minuto 630 del reloj, pero HOY es el 13.
        val sections = listOf(datedSection(day12, blocks = listOf(block(600, 700))), datedSection(day13))

        assertEquals(
            RailAnchor(date = day12, minuteOfDay = 600, kind = RailAnchorKind.FirstBlock),
            railInitialAnchor(sections, day13, 630),
        )
    }

    @Test
    fun `el inicio del bloque cuenta como en curso y el fin no`() {
        val sections = listOf(datedSection(day13, blocks = listOf(block(600, 660))))

        assertEquals(RailAnchorKind.InProgress, railInitialAnchor(sections, day13, 600)?.kind)
        // `[start, end)`: a las 11:00 exactas el bloque ya terminó, así que cae al primer bloque.
        assertEquals(RailAnchorKind.FirstBlock, railInitialAnchor(sections, day13, 660)?.kind)
    }

    @Test
    fun `un dia de hoy plegado se ancla igual`() {
        // El plegado es de la VISTA; los bloques del día siguen en la sección.
        val sections = listOf(datedSection(day13, isExpanded = false, blocks = listOf(block(600, 700))))

        assertEquals(
            RailAnchor(date = day13, minuteOfDay = 630, kind = RailAnchorKind.InProgress),
            railInitialAnchor(sections, day13, 630),
        )
    }

    @Test
    fun `sin bloques en ninguna seccion no hay anclaje`() {
        val sections = listOf(datedSection(day12), datedSection(day13))

        assertNull(railInitialAnchor(sections, day13, 630))
        assertNull(railInitialAnchor(emptyList(), day13, 630))
    }

    @Test
    fun `la seccion sin fecha nunca es el anclaje inicial`() {
        // Sus filas no tienen eje horario ni minuto, así que no hay dónde posarse.
        val sections = listOf(undatedSection(2))

        assertNull(railInitialAnchor(sections, day13, 630))
    }

    // ── S3 · rótulo compacto del día ─────────────────────────────────────

    @Test
    fun `el rotulo compacto del dia se deriva de las tablas existentes`() {
        // 2026-05-13 es miércoles; el arteboard lo escribe "MIÉ 13 MAY".
        assertEquals("MIÉ 13 MAY", railDayLabel(day13))
        assertEquals("MAR 12 MAY", railDayLabel(day12))
        // Un día de una cifra NO se rellena con cero: es titular, no columna de lista.
        assertEquals("MAR 5 MAY", railDayLabel(LocalDate(2026, 5, 5)))
    }
}
