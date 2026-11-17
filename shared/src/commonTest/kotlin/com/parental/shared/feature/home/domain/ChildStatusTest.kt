package com.parental.shared.feature.home.domain

import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.home.domain.model.ChildProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S5 · la carga vencida de cada hijo. */
class ChildStatusTest {

    private fun child(id: String, name: String) = ChildProfile(
        id = id,
        firstName = name,
        lastName = "Pérez",
        dateOfBirth = "2016-05-01",
    )

    private fun activity(
        id: String,
        status: String,
        childrenIds: List<String>,
    ) = Actividad(
        id = id,
        title = "Actividad $id",
        type = "obligation",
        status = status,
        categoryId = "cat-educacion",
        criticality = "high",
        childrenIds = childrenIds,
    )

    @Test
    fun `cuenta las vencidas de cada hijo`() {
        val emma = child("emma", "Emma")
        val tomas = child("tomas", "Tomás")
        val statuses = selectChildStatuses(
            children = listOf(emma, tomas),
            activities = listOf(
                activity("a", "overdue", listOf("emma")),
                activity("b", "overdue", listOf("emma")),
                activity("c", "overdue", listOf("tomas")),
            ),
        )

        assertEquals(2, statuses.first { it.child.id == "emma" }.overdue)
        assertEquals(1, statuses.first { it.child.id == "tomas" }.overdue)
    }

    @Test
    fun `un hijo sin vencidas queda en cero, que si es un dato`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma")),
            activities = listOf(activity("a", "assigned", listOf("emma"))),
        )

        assertEquals(0, statuses.single().overdue)
    }

    @Test
    fun `una actividad de varios hijos suma para todos`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma"), child("tomas", "Tomás")),
            activities = listOf(activity("a", "overdue", listOf("emma", "tomas"))),
        )

        assertTrue(statuses.all { it.overdue == 1 })
    }

    @Test
    fun `solo overdue cuenta como vencida`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma")),
            activities = listOf(
                activity("a", "overdue", listOf("emma")),
                activity("b", "done", listOf("emma")),
                activity("c", "cancelled", listOf("emma")),
                activity("d", "in_progress", listOf("emma")),
            ),
        )

        assertEquals(1, statuses.single().overdue)
    }

    @Test
    fun `una actividad sin hijos no suma a nadie`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma")),
            activities = listOf(activity("a", "overdue", emptyList())),
        )

        assertEquals(0, statuses.single().overdue)
    }

    @Test
    fun `sin lista devuelve vacio en vez de afirmar que todos estan al dia`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma")),
            activities = null,
        )

        assertTrue(
            statuses.isEmpty(),
            "Sin lista no se puede afirmar 'Al día': la sección degrada a nombres sin estado.",
        )
    }

    @Test
    fun `conserva el orden de los hijos`() {
        val statuses = selectChildStatuses(
            children = listOf(child("emma", "Emma"), child("tomas", "Tomás")),
            activities = emptyList(),
        )

        assertEquals(listOf("emma", "tomas"), statuses.map { it.child.id })
    }
}
