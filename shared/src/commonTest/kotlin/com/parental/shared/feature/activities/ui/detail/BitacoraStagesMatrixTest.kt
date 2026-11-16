package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S2/S4/S10 · La MATRIZ del riel de estado, no un caso suelto.
 *
 * El PRD fija dos cosas que el riel anterior no podía expresar:
 * 1. El camino feliz tiene CINCO pasos con estos nombres exactos: Creada, Asignada, Asiste,
 *    En progreso, Finalizada. "Asiste" es su propio paso y `ASSISTING` lo prueba.
 * 2. Vencida y No asiste son TERMINALES: el trazo avanza hasta el paso donde ocurrió y ahí
 *    termina. Ya no se retrocede a "Asignada" ni se muestra "En progreso" como actual.
 *
 * El `mask` (cinco bits, en el orden del riel) es la forma barata de fijar la propiedad
 * central: el riel puede tener un hueco (`11010`) cuando `in_progress` llega sin marca de
 * confirmación, y un terminal SIEMPRE es un prefijo (`11110`) porque la línea no puede
 * avanzar más allá del punto donde se cortó.
 */
class BitacoraStagesMatrixTest {

    private fun detalle(
        status: String,
        assignedTo: String? = "u-maria",
        assignedConfirmed: Boolean = false,
        confirmedAt: String? = null,
        completedAt: String? = null,
        isPast: Boolean = false,
    ) = ActividadDetalle(
        id = "a1",
        title = "Clases de natación",
        type = "event",
        status = status,
        categoryId = "c1",
        criticality = "medium",
        createdBy = "u-carlos",
        createdAt = "2026-08-01T10:00:00Z",
        scheduledStart = "2026-08-05T16:30:00Z",
        assignedTo = assignedTo,
        assignedConfirmed = assignedConfirmed,
        confirmedAt = confirmedAt,
        completedAt = completedAt,
        isPast = isPast,
    )

    private fun nodos(actividad: ActividadDetalle) = stageRail(actividad).nodes

    /** Creada·Asignada·Asiste·EnProgreso·Finalizada, en ese orden. */
    private fun mask(actividad: ActividadDetalle): String =
        nodos(actividad).joinToString("") { if (it.reached) "1" else "0" }

    private fun terminal(actividad: ActividadDetalle) = nodos(actividad).singleOrNull { it.terminal != null }

    // ── Camino feliz ─────────────────────────────────────────────────

    @Test
    fun `S2 el riel siempre tiene los cinco pasos del camino feliz con su nombre exacto`() {
        val esperado = listOf("Creada", "Asignada", "Asiste", "En progreso", "Finalizada")
        val estados = listOf("created", "assigned", "assisting", "in_progress", "verify", "done")
        for (status in estados) {
            assertEquals(esperado, nodos(detalle(status)).map { it.stage.label }, "estado: $status")
        }
    }

    @Test
    fun `S2 assisting prueba Asiste sin depender de assignedConfirmed`() {
        // El servidor sólo pasa a `assisting` cuando el asignado confirma: el estado ES la
        // prueba. Exigir además `assignedConfirmed` dejaba un hueco en un paso ya ocurrido.
        assertEquals("11100", mask(detalle("assisting")))
        assertEquals("Asiste", nodos(detalle("assisting")).single { it.current }.stage.label)
    }

    @Test
    fun `S4 in_progress sin confirmacion conserva el hueco en Asiste`() {
        // A4: `in_progress` sin `confirmedAt` es un estado que el servidor puede mandar y el
        // riel no puede pintar "Asiste" como alcanzado; la etapa actual sí es la del estado.
        val actividad = detalle("in_progress", assignedConfirmed = false)
        assertEquals("11010", mask(actividad))
        assertEquals("En progreso", nodos(actividad).single { it.current }.stage.label)
    }

    @Test
    fun `S4 in_progress confirmado alcanza los cuatro primeros pasos`() {
        assertEquals("11110", mask(detalle("in_progress", assignedConfirmed = true)))
    }

    @Test
    fun `S3 verificar mantiene En progreso como actual y avisa que esta pendiente`() {
        // PRD §2: Verificar NO es un paso propio; el stepper sigue en el 4 con una indicación.
        val rail = stageRail(detalle("verify", assignedConfirmed = true))
        assertEquals("11110", rail.nodes.joinToString("") { if (it.reached) "1" else "0" })
        assertEquals("En progreso", rail.nodes.single { it.current }.stage.label)
        assertTrue(rail.verificationPending)
        assertNull(rail.terminal)
    }

    @Test
    fun `S3 fuera de Verificar no hay aviso de verificacion pendiente`() {
        assertFalse(stageRail(detalle("in_progress", assignedConfirmed = true)).verificationPending)
        assertFalse(stageRail(detalle("done", assignedConfirmed = true)).verificationPending)
    }

    @Test
    fun `S10 finalizada alcanza los cinco pasos`() {
        val actividad = detalle(
            status = "done",
            assignedConfirmed = true,
            completedAt = "2026-08-05T18:00:00Z",
        )
        assertEquals("11111", mask(actividad))
        assertEquals("Finalizada", nodos(actividad).single { it.current }.stage.label)
        assertNull(stageRail(actividad).terminal)
    }

    @Test
    fun `S6 done sin marca de confirmacion no rellena el hueco de Asiste`() {
        assertEquals("11011", mask(detalle("done", assignedConfirmed = false, confirmedAt = null)))
    }

    // ── Terminales negativos (PRD §3 y §5) ───────────────────────────

    @Test
    fun `S5 vencida estando asignada termina el trazo en el paso 2`() {
        // El ejemplo del PRD: ● ─ 🔴 con "Vencida" en el paso donde ocurrió.
        val actividad = detalle("overdue", assignedConfirmed = false)
        assertEquals("11000", mask(actividad))
        assertEquals(TerminalOutcome.Expired, terminal(actividad)?.terminal)
        assertEquals(1, stageRail(actividad).terminalIndex)
        assertTrue(nodos(actividad).none { it.current }, "un terminal no deja etapa actual")
    }

    @Test
    fun `S5 vencida en Verificar termina el trazo en el paso 4`() {
        // ● ─ ● ─ ● ─ 🔴: llegó a En progreso y ahí venció.
        val actividad = detalle("overdue", assignedConfirmed = true, isPast = true)
        assertEquals("11110", mask(actividad))
        assertEquals(3, stageRail(actividad).terminalIndex)
        assertEquals("Vencida", terminal(actividad)?.terminal?.label)
    }

    @Test
    fun `S5 vencida sin asignar termina el trazo en el paso 1`() {
        val actividad = detalle("overdue", assignedTo = null)
        assertEquals("10000", mask(actividad))
        assertEquals(0, stageRail(actividad).terminalIndex)
    }

    @Test
    fun `S5 no asiste termina el trazo en el paso 2 con su propio rotulo`() {
        val actividad = detalle("not_assisting")
        assertEquals("11000", mask(actividad))
        assertEquals(TerminalOutcome.Declined, terminal(actividad)?.terminal)
        assertEquals("No asiste", terminal(actividad)?.terminal?.label)
        assertEquals(1, stageRail(actividad).terminalIndex)
    }

    @Test
    fun `S5 cancelada tambien cierra el trazo donde ocurrio`() {
        val actividad = detalle("cancelled", assignedConfirmed = true, isPast = true)
        assertEquals("11110", mask(actividad))
        assertEquals(TerminalOutcome.Cancelled, terminal(actividad)?.terminal)
        assertEquals(3, stageRail(actividad).terminalIndex)
    }

    @Test
    fun `S5 los pasos posteriores al terminal no quedan alcanzados`() {
        for (status in listOf("overdue", "not_assisting", "cancelled")) {
            val rail = stageRail(detalle(status))
            val corte = rail.terminalIndex
            assertTrue(corte >= 0, "terminal sin índice: $status")
            rail.nodes.drop(corte + 1).forEach {
                assertFalse(it.reached, "posterior alcanzado en $status: ${it.stage.label}")
                assertFalse(it.current, "posterior actual en $status: ${it.stage.label}")
                assertNull(it.terminal, "terminal duplicado en $status")
            }
        }
    }

    @Test
    fun `S10 hay a lo sumo una etapa actual y el terminal la reemplaza`() {
        val estados = listOf(
            "created", "assigned", "assisting", "in_progress", "verify",
            "overdue", "done", "completed", "not_assisting", "cancelled", "WEIRD",
        )
        for (status in estados) {
            for (confirmed in listOf(false, true)) {
                for (past in listOf(false, true)) {
                    val rail = stageRail(detalle(status, assignedConfirmed = confirmed, isPast = past))
                    assertEquals(5, rail.nodes.size, "tamaño: $status/$confirmed/$past")
                    assertTrue(rail.nodes.first().reached, "Creada siempre: $status/$confirmed/$past")
                    if (rail.terminal != null) {
                        assertEquals(0, rail.nodes.count { it.current }, "terminal con actual: $status")
                        assertEquals(1, rail.nodes.count { it.terminal != null }, "un terminal: $status")
                    } else {
                        assertEquals(1, rail.nodes.count { it.current }, "una actual: $status/$confirmed/$past")
                        assertEquals(0, rail.nodes.count { it.terminal != null }, "sin terminal: $status")
                    }
                }
            }
        }
    }

    @Test
    fun `S6 un estado desconocido no asciende el riel`() {
        val actividad = detalle("WEIRD", assignedTo = null)
        assertEquals("10000", mask(actividad))
        assertEquals("Creada", nodos(actividad).single { it.current }.stage.label)
        assertNull(stageRail(actividad).terminal)
    }

    @Test
    fun `S6 un confirmedAt ilegible no alcanza Asiste`() {
        assertEquals("11000", mask(detalle("assigned", confirmedAt = "basura")))
    }
}
