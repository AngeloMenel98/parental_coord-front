package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * C1 (activity-card-stack): el salto del mapa de densidad sigue vivo con la Nueva ÚNICA
 * vista — `agendaItemIndexOf` es la MISMA cuenta que monta `AgendaTimeline`
 * (hint + header + un ítem por fila, día ascendente, sin-fecha al final).
 */
class AgendaItemIndexTest {
    private fun row(id: String, date: String = "2026-05-13"): TimelineRow {
        val scheduledStart = Instant.parse("${date}T09:00:00Z")
        return TimelineRow(
            actividad = actividadOf(id),
            scheduledStart = scheduledStart,
            localStart = scheduledStart.toLocalDateTime(zone),
            deadline = null,
        )
    }

    private val zone = TimeZone.UTC

    private fun actividadOf(id: String) = Actividad(
        id = id,
        title = id,
        type = "event",
        status = "assigned",
        categoryId = "cat-1",
        criticality = "medium",
    )

    private val d13 = LocalDate(2026, 5, 13)
    private val d14 = LocalDate(2026, 5, 14)

    @Test
    fun `hint + header son los dos primeros indices de un grupo`() {
        val groups = buildAgendaGroups(listOf(row("a"), row("b")))
        assertEquals(1, agendaItemIndexOf(groups, d13))
    }

    @Test
    fun `el segundo dia sube por header + items del primero`() {
        val groups = buildAgendaGroups(listOf(row("a", "2026-05-13"), row("b", "2026-05-14")))
        assertEquals(1 + 1 + 1, agendaItemIndexOf(groups, d14))
    }

    @Test
    fun `dias fuera de la lista no existen`() {
        val groups = buildAgendaGroups(listOf(row("a")))
        assertNull(agendaItemIndexOf(groups, LocalDate(2026, 6, 1)))
    }

    @Test
    fun `el grupo sin fecha es destino imposible y vive ultimo`() {
        val groups = buildAgendaGroups(listOf(row("a", "2026-05-13")))
        assertNull(agendaItemIndexOf(groups, LocalDate.parse("0001-01-01")))
        // el índice del 13 no se movió: la fila sin fecha no existe en este caso
        assertEquals(1, agendaItemIndexOf(groups, d13))
    }
}
