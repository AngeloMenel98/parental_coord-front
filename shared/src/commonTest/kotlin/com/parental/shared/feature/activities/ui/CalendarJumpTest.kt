package com.parental.shared.feature.activities.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T1 de `odd/tasks/activities-cards-ui.md` — derivación pura de la hoja de calendario
 * (S3) y de los rótulos de grupo/día (S4, S5).
 *
 * TEST-FIRST: este archivo se escribió ANTES que `CalendarJump.kt`; la primera corrida
 * dio ROJO por compilación (las funciones todavía no existían) y ése es el RED registrado.
 *
 * Todo es Kotlin puro: ni Compose ni estado. La UI (T2–T5) consume estas funciones, no
 * al revés.
 */
class CalendarJumpTest {

    // ── Utilidades de fixture ────────────────────────────────────────────

    private fun row(
        id: String,
        title: String = "Actividad $id",
        type: String = "event",
        categoryId: String = "cat-1",
        date: LocalDate? = LocalDate(2026, 5, 12),
        hour: Int = 9,
        minute: Int = 0,
    ): TimelineRow {
        val localStart = date?.let { LocalDateTime(it, LocalTime(hour, minute)) }
        val actividad = Actividad(
            id = id,
            title = title,
            type = type,
            status = "created",
            categoryId = categoryId,
            criticality = "low",
        )
        return TimelineRow(
            actividad = actividad,
            scheduledStart = localStart?.toInstant(TimeZone.UTC),
            localStart = localStart,
            deadline = null,
        )
    }

    private fun info(name: String) = CategoryInfo(
        name = name,
        colorHex = null,
        emoji = null,
    )

    private fun resolver(vararg names: String): (String) -> CategoryInfo? = { id ->
        names.firstOrNull { it == id }?.let { info(it) }
    }

    // ── S3 · buildMonthGrid ───────────────────────────────────────────────

    @Test
    fun `la grilla de mayo 2026 arranca en lunes y tiene 35 celdas`() {
        val grid = buildMonthGrid(2026, 5)

        assertEquals(35, grid.size)
        // lunes 27 de abril de 2026
        assertEquals(LocalDate(2026, 4, 27), grid.first())
        // el mes completo está dentro de la grilla
        assertTrue(grid.contains(LocalDate(2026, 5, 1)))
        assertTrue(grid.contains(LocalDate(2026, 5, 31)))
    }

    @Test
    fun `la grilla de febrero 2026 empieza en domingo y tiene 35 celdas`() {
        val grid = buildMonthGrid(2026, 2)

        assertEquals(35, grid.size)
        // lunes 26 de enero de 2026 — febrero 1 es domingo
        assertEquals(LocalDate(2026, 1, 26), grid.first())
        assertTrue(grid.contains(LocalDate(2026, 2, 28)))
        // la última celda es el domingo siguiente
        assertEquals(LocalDate(2026, 3, 1), grid.last())
    }

    @Test
    fun `la grilla de junio 2026 arranca el propio lunes y tiene 35 celdas`() {
        val grid = buildMonthGrid(2026, 6)

        assertEquals(35, grid.size)
        assertEquals(LocalDate(2026, 6, 1), grid.first())
    }

    @Test
    fun `la grilla de agosto 2026 necesita 42 celdas`() {
        val grid = buildMonthGrid(2026, 8)

        assertEquals(42, grid.size)
        // lunes 27 de julio de 2026 — agosto 1 es sábado
        assertEquals(LocalDate(2026, 7, 27), grid.first())
        assertTrue(grid.contains(LocalDate(2026, 8, 31)))
    }

    @Test
    fun `las celdas son fechas consecutivas sin huecos`() {
        val grid = buildMonthGrid(2026, 5)

        grid.zipWithNext { previous, next ->
            assertEquals(previous.plus(1, kotlinx.datetime.DateTimeUnit.DAY), next)
        }
    }

    // ── S3 · buildDayDots ─────────────────────────────────────────────────

    @Test
    fun `los puntos del día se ordenan por la hora de inicio más temprana`() {
        val rows = listOf(
            row("c", categoryId = "cat-c", hour = 18),
            row("a", categoryId = "cat-a", hour = 9),
            row("b", categoryId = "cat-b", hour = 14),
        )

        val dots = buildDayDots(rows, resolver("cat-a", "cat-b", "cat-c"))

        assertEquals(listOf("cat-a", "cat-b", "cat-c"), dots[LocalDate(2026, 5, 12)]?.map { it?.name })
    }

    @Test
    fun `las categorías repetidas producen un solo punto`() {
        val rows = listOf(
            row("a", categoryId = "cat-a", hour = 9),
            row("b", categoryId = "cat-a", hour = 11),
        )

        val dots = buildDayDots(rows, resolver("cat-a"))

        assertEquals(1, dots[LocalDate(2026, 5, 12)]?.size)
    }

    @Test
    fun `el día muestra como máximo tres puntos`() {
        val rows = listOf(
            row("a", categoryId = "cat-a", hour = 9),
            row("b", categoryId = "cat-b", hour = 10),
            row("c", categoryId = "cat-c", hour = 11),
            row("d", categoryId = "cat-d", hour = 12),
        )

        val dots = buildDayDots(rows, resolver("cat-a", "cat-b", "cat-c", "cat-d"))

        assertEquals(3, dots[LocalDate(2026, 5, 12)]?.size)
    }

    @Test
    fun `las filas sin fecha de inicio no generan puntos`() {
        val rows = listOf(row("a", date = null))

        assertTrue(buildDayDots(rows, resolver("cat-a")).isEmpty())
    }

    @Test
    fun `una categoría desconocida se conserva como null`() {
        val rows = listOf(row("a", categoryId = "desconocida"))

        val dots = buildDayDots(rows, resolver("cat-a"))

        assertEquals(listOf(null), dots[LocalDate(2026, 5, 12)])
    }

    @Test
    fun `los puntos se agrupan por día`() {
        val rows = listOf(
            row("a", date = LocalDate(2026, 5, 12)),
            row("b", date = LocalDate(2026, 5, 13)),
        )

        val dots = buildDayDots(rows, resolver("cat-a"))

        assertEquals(setOf(LocalDate(2026, 5, 12), LocalDate(2026, 5, 13)), dots.keys)
    }

    // ── S3 · agendaHeaderIndex ───────────────────────────────────────────

    @Test
    fun `el indice del encabezado cuenta la pista y las tarjetas previas`() {
        val groups = listOf(
            AgendaGroup(LocalDate(2026, 5, 12), listOf(row("a"), row("b"))),
            AgendaGroup(LocalDate(2026, 5, 13), listOf(row("c"))),
            AgendaGroup(null, listOf(row("d"))),
        )

        // La lista del feed es: [pista, encabezado, tarjetas…, encabezado, tarjetas…].
        assertEquals(1, agendaHeaderIndex(groups, LocalDate(2026, 5, 12)))
        assertEquals(4, agendaHeaderIndex(groups, LocalDate(2026, 5, 13)))
    }

    @Test
    fun `un dia que no tiene grupo no tiene encabezado`() {
        val groups = listOf(AgendaGroup(LocalDate(2026, 5, 12), listOf(row("a"))))

        assertNull(agendaHeaderIndex(groups, LocalDate(2026, 5, 20)))
    }

    @Test
    fun `una lista sin grupos no tiene ningun encabezado`() {
        assertNull(agendaHeaderIndex(emptyList(), LocalDate(2026, 5, 12)))
    }

    // ── S3 · jumpTargetDate ───────────────────────────────────────────────

    @Test
    fun `un día exacto salta a ese día`() {
        val groups = listOf(
            AgendaGroup(LocalDate(2026, 5, 12), listOf(row("a"))),
            AgendaGroup(LocalDate(2026, 5, 13), listOf(row("b"))),
        )

        assertEquals(LocalDate(2026, 5, 12), jumpTargetDate(LocalDate(2026, 5, 12), groups))
    }

    @Test
    fun `un día sin actividades salta al grupo siguiente más cercano`() {
        val groups = listOf(
            AgendaGroup(LocalDate(2026, 5, 12), listOf(row("a"))),
            AgendaGroup(LocalDate(2026, 5, 13), listOf(row("b"))),
            AgendaGroup(LocalDate(2026, 5, 20), listOf(row("c"))),
        )

        // 15 de mayo → el grupo MÁS CERCANO HACIA ADELANTE (arteboard: "el grupo con
        // actividades más cercano hacia adelante"), o sea el 20, no el 13 de mayo.
        assertEquals(LocalDate(2026, 5, 20), jumpTargetDate(LocalDate(2026, 5, 15), groups))
    }

    @Test
    fun `sin grupo siguiente salta al grupo anterior más reciente`() {
        val groups = listOf(
            AgendaGroup(LocalDate(2026, 5, 5), listOf(row("a"))),
            AgendaGroup(LocalDate(2026, 5, 12), listOf(row("b"))),
        )

        assertEquals(LocalDate(2026, 5, 12), jumpTargetDate(LocalDate(2026, 5, 18), groups))
    }

    @Test
    fun `sin grupos con fecha no hay destino`() {
        val groups = listOf(AgendaGroup(null, listOf(row("a"))))

        assertNull(jumpTargetDate(LocalDate(2026, 5, 12), groups))
    }

    @Test
    fun `el grupo sin fecha nunca es destino`() {
        val groups = listOf(
            AgendaGroup(null, listOf(row("a"))),
            AgendaGroup(LocalDate(2026, 5, 12), listOf(row("b"))),
        )

        assertEquals(LocalDate(2026, 5, 12), jumpTargetDate(LocalDate(2026, 5, 1), groups))
    }

    // ── S5 · tabCounts ────────────────────────────────────────────────────

    @Test
    fun `los contadores de pestañas respetan el tipo de actividad`() {
        val actividades = listOf(
            row("a", type = "event").actividad,
            row("b", type = "event").actividad,
            row("c", type = "obligation").actividad,
        )

        val counts = tabCounts(actividades)

        assertEquals(3, counts[ActivityTab.TODOS])
        assertEquals(2, counts[ActivityTab.EVENTOS])
        assertEquals(1, counts[ActivityTab.OBLIGACIONES])
    }

    @Test
    fun `una lista vacía cuenta cero en todas las pestañas`() {
        val counts = tabCounts(emptyList())

        assertEquals(0, counts[ActivityTab.TODOS])
        assertEquals(0, counts[ActivityTab.EVENTOS])
        assertEquals(0, counts[ActivityTab.OBLIGACIONES])
    }

    // ── S4 · formatGroupTitle ─────────────────────────────────────────────

    @Test
    fun `el título del grupo usa día de semana y mes en minúsculas`() {
        // 2026-05-11 es lunes y 2026-05-12 es martes: los dos casos fijan el mapeo.
        assertEquals("lunes 11 de mayo", formatGroupTitle(LocalDate(2026, 5, 11), LocalDate(2026, 5, 20)))
        assertEquals("martes 12 de mayo", formatGroupTitle(LocalDate(2026, 5, 12), LocalDate(2026, 5, 20)))
    }

    @Test
    fun `el grupo de hoy se marca`() {
        assertEquals(
            "martes 12 de mayo · hoy",
            formatGroupTitle(LocalDate(2026, 5, 12), LocalDate(2026, 5, 12)),
        )
    }

    // ── S3 · dayCellSemanticLabel ────────────────────────────────────────

    @Test
    fun `una celda con varias actividades las anuncia con dos puntos`() {
        val label = dayCellSemanticLabel(
            date = LocalDate(2026, 5, 12),
            today = LocalDate(2026, 5, 12),
            categories = listOf(info("Salud"), info("Educación"), info("Recreación")),
        )

        assertEquals(
            "martes 12 de mayo, hoy, 3 actividades: Salud, Educación, Recreación",
            label,
        )
    }

    @Test
    fun `una celda con una sola actividad la anuncia con coma`() {
        val label = dayCellSemanticLabel(
            date = LocalDate(2026, 5, 5),
            today = LocalDate(2026, 5, 12),
            categories = listOf(info("Familiar")),
        )

        assertEquals("martes 5 de mayo, 1 actividad, Familiar", label)
    }

    @Test
    fun `una celda sin actividades lo dice`() {
        val label = dayCellSemanticLabel(
            date = LocalDate(2026, 5, 4),
            today = LocalDate(2026, 5, 12),
            categories = emptyList(),
        )

        assertEquals("lunes 4 de mayo, sin actividades", label)
    }

    @Test
    fun `una categoría desconocida se anuncia como desconocida`() {
        val label = dayCellSemanticLabel(
            date = LocalDate(2026, 5, 5),
            today = LocalDate(2026, 5, 12),
            categories = listOf(null),
        )

        assertEquals("martes 5 de mayo, 1 actividad, desconocida", label)
    }
}
