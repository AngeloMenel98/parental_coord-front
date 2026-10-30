package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S11 · A-5…A-7 — filtro de hijos client-side.
 *
 * El filtro es una función pura sobre la lista ya cargada: combina con la pestaña
 * actual y aplica unión sobre los hijos seleccionados.
 */
class ChildrenFilterDerivationTest {

    private fun actividad(
        id: String,
        type: String = "event",
        childrenIds: List<String> = emptyList(),
    ) = Actividad(
        id = id,
        title = "Actividad $id",
        type = type,
        status = "created",
        categoryId = "cat-1",
        criticality = "low",
        childrenIds = childrenIds,
    )

    @Test
    fun `seleccion vacia muestra todas las actividades de la pestaña`() {
        val all = listOf(
            actividad("a", childrenIds = listOf("c1")),
            actividad("b", childrenIds = listOf("c2")),
        )

        val filtered = filterActivities(all, ActivityTab.TODOS, emptySet())

        assertEquals(listOf("a", "b"), filtered.map { it.id })
    }

    @Test
    fun `un hijo seleccionado muestra solo sus actividades`() {
        val all = listOf(
            actividad("a", childrenIds = listOf("c1")),
            actividad("b", childrenIds = listOf("c2")),
            actividad("c", childrenIds = listOf("c1", "c2")),
        )

        val filtered = filterActivities(all, ActivityTab.TODOS, setOf("c1"))

        assertEquals(listOf("a", "c"), filtered.map { it.id })
    }

    @Test
    fun `dos hijos seleccionados muestran la union`() {
        val all = listOf(
            actividad("a", childrenIds = listOf("c1")),
            actividad("b", childrenIds = listOf("c2")),
            actividad("c", childrenIds = listOf("c3")),
        )

        val filtered = filterActivities(all, ActivityTab.TODOS, setOf("c1", "c2"))

        assertEquals(listOf("a", "b"), filtered.map { it.id })
    }

    @Test
    fun `un hijo sin actividades no muestra nada`() {
        val all = listOf(
            actividad("a", childrenIds = listOf("c1")),
        )

        val filtered = filterActivities(all, ActivityTab.TODOS, setOf("c2"))

        assertEquals(emptyList(), filtered)
    }

    @Test
    fun `el filtro de hijos combina con el filtro de pestaña`() {
        val all = listOf(
            actividad("ev-c1", type = "event", childrenIds = listOf("c1")),
            actividad("obl-c1", type = "obligation", childrenIds = listOf("c1")),
            actividad("ev-c2", type = "event", childrenIds = listOf("c2")),
        )

        val eventosC1 = filterActivities(all, ActivityTab.EVENTOS, setOf("c1"))

        assertEquals(listOf("ev-c1"), eventosC1.map { it.id })
    }

    @Test
    fun `los contadores de hijos respetan la pestaña activa`() {
        val all = listOf(
            actividad("ev-c1", type = "event", childrenIds = listOf("c1")),
            actividad("ev-c2", type = "event", childrenIds = listOf("c2")),
            actividad("obl-c1", type = "obligation", childrenIds = listOf("c1")),
        )

        val counts = childFilterCounts(all, ActivityTab.EVENTOS)

        assertEquals(2, counts[CHILD_FILTER_ALL])
        assertEquals(1, counts["c1"])
        assertEquals(1, counts["c2"])
    }

    @Test
    fun `el contador de Todos refleja la pestaña activa`() {
        val all = listOf(
            actividad("ev", type = "event"),
            actividad("obl", type = "obligation"),
        )

        val counts = childFilterCounts(all, ActivityTab.TODOS)

        assertEquals(2, counts[CHILD_FILTER_ALL])
    }
}
