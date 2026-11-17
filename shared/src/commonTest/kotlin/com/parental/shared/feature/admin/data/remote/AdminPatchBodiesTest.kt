package com.parental.shared.feature.admin.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F1 · S1-S4 — los cuerpos que el admin manda al backend.
 *
 * Se prueban como JSON puro: la forma del cuerpo es el contrato, y un campo de
 * más (o un `""` donde va `null`) lo rompe en silencio.
 */
class AdminPatchBodiesTest {
    private val childId = "63d810b9-2c6f-4328-87d5-ba7efe4cd273"
    private val baseUrl = "http://localhost:3000/api"

    private fun rendered(body: kotlinx.serialization.json.JsonObject): String = body.toString()

    @Test
    fun `set active sends exactly the boolean`() {
        assertEquals("""{"isActive":false}""", rendered(buildSetActiveBody(false)))
        assertEquals("""{"isActive":true}""", rendered(buildSetActiveBody(true)))
    }

    @Test
    fun `child patch omits every field the admin did not touch`() {
        val body = buildChildPatchBody(
            firstName = null,
            lastName = null,
            dateOfBirth = null,
            clearDateOfBirth = false,
        )

        assertEquals("{}", rendered(body))
    }

    @Test
    fun `child patch sends only the changed fields`() {
        val body = Json.parseToJsonElement(
            rendered(
                buildChildPatchBody(
                    firstName = "Mateo",
                    lastName = null,
                    dateOfBirth = "2020-05-15",
                    clearDateOfBirth = false,
                ),
            ),
        ).jsonObject

        assertEquals("Mateo", body.getValue("firstName").jsonPrimitive.content)
        assertEquals("2020-05-15", body.getValue("dateOfBirth").jsonPrimitive.content)
        assertFalse(body.containsKey("lastName"))
    }

    @Test
    fun `clearing the date sends an explicit null and never an empty string`() {
        val body = buildChildPatchBody(
            firstName = null,
            lastName = null,
            dateOfBirth = "2020-05-15",
            clearDateOfBirth = true,
        )

        assertTrue(body.containsKey("dateOfBirth"))
        assertEquals(JsonNull, body.getValue("dateOfBirth"))
        assertEquals("""{"dateOfBirth":null}""", rendered(body))
    }

    @Test
    fun `update and delete share the same child path`() {
        assertEquals("$baseUrl/admin/children/$childId", adminChildPath(baseUrl, childId))
    }
}
