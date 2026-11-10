package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5 de `odd/tasks/activity-rail.md` — mapa de densidad mensual (S4).
 *
 * TEST-FIRST: este archivo se escribió ANTES que `MonthDensity.kt`; la primera corrida dio
 * ROJO por compilación (las funciones todavía no existían) y ése es el RED registrado.
 *
 * Todo es Kotlin puro: ni Compose ni estado, y NINGÚN `Clock.System` — la zona y las fechas
 * entran por parámetro, así que el test no cambia con el reloj de la máquina.
 */
class MonthDensityTest {

    // ── Utilidades de fixture ────────────────────────────────────────────

    /** Zona con offset negativo: la que prueba que el mes se decide EN LA ZONA dada. */
    private val buenosAires = TimeZone.of("America/Argentina/Buenos_Aires")

    /**
     * Fila con un inicio programado [startUtc] (ISO-8601) o sin inicio cuando es `null`.
     *
     * `localStart` se calcula SIEMPRE en UTC a propósito: así `buildMonthDensity` no puede
     * “colarse” por el campo ya derivado y tiene que decidir el mes con la zona que recibe.
     */
    private fun row(
        id: String,
        startUtc: String? = null,
        status: String = "created",
    ): TimelineRow {
        val scheduledStart = startUtc?.let(Instant::parse)
        val actividad = Actividad(
            id = id,
            title = "Actividad $id",
            type = "event",
            status = status,
            categoryId = "cat-1",
            criticality = "low",
            scheduledStart = startUtc,
        )
        return TimelineRow(
            actividad = actividad,
            scheduledStart = scheduledStart,
            localStart = scheduledStart?.toLocalDateTime(TimeZone.UTC),
            deadline = null,
        )
    }

    // ── S4 · densidad por día ────────────────────────────────────────────

    @Test
    fun `cuenta las actividades de cada dia del mes`() {
        val density = buildMonthDensity(
            rows = listOf(
                row("a", "2026-05-05T09:00:00Z"),
                row("b", "2026-05-05T17:30:00Z"),
                row("c", "2026-05-07T08:00:00Z"),
            ),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertEquals(2, density.getValue(LocalDate(2026, 5, 5)).count)
        assertEquals(1, density.getValue(LocalDate(2026, 5, 7)).count)
    }

    @Test
    fun `dos actividades el mismo dia dan nivel medio`() {
        val density = buildMonthDensity(
            rows = listOf(
                row("a", "2026-05-05T09:00:00Z"),
                row("b", "2026-05-05T10:00:00Z"),
            ),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        val day = density.getValue(LocalDate(2026, 5, 5))
        assertEquals(2, day.count)
        assertEquals(DensityLevel.MEDIUM, densityLevel(day.count))
    }

    @Test
    fun `cuatro o mas actividades dan nivel lleno`() {
        val four = buildMonthDensity(
            rows = (0..3).map { index -> row("a$index", "2026-05-05T0$index:00:00Z") },
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )
        val five = buildMonthDensity(
            rows = (0..4).map { index -> row("b$index", "2026-05-05T0$index:00:00Z") },
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertEquals(4, four.getValue(LocalDate(2026, 5, 5)).count)
        assertEquals(DensityLevel.FULL, densityLevel(four.getValue(LocalDate(2026, 5, 5)).count))
        assertEquals(DensityLevel.FULL, densityLevel(five.getValue(LocalDate(2026, 5, 5)).count))
    }

    @Test
    fun `los niveles de densidad son los cuatro escalones mas el vacio`() {
        assertEquals(DensityLevel.EMPTY, densityLevel(0))
        assertEquals(DensityLevel.LOW, densityLevel(1))
        assertEquals(DensityLevel.MEDIUM, densityLevel(2))
        assertEquals(DensityLevel.HIGH, densityLevel(3))
        assertEquals(DensityLevel.FULL, densityLevel(4))
        assertEquals(DensityLevel.FULL, densityLevel(9))
    }

    @Test
    fun `un dia con una actividad vencida marca el dia como vencido`() {
        val density = buildMonthDensity(
            rows = listOf(
                row("a", "2026-05-05T09:00:00Z"),
                row("b", "2026-05-05T10:00:00Z", status = "overdue"),
                row("c", "2026-05-06T09:00:00Z", status = "done"),
            ),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertTrue(density.getValue(LocalDate(2026, 5, 5)).hasOverdue)
        assertFalse(density.getValue(LocalDate(2026, 5, 6)).hasOverdue)
    }

    @Test
    fun `las filas sin fecha de inicio no cuentan`() {
        val density = buildMonthDensity(
            rows = listOf(
                row("sin-fecha"),
                row("a", "2026-05-05T09:00:00Z"),
            ),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertEquals(mapOf(LocalDate(2026, 5, 5) to MonthDensity(count = 1, hasOverdue = false)), density)
    }

    @Test
    fun `una fila del mes anterior en la zona dada queda fuera del mes`() {
        // 01-jun 01:00 UTC es 31-may 22:00 en Buenos Aires: pertenece a MAYO, no a junio.
        val rows = listOf(row("a", "2026-06-01T01:00:00Z"))

        val junio = buildMonthDensity(rows, year = 2026, monthNumber = 6, zone = buenosAires)
        val mayo = buildMonthDensity(rows, year = 2026, monthNumber = 5, zone = buenosAires)

        assertTrue(junio.isEmpty())
        assertEquals(1, mayo.getValue(LocalDate(2026, 5, 31)).count)
        // Y en UTC la MISMA fila sí es de junio: el mes lo decide la zona, no el `localStart`.
        assertEquals(1, buildMonthDensity(rows, 2026, 6, TimeZone.UTC).getValue(LocalDate(2026, 6, 1)).count)
    }

    @Test
    fun `una fila de otro mes queda fuera del mapa`() {
        val density = buildMonthDensity(
            rows = listOf(
                row("a", "2026-04-30T09:00:00Z"),
                row("b", "2026-06-01T09:00:00Z"),
            ),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertTrue(density.isEmpty())
    }

    @Test
    fun `los dias sin filas no aparecen en el mapa`() {
        val density = buildMonthDensity(
            rows = listOf(row("a", "2026-05-05T09:00:00Z")),
            year = 2026,
            monthNumber = 5,
            zone = TimeZone.UTC,
        )

        assertEquals(1, density.size)
        assertTrue(density.containsKey(LocalDate(2026, 5, 5)))
        assertNull(density[LocalDate(2026, 5, 6)])
        assertFalse(density.containsKey(LocalDate(2026, 5, 6)))
    }

    @Test
    fun `una lista vacia da un mapa vacio`() {
        assertEquals(emptyMap(), buildMonthDensity(emptyList(), 2026, 5, TimeZone.UTC))
    }

    // ── S4 · rótulo accesible de la celda ────────────────────────────────

    @Test
    fun `la etiqueta de la celda dice hoy y cuantas actividades hay`() {
        val label = densityCellSemanticLabel(
            date = LocalDate(2026, 5, 12),
            today = LocalDate(2026, 5, 12),
            density = MonthDensity(count = 2, hasOverdue = false),
        )

        assertEquals("martes 12 de mayo, hoy, 2 actividades", label)
    }

    @Test
    fun `la etiqueta de la celda anuncia la actividad vencida`() {
        val label = densityCellSemanticLabel(
            date = LocalDate(2026, 5, 13),
            today = LocalDate(2026, 5, 12),
            density = MonthDensity(count = 1, hasOverdue = true),
        )

        assertEquals("miércoles 13 de mayo, 1 actividad, vencida", label)
    }

    @Test
    fun `la etiqueta de un dia sin actividades lo dice`() {
        val label = densityCellSemanticLabel(
            date = LocalDate(2026, 5, 14),
            today = LocalDate(2026, 5, 12),
            density = null,
        )

        assertEquals("jueves 14 de mayo, sin actividades", label)
    }
}
