package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S7/S8 · La ÚNICA etiqueta accesible de la tarjeta anuncia también el asignatario
 * y los hijos, para que TalkBack no dependa de nodos sueltos dentro del cuerpo.
 */
class ActivityCardSemanticLabelTest {

    private val zone = TimeZone.currentSystemDefault()

    private fun row(actividad: Actividad) = TimelineRow(
        actividad = actividad,
        scheduledStart = null,
        localStart = null,
        deadline = null,
    )

    @Test
    fun `etiqueta conserva los campos anteriores cuando no hay asignatario ni hijos`() {
        val actividad = Actividad(
            id = "a-0",
            title = "Entrega de documentos",
            type = "obligation",
            status = "assigned",
            categoryId = "cat-1",
            criticality = "high",
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
        )

        assertTrue("Entrega de documentos" in label, label)
        assertTrue("Obligación" in label, label)
        assertTrue("Asignada" in label, label)
        assertFalse("Asignada a:" in label, label)
        assertFalse("Para:" in label, label)
    }

    @Test
    fun `etiqueta incluye el asignatario resuelto`() {
        val actividad = Actividad(
            id = "a-1",
            title = "Clases de natación",
            type = "event",
            status = "assigned",
            categoryId = "cat-1",
            criticality = "medium",
            assignedTo = "u-1",
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
            memberNames = mapOf("u-1" to "María G."),
            children = emptyList(),
        )

        assertTrue(
            "Asignada a: María G." in label,
            "🔴 La etiqueta no anuncia el asignatario resuelto: $label",
        )
    }

    @Test
    fun `etiqueta omite asignatario si no hay mapping`() {
        val actividad = Actividad(
            id = "a-2",
            title = "Reunión escolar",
            type = "event",
            status = "assigned",
            categoryId = "cat-1",
            criticality = "medium",
            assignedTo = "u-desconocido",
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
            memberNames = emptyMap(),
            children = emptyList(),
        )

        assertFalse(
            "Asignada a" in label,
            "🔴 La etiqueta anuncia un asignatario sin nombre resuelto: $label",
        )
    }

    @Test
    fun `etiqueta incluye los nombres de los hijos`() {
        val children = listOf(
            VinculoChild(id = "c-1", firstName = "Mateo", lastName = "García"),
            VinculoChild(id = "c-2", firstName = "Lucía", lastName = "García"),
        )
        val actividad = Actividad(
            id = "a-3",
            title = "Consulta pediátrica",
            type = "obligation",
            status = "created",
            categoryId = "cat-1",
            criticality = "low",
            childrenIds = listOf("c-1", "c-2"),
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
            memberNames = emptyMap(),
            children = children,
        )

        assertTrue(
            "Para: Mateo García, Lucía García" in label,
            "🔴 La etiqueta no anuncia los nombres de los hijos: $label",
        )
    }

    @Test
    fun `etiqueta omite hijos si no hay ids resueltos`() {
        val actividad = Actividad(
            id = "a-4",
            title = "Evento escolar",
            type = "event",
            status = "created",
            categoryId = "cat-1",
            criticality = "low",
            childrenIds = emptyList(),
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
            memberNames = emptyMap(),
            children = emptyList(),
        )

        assertFalse(
            "Para:" in label,
            "🔴 La etiqueta anuncia hijos cuando no hay ninguno: $label",
        )
    }

    @Test
    fun `etiqueta omite hijos si los ids no se resuelven a nombres`() {
        val actividad = Actividad(
            id = "a-5",
            title = "Evento escolar",
            type = "event",
            status = "created",
            categoryId = "cat-1",
            criticality = "low",
            childrenIds = listOf("c-desconocido"),
        )
        val label = activitySemanticLabel(
            row = row(actividad),
            info = null,
            zone = zone,
            memberNames = emptyMap(),
            children = emptyList(),
        )

        assertFalse(
            "Para:" in label,
            "🔴 La etiqueta anuncia un hijo cuyo nombre no se pudo resolver: $label",
        )
    }
}
