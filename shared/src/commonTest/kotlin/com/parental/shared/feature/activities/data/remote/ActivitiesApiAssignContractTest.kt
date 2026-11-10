package com.parental.shared.feature.activities.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T21 — contrato del cuerpo de `POST /activities/:id/assign` y
 * `POST /activities/:id/reassign`.
 *
 * No hay motor mock de Ktor en el repo, así que se prueba el builder puro.
 * La forma del cuerpo es idéntica en los dos verbos: la única diferencia
 * es el path.
 */
class ActivitiesApiAssignContractTest {

    private val api = ActivitiesApi(
        client = io.ktor.client.HttpClient { },
        baseUrl = "http://unused",
    )

    @Test
    fun `el cuerpo lleva assignedTo cuando hay destino`() {
        val body = api.assignBody("user-123")

        assertEquals(
            """{"assignedTo":"user-123"}""",
            body.toString(),
        )
    }

    @Test
    fun `el cuerpo es vacio cuando no hay destino`() {
        val body = api.assignBody(null)

        assertEquals("{}", body.toString())
    }

    @Test
    fun `el cuerpo nunca manda assignedTo null explicito`() {
        val body = api.assignBody(null)

        assertTrue(
            "assignedTo" !in body,
            "🔴 El cuerpo manda `assignedTo: null`: con `explicitNulls` activo el backend " +
                "rechazaría la propiedad que no declara.",
        )
    }
}
