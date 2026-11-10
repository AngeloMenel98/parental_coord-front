package com.parental.shared.feature.admin.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for `serializing collection of different types is not yet
 * supported` on `POST /admin/bonds`.
 *
 * The payload used to be a `mapOf(...)` mixing strings and a `List<String>`,
 * which infers `Map<String, Any>` and cannot be serialized.
 */
class CreateBondRequestTest {
    private val mariaId = "e7dbc9ff-1aad-45e2-95da-401a87765b84"
    private val testId = "63d810b9-2c6f-4328-87d5-ba7efe4cd273"

    @Test
    fun `serializes the body the backend expects`() {
        val request = CreateBondRequest(
            title = "Bond Test",
            agreementType = "formal",
            userIds = listOf(mariaId, testId),
        )

        val encoded = Json.encodeToString(CreateBondRequest.serializer(), request)
        val body = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("Bond Test", body.getValue("title").jsonPrimitive.content)
        assertEquals("formal", body.getValue("agreementType").jsonPrimitive.content)
        assertEquals(
            listOf(mariaId, testId),
            body.getValue("userIds").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `userIds stays a json array even with a single member`() {
        val encoded = Json.encodeToString(
            CreateBondRequest.serializer(),
            CreateBondRequest(title = "Bond Test", agreementType = "informal", userIds = listOf(mariaId)),
        )

        val userIds = Json.parseToJsonElement(encoded).jsonObject.getValue("userIds").jsonArray
        assertEquals(1, userIds.size)
        assertEquals(mariaId, userIds.single().jsonPrimitive.content)
    }
}
