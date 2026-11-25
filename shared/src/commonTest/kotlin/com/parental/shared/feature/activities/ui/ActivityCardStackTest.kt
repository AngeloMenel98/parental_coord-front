package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * S3 · Agrupación en franja horaria de la lista AGENDA (T2 de `odd/tasks/activity-card-stack.md`).
 *
 * La ley (regla 4): las pruebas del grupo son PURE DERIVACIÓN — nada de Compose. Aquí se
 * prueba `groupByStartSlot` de `TimelineDerivation.kt`: qué filas se apilan, con quién y
 * en qué orden; cómo se dibujan los fantasmas es del artboard, no de esta prueba.
 */
class ActivityCardStackTest {

    private val zone = TimeZone.UTC

    private fun actividad(id: String, title: String) = Actividad(
        id = id,
        title = title,
        type = "event",
        status = "assigned",
        categoryId = "cat-1",
        criticality = "medium",
    )

    private fun row(id: String, title: String, startIso: String?): TimelineRow {
        val scheduledStart = startIso?.let { Instant.parse(it) }
        return TimelineRow(
            actividad = actividad(id, title),
            scheduledStart = scheduledStart,
            localStart = scheduledStart?.toLocalDateTime(zone),
            deadline = null,
        )
    }

    @Test
    fun `dos filas en la misma franja fecha-hora-minuto se apilan`() {
        val entries = groupByStartSlot(
            listOf(
                row("a-1", "Cena con los abuelos", "2026-05-13T18:00:00Z"),
                row("a-2", "Voley en el club", "2026-05-13T18:00:00Z"),
            ),
        )

        assertEquals(1, entries.size, "La misma franja es UNA entrada, no dos cards sueltas")
        val stack = entries.single() as AgendaEntry.Stack
        assertEquals("a-1", stack.first.actividad.id)
        assertEquals(listOf("a-2"), stack.rest.map { it.actividad.id })
        assertEquals("2026-05-13T18:00", stack.slotKey)
    }

    @Test
    fun `tres en la misma franja, la primera encabeza y el resto apila detras`() {
        val entries = groupByStartSlot(
            listOf(
                row("a-1", "Cena", "2026-05-13T18:00:00Z"),
                row("a-2", "Voley", "2026-05-13T18:00:00Z"),
                row("a-3", "Cine", "2026-05-13T18:00:00Z"),
            ),
        )

        val stack = entries.single() as AgendaEntry.Stack
        assertEquals("a-1", stack.first.actividad.id)
        assertEquals(listOf("a-2", "a-3"), stack.rest.map { it.actividad.id })
    }

    @Test
    fun `minutos distintos NO se apilan`() {
        val entries = groupByStartSlot(
            listOf(
                row("a-1", "Fisioterapia", "2026-05-13T09:30:00Z"),
                row("a-2", "Visita medica", "2026-05-13T09:31:00Z"),
            ),
        )

        assertEquals(
            listOf("a-1", "a-2"),
            entries.map { (it as AgendaEntry.Single).row.actividad.id },
        )
    }

    @Test
    fun `misma franja en dias distintos NO se apilan`() {
        val entries = groupByStartSlot(
            listOf(
                row("a-1", "Turno del 13", "2026-05-13T09:30:00Z"),
                row("a-2", "Turno del 14", "2026-05-14T09:30:00Z"),
            ),
        )

        assertEquals(
            listOf("a-1", "a-2"),
            entries.map { (it as AgendaEntry.Single).row.actividad.id },
        )
    }

    @Test
    fun `Stack first es el PRIMERO en el orden de la lista`() {
        val entries = groupByStartSlot(
            listOf(
                row("b-2", "Voley", "2026-05-13T18:00:00Z"),
                row("b-1", "Cena", "2026-05-13T18:00:00Z"),
            ),
        )

        val stack = entries.single() as AgendaEntry.Stack
        assertEquals("b-2", stack.first.actividad.id, "El orden es el de la lista, no el del id")
        assertEquals("b-1", stack.rest.single().actividad.id)
    }

    @Test
    fun `la entrada apilada se emite en la posicion de su primera fila`() {
        val entries = groupByStartSlot(
            listOf(
                row("a-1", "Nueve y media", "2026-05-13T09:30:00Z"),
                row("a-3", "Diez", "2026-05-13T10:00:00Z"),
                row("a-2", "Nueve y media bis", "2026-05-13T09:30:00Z"),
            ),
        )

        assertTrue(
            entries[0] is AgendaEntry.Stack && entries[1] is AgendaEntry.Single,
            "La pila sale donde su primera fila; lo demás conserva su sitio",
        )
        assertEquals("a-1", (entries[0] as AgendaEntry.Stack).first.actividad.id)
        assertEquals("a-3", (entries[1] as AgendaEntry.Single).row.actividad.id)
    }

    @Test
    fun `una fila sola en su franja es Single — no hay pila de una`() {
        val entries = groupByStartSlot(
            listOf(row("c-1", "Almuerzo", "2026-05-13T12:00:00Z")),
        )

        assertTrue(entries.single() is AgendaEntry.Single)
    }

    @Test
    fun `filas sin fecha de inicio NO se apilan entre si`() {
        val entries = groupByStartSlot(
            listOf(
                row("d-1", "Sin fecha uno", null),
                row("d-2", "Sin fecha dos", null),
            ),
        )

        assertEquals(
            listOf("d-1", "d-2"),
            entries.map { (it as AgendaEntry.Single).row.actividad.id },
        )
    }
}
