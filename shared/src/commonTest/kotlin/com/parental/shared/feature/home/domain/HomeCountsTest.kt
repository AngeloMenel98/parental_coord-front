package com.parental.shared.feature.home.domain

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T20 · Los conteos del Home.
 *
 * R11: los dos conteos se calculan en el cliente sobre la lista del vínculo. La
 * función es PURA y testeable: la lista entra, los números salen, y `null` significa
 * "no se pudo calcular" — nunca `0` de mentira.
 */
class HomeCountsTest {

    private fun activity(
        status: String,
        assignedTo: String? = "u1",
        id: String = "a-${status}-$assignedTo",
    ) = Actividad(
        id = id,
        title = "Actividad",
        type = "event",
        status = status,
        categoryId = "cat-1",
        criticality = "medium",
        assignedTo = assignedTo,
    )

    @Test
    fun `sin lista los dos conteos quedan sin dato`() {
        val counts = computeHomeCounts(activities = null, currentUserId = "u1")

        assertNull(counts.assignedToMe)
        assertNull(counts.overdue)
    }

    @Test
    fun `asignadas cuenta las abiertas del usuario`() {
        val activities = listOf(
            activity("assigned", assignedTo = "u1"),
            activity("assisting", assignedTo = "u1"),
            activity("in_progress", assignedTo = "u1"),
        )

        val counts = computeHomeCounts(activities, currentUserId = "u1")

        assertEquals(3, counts.assignedToMe)
    }

    @Test
    fun `asignadas no cuenta las de otro miembro`() {
        val activities = listOf(
            activity("assigned", assignedTo = "u1"),
            activity("assigned", assignedTo = "u2"),
        )

        val counts = computeHomeCounts(activities, currentUserId = "u1")

        assertEquals(1, counts.assignedToMe)
    }

    @Test
    fun `los estados terminados no cuentan como asignadas`() {
        val activities = listOf(
            activity("done", assignedTo = "u1"),
            activity("cancelled", assignedTo = "u1"),
            activity("not_assisting", assignedTo = "u1"),
        )

        val counts = computeHomeCounts(activities, currentUserId = "u1")

        // `not_assisting` NO es terminal: el que la rechazado la tiene asignada y
        // sigue siendo carga de alguien — es el estado desde el que se reasigna.
        assertEquals(1, counts.assignedToMe)
    }

    @Test
    fun `vencidas cuenta las overdue del usuario`() {
        val activities = listOf(
            activity("overdue", assignedTo = "u1"),
            activity("overdue", assignedTo = "u2"),
            activity("assigned", assignedTo = "u1"),
        )

        val counts = computeHomeCounts(activities, currentUserId = "u1")

        assertEquals(1, counts.overdue)
    }

    @Test
    fun `sin user id los conteos quedan sin dato y no en cero`() {
        val activities = listOf(activity("assigned", assignedTo = "u1"))

        val counts = computeHomeCounts(activities, currentUserId = null)

        assertNull(counts.assignedToMe)
        assertNull(counts.overdue)
    }

    @Test
    fun `una lista vacia es cero y no ausencia de dato`() {
        val counts = computeHomeCounts(activities = emptyList(), currentUserId = "u1")

        assertEquals(0, counts.assignedToMe)
        assertEquals(0, counts.overdue)
    }
}
