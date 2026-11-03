package com.parental.shared.feature.activities.domain.model

import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T03 — `childrenIds` sobrevive a la deserialización y el resolver produce los nombres
 * que la tarjeta (T08) y la hoja de detalle (T09) consumirán.
 */
class ResolveChildNamesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `payload del backend decodifica childrenIds en Actividad`() {
        val payload = """
            {
                "id": "act-1",
                "title": "Consulta pediátrica",
                "type": "event",
                "status": "assigned",
                "categoryId": "cat-123",
                "criticality": "medium",
                "childrenIds": ["child-a", "child-b"],
                "assignedConfirmed": true
            }
        """.trimIndent()

        val activity = json.decodeFromString(Actividad.serializer(), payload)

        assertEquals(listOf("child-a", "child-b"), activity.childrenIds)
    }

    @Test
    fun `payload sin childrenIds decodifica como lista vacia`() {
        val payload = """
            {
                "id": "act-2",
                "title": "Evento escolar",
                "type": "event",
                "status": "created",
                "categoryId": "cat-456",
                "criticality": "low"
            }
        """.trimIndent()

        val activity = json.decodeFromString(Actividad.serializer(), payload)

        assertEquals(emptyList(), activity.childrenIds)
    }

    @Test
    fun `resolver sigue el orden de los ids`() {
        val children = listOf(
            VinculoChild(id = "c-2", firstName = "Lucía", lastName = "García"),
            VinculoChild(id = "c-1", firstName = "Mateo", lastName = "García"),
        )

        val names = resolveChildNames(children, listOf("c-1", "c-2"))

        assertEquals(listOf("Mateo García", "Lucía García"), names)
    }

    @Test
    fun `resolver omite ids desconocidos`() {
        val children = listOf(
            VinculoChild(id = "c-1", firstName = "Mateo", lastName = "García"),
        )

        val names = resolveChildNames(children, listOf("c-1", "c-desconocido", "c-1"))

        assertEquals(listOf("Mateo García", "Mateo García"), names)
    }

    @Test
    fun `resolver devuelve lista vacia si no hay ids`() {
        val children = listOf(
            VinculoChild(id = "c-1", firstName = "Mateo", lastName = "García"),
        )

        val names = resolveChildNames(children, emptyList())

        assertEquals(emptyList(), names)
    }

    @Test
    fun `resolver ignora partes del nombre en blanco`() {
        val children = listOf(
            VinculoChild(id = "c-1", firstName = "  Mateo  ", lastName = "  "),
            VinculoChild(id = "c-2", firstName = "", lastName = "García"),
            VinculoChild(id = "c-3", firstName = "   ", lastName = "   "),
        )

        val names = resolveChildNames(children, listOf("c-1", "c-2", "c-3"))

        // "c-3" no tiene NINGÚN nombre legible: se omite entero. Una etiqueta vacía en
        // la tarjeta es ruido, no información.
        assertEquals(listOf("Mateo", "García"), names)
    }
}
