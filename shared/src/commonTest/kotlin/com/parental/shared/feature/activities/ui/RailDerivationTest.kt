package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T1 de `odd/tasks/activity-rail.md` — derivación pura del riel (S3): eje del día,
 * carriles de solapamiento, ticks horarios y escalas minutos→dp.
 *
 * TEST-FIRST: este archivo se escribió ANTES que `RailDerivation.kt`; la primera corrida
 * dio ROJO por compilación (las funciones todavía no existían) y ése es el RED registrado.
 *
 * Todo es Kotlin puro: ni Compose ni estado. La zona es siempre un parámetro
 * ([TimeZone.UTC] salvo la prueba de zona) y nunca se lee el reloj del sistema.
 */
class RailDerivationTest {

    // ── Utilidades de fixture ────────────────────────────────────────────

    private val day = LocalDate(2026, 5, 12)

    private fun at(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime(day, LocalTime(hour, minute))

    /**
     * Fila del riel con inicio local [start] y duración en minutos.
     *
     * `start == null` produce la fila SIN fecha de inicio (la de la sección sin fecha).
     * `durationMinutes == null` deja `scheduledEnd` nulo; `0` lo deja IGUAL al inicio.
     */
    private fun row(
        id: String,
        start: LocalDateTime?,
        durationMinutes: Int? = null,
        zone: TimeZone = TimeZone.UTC,
    ): TimelineRow {
        val startInstant = start?.toInstant(zone)
        val endInstant = durationMinutes?.let { minutes ->
            startInstant?.plus(minutes, DateTimeUnit.MINUTE)
        }
        val actividad = Actividad(
            id = id,
            title = "Actividad $id",
            type = "event",
            status = "created",
            categoryId = "cat-1",
            criticality = "low",
            scheduledStart = startInstant?.toString(),
            scheduledEnd = endInstant?.toString(),
        )
        return TimelineRow(
            actividad = actividad,
            scheduledStart = startInstant,
            localStart = start,
            deadline = null,
        )
    }

    /** Un día de riel para [rows] en UTC — el caso feliz de casi todas las pruebas. */
    private fun rail(vararg rows: TimelineRow, zone: TimeZone = TimeZone.UTC): RailDay =
        buildRailDay(rows.toList(), day, zone)

    // ── S3 · altura proporcional y mínima ────────────────────────────────

    @Test
    fun `un bloque de quince minutos no baja de la altura minima y uno largo crece`() {
        val short = rail(row("corta", at(9), 15)).blocks.single()
        assertEquals(540, short.startMinute)
        assertEquals(555, short.endMinute)
        // 15 min a 64dp/h son 16dp: el mínimo de toque (S11, 44dp) gana.
        assertEquals(44f, railHeightDp(short, dpPerHour = 64f, minHeightDp = 44f), 0.001f)

        val long = rail(row("larga", at(9), 90)).blocks.single()
        // 90 min a 64dp/h son 96dp: la proporción gana al mínimo.
        assertEquals(96f, railHeightDp(long, dpPerHour = 64f, minHeightDp = 44f), 0.001f)
    }

    // ── S3 · carriles de solapamiento ────────────────────────────────────

    @Test
    fun `dos bloques solapados ocupan dos carriles`() {
        val result = rail(
            row("a", at(9), 60),
            row("b", at(9, 30), 60),
        )

        val blocks = result.blocks
        assertEquals(2, blocks.size)
        assertEquals(listOf("a", "b"), blocks.map { it.row.actividad.id })
        assertEquals(listOf(0, 1), blocks.map { it.lane })
        assertEquals(listOf(2, 2), blocks.map { it.laneCount })
    }

    @Test
    fun `tres bloques solapados ocupan tres carriles`() {
        val result = rail(
            row("a", at(9), 60),
            row("b", at(9, 15), 60),
            row("c", at(9, 30), 60),
        )

        assertEquals(listOf(0, 1, 2), result.blocks.map { it.lane })
        assertEquals(listOf(3, 3, 3), result.blocks.map { it.laneCount })
    }

    @Test
    fun `los bloques que no se solapan comparten el carril cero`() {
        val result = rail(
            row("a", at(9), 60),
            row("b", at(11), 60),
        )

        assertEquals(listOf(0, 0), result.blocks.map { it.lane })
        assertEquals(listOf(1, 1), result.blocks.map { it.laneCount })
    }

    @Test
    fun `una cadena de solapamientos reparte el ancho maximo del grupo`() {
        // A y C no se tocan, pero B encadena los tres: el grupo es uno solo y su
        // concurrencia máxima es 2, así que C no puede pintarse a ancho completo.
        val result = rail(
            row("a", at(9), 60),
            row("b", at(9, 30), 60),
            row("c", at(10, 15), 60),
        )

        assertEquals(listOf("a", "b", "c"), result.blocks.map { it.row.actividad.id })
        assertEquals(listOf(0, 1, 0), result.blocks.map { it.lane })
        assertEquals(listOf(2, 2, 2), result.blocks.map { it.laneCount })
    }

    @Test
    fun `el fin de un bloque no cuenta como solapamiento`() {
        // [09:00, 10:00) y [10:00, 11:00) son contiguos: un solo carril.
        val result = rail(
            row("a", at(9), 60),
            row("b", at(10), 60),
        )

        assertEquals(listOf(0, 0), result.blocks.map { it.lane })
        assertEquals(listOf(1, 1), result.blocks.map { it.laneCount })
    }

    // ── S3 · eje del día ─────────────────────────────────────────────────

    @Test
    fun `el eje se extiende hacia atras y hacia adelante`() {
        val result = rail(
            row("temprano", at(6, 30), 30),
            row("tarde", at(21, 30), 45),
        )

        // 06:30 → 06:00 y el fin de las 22:15 → 23:00.
        assertEquals(RailAxis(startMinute = 6 * 60, endMinute = 23 * 60), result.axis)
    }

    @Test
    fun `un dia vacio devuelve el eje por defecto y ningun bloque`() {
        val result = buildRailDay(emptyList(), day, TimeZone.UTC)

        assertEquals(RailAxis(startMinute = 7 * 60, endMinute = 21 * 60), result.axis)
        assertTrue(result.blocks.isEmpty())
        assertEquals(day, result.date)
    }

    @Test
    fun `una fila sin fecha de inicio queda fuera del riel`() {
        val result = rail(row("sin-fecha", start = null))

        assertTrue(result.blocks.isEmpty())
        assertEquals(RailAxis(startMinute = 7 * 60, endMinute = 21 * 60), result.axis)
    }

    @Test
    fun `un fin igual al inicio o ilegible cae al minimo de bloque`() {
        val sameEnd = rail(row("igual", at(9), 0)).blocks.single()
        assertEquals(540, sameEnd.startMinute)
        assertEquals(560, sameEnd.endMinute)

        val unreadable = rail(
            row("ilegible", at(9), 60).let { base ->
                base.copy(actividad = base.actividad.copy(scheduledEnd = "no-es-una-fecha"))
            },
        ).blocks.single()
        assertEquals(560, unreadable.endMinute)

        val wider = buildRailDay(
            rows = listOf(row("ancho", at(9), 0)),
            date = day,
            zone = TimeZone.UTC,
            minBlockMinutes = 30,
        )
        assertEquals(570, wider.blocks.single().endMinute)
    }

    // ── S3 · orden del día ───────────────────────────────────────────────

    @Test
    fun `dos actividades a la misma hora ordenan primero la mas larga`() {
        val result = rail(
            row("corta", at(9), 30),
            row("larga", at(9), 60),
        )

        assertEquals(listOf("larga", "corta"), result.blocks.map { it.row.actividad.id })
    }

    // ── S3 · ticks horarios ──────────────────────────────────────────────

    @Test
    fun `los ticks horarios cubren el tramo del eje`() {
        val result = rail(
            row("temprano", at(6, 30), 30),
            row("tarde", at(21, 30), 45),
        )

        val expected = (0..17).map { 6 * 60 + it * 60 }
        assertEquals(expected, result.hourTicks)
        assertEquals(360, result.hourTicks.first())
        assertEquals(1380, result.hourTicks.last())
    }

    @Test
    fun `el tick del final del eje no se pisa cuando el fin no es en punto`() {
        // Un bloque solo: el eje queda en los valores por defecto, ya en punto.
        val result = buildRailDay(
            rows = listOf(row("unico", at(9), 60)),
            date = day,
            zone = TimeZone.UTC,
        )

        assertEquals(RailAxis(startMinute = 7 * 60, endMinute = 21 * 60), result.axis)
        assertEquals((0..14).map { 7 * 60 + it * 60 }, result.hourTicks)
    }

    // ── S3 · zona horaria ────────────────────────────────────────────────

    @Test
    fun `el filtro por fecha local respeta la zona`() {
        // 2026-05-13T02:00Z es el 12 de mayo a las 23:00 en Buenos Aires (UTC-3).
        val instant = LocalDateTime(2026, 5, 13, 2, 0).toInstant(TimeZone.UTC)
        val fila = row("nocturna", LocalDateTime(2026, 5, 13, 2, 0), 30)
        val buenosAires = TimeZone.of("America/Argentina/Buenos_Aires")

        val enBuenosAires = buildRailDay(listOf(fila), day, buenosAires)
        assertEquals(1, enBuenosAires.blocks.size)
        assertEquals(23 * 60, enBuenosAires.blocks.single().startMinute)
        assertEquals(instant, fila.scheduledStart)

        // La MISMA fila no pertenece al 12 de mayo en UTC.
        assertTrue(buildRailDay(listOf(fila), day, TimeZone.UTC).blocks.isEmpty())
        assertEquals(1, buildRailDay(listOf(fila), LocalDate(2026, 5, 13), TimeZone.UTC).blocks.size)
    }

    // ── S3 · reparto del ancho y escala vertical ─────────────────────────

    @Test
    fun `la fraccion de carril reparte el ancho en mitades y tercios`() {
        val halves = rail(
            row("a", at(9), 60),
            row("b", at(9, 30), 60),
        ).blocks
        val first = railLaneFraction(halves[0])
        assertEquals(0f, first.start, 0.001f)
        assertEquals(0.5f, first.endInclusive, 0.001f)

        val second = railLaneFraction(halves[1])
        assertEquals(0.5f, second.start, 0.001f)
        assertEquals(1f, second.endInclusive, 0.001f)

        val thirds = rail(
            row("a", at(9), 60),
            row("b", at(9, 15), 60),
            row("c", at(9, 30), 60),
        ).blocks
        val middle = railLaneFraction(thirds[1])
        assertEquals(1f / 3f, middle.start, 0.001f)
        assertEquals(2f / 3f, middle.endInclusive, 0.001f)
        val last = railLaneFraction(thirds[2])
        assertEquals(2f / 3f, last.start, 0.001f)
        assertEquals(1f, last.endInclusive, 0.001f)
    }

    @Test
    fun `el desplazamiento vertical se mide desde el inicio del eje`() {
        val axis = RailAxis(startMinute = 6 * 60, endMinute = 23 * 60)

        // 09:00 son 180 minutos después de las 06:00: 3 h × 64dp.
        assertEquals(192f, railOffsetDp(9 * 60, axis, dpPerHour = 64f), 0.001f)
        assertEquals(0f, railOffsetDp(6 * 60, axis, dpPerHour = 64f), 0.001f)
        assertEquals(
            17 * 64f,
            railOffsetDp(23 * 60, axis, dpPerHour = 64f),
            0.001f,
        )
    }
}
