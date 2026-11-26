package com.parental.shared.feature.activities.ui

import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.isOverdueStatus
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * S2 · El punto del riel de la lista AGENDA (T2 de `odd/tasks/activity-card-stack.md`).
 *
 * La ley (regla 4): derivación PURA — nada de Compose interno. Estos puntos son:
 *  · el punto mide 10dp de núcleo con anillo vencido punteado — su offset sale de una
 *    función pura y sus tamaños de constantes;
 *  · el color del punto es el de la CATEGORÍA (dot de la tríada);
 *  · la marca del anillo es el MISMO predicate de "vencida" que la card usa para la
 *    fecha límite en rojo.
 */
class AgendaRailTest {

    private fun row(status: String): TimelineRow = TimelineRow(
        actividad = Actividad(
            id = "a-1",
            title = "Reunión con la tutora",
            type = "event",
            status = status,
            categoryId = "cat-1",
            criticality = "high",
        ),
        scheduledStart = null,
        localStart = null,
        deadline = null,
    )

    @Test
    fun `status overdue marca el anillo rojo del riel`() {
        assertTrue(
            isOverdueRow(row("overdue")),
            "🔴 Un estado vencido no enciende el anillo del punto del riel",
        )
        assertTrue(isOverdueRow(row("OVERDUE")), "El backend manda minúsculas, el guard no distingue")
    }

    @Test
    fun `status no-vencido no marca el anillo`() {
        listOf("assigned", "in_progress", "verify", "done", "cancelled").forEach { status ->
            assertFalse(
                isOverdueRow(row(status)),
                "🔴 El estado `$status` no es vencido y el anillo rojo no debería encenderse",
            )
        }
    }

    @Test
    fun `la marca del anillo es el MISMO predicate de la fecha límite en rojo`() {
        // Una sola derivación alimenta dos lugares (anillo del riel y meta roja):
        // si algún día se vuelve a bifurcar, divergen y nadie lo ve.
        val overdue = row("overdue")
        assertTrue(isOverdueStatus(overdue.actividad.status))
        assertTrue(isOverdueRow(overdue) == isOverdueStatus(overdue.actividad.status))
    }

    @Test
    fun `la line del riel es la collar de 2dp y su centro sale de la geometria`() {
        assertEquals(Spacing.collar, AgendaRailLineWidth, "La línea del riel es 2dp (outlineVariant)")
        assertEquals(
            AgendaRailLineOffset + AgendaRailLineWidth / 2,
            AgendaRailCenter,
            "El centro del riel es el centro de la línea, no un número repetido a mano",
        )
    }

    @Test
    fun `offset del punto centrado en el riel sin inyeccion propia`() {
        assertEquals(
            -24.dp,
            agendaRailDotOffset(railLeading = 0.dp),
            "El núcleo de 10dp del punto queda centrado sobre la línea del riel (15dp del borde)",
        )
    }

    @Test
    fun `offset del punto dentro de la pila, el card entra 8dp y el punto sigue en el riel`() {
        assertEquals(-32.dp, agendaRailDotOffset(railLeading = AgendaStackInset))
    }

    @Test
    fun `offset del dot-contador de la pila, pill centrada en el riel`() {
        assertEquals(-23.dp, agendaStackPillOffset())
    }
}
