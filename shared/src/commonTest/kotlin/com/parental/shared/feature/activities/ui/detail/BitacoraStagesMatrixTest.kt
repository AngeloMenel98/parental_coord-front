package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * S3/S6 · La MATRIZ de estados del riel, no un caso suelto.
 *
 * Existe por dos defectos que la primera ronda de tests no atrapó y que la verificación
 * encontró:
 * 1. `assisting` no estaba en el mapa de estados y caía en "Creada".
 * 2. el modelo de índice único pintaba "Confirmada" en una actividad cerrada sin marca de
 *    confirmación.
 *
 * El modelo corregido acumula un CONJUNTO de etapas alcanzadas, y su propiedad central —que
 * el riel puede tener un hueco (`11011`) en vez de ser un prefijo— no la afirmaba ningún
 * test. El `mask` es la forma barata de fijarla: cinco bits, en el orden del riel.
 */
class BitacoraStagesMatrixTest {

    private fun detalle(
        status: String,
        assignedConfirmed: Boolean = false,
        confirmedAt: String? = null,
        completedAt: String? = null,
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
        assignedTo = "u-maria",
        assignedConfirmed = assignedConfirmed,
        confirmedAt = confirmedAt,
        completedAt = completedAt,
        isPast = false,
    )

    /** Creada·Asignada·Confirmada·EnProgreso·Finalizada, en ese orden. */
    private fun mask(actividad: ActividadDetalle): String =
        activityStages(actividad).joinToString("") { if (it.reached) "1" else "0" }

    @Test
    fun `S3 assisting sin confirmacion deja En progreso como actual y el hueco en Confirmada`() {
        val actividad = detalle("assisting")
        assertEquals("11010", mask(actividad))
        assertEquals("En progreso", activityStages(actividad).single { it.current }.stage.label)
        assertFalse(activityStages(actividad).single { it.stage.label == "Confirmada" }.reached)
    }

    @Test
    fun `S6 done sin confirmacion deja el hueco en Confirmada sin rellenar el riel`() {
        // El defecto original: esto daba "11111" y afirmaba una confirmación que nunca ocurrió.
        assertEquals("11011", mask(detalle("done")))
    }

    @Test
    fun `S3 confirmedAt valido alcanza Confirmada aunque assignedConfirmed sea falso`() {
        val actividad = detalle("assigned", confirmedAt = "2026-08-02T11:00:00Z")
        assertEquals("11100", mask(actividad))
        assertEquals("Confirmada", activityStages(actividad).single { it.current }.stage.label)
    }

    @Test
    fun `S6 un confirmedAt ilegible no alcanza Confirmada`() {
        assertEquals("11000", mask(detalle("assigned", confirmedAt = "basura")))
    }

    @Test
    fun `S3 cancelled y not_assisting se ubican en Asignada y el estado desconocido no asciende`() {
        assertEquals("Asignada", activityStages(detalle("cancelled")).single { it.current }.stage.label)
        assertEquals("Asignada", activityStages(detalle("not_assisting")).single { it.current }.stage.label)
        assertEquals("10000", mask(detalle("WEIRD")))
        assertEquals("Creada", activityStages(detalle("WEIRD")).single { it.current }.stage.label)
    }

    @Test
    fun `S3 el estado terminal alcanza Finalizada sin completedAt`() {
        assertEquals("11011", mask(detalle("completed")))
        assertEquals("Finalizada", activityStages(detalle("completed")).single { it.current }.stage.label)
    }
}
