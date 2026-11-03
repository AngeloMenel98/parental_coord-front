package com.parental.shared.feature.expenses.data.remote

import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.RejectExpenseRequest
import com.parental.shared.feature.expenses.domain.model.attachments.ConvertExpenseAttachmentReq
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato del cliente HTTP de gastos (S1/T28).
 *
 * No hay motor mock de Ktor en el repo, así que este test fija la forma de los
 * cuerpos de request y los valores por omisión que el ViewModel va a usar.
 */
class ExpensesApiContractTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `el tamano de pagina por defecto es 20`() {
        assertEquals(20, ExpensesApi.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun `el cuerpo del PATCH lleva SOLO los campos editados`() {
        val request = ExpenseUpdateRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
            description = "Nota",
            splitRatioPayer = 60,
        )

        val body = request.toPatchBody()

        assertEquals(
            """
                {"concept":"Cuota","amount":1500.0,"categoryId":"cat-2","expenseDate":"2026-04-01","description":"Nota","splitRatioPayer":60}
            """.trimIndent().filter { !it.isWhitespace() },
            body.toString().filter { !it.isWhitespace() },
        )
    }

    @Test
    fun `el cuerpo del PATCH omite los campos nulos`() {
        // El cliente Ktor serializa los null (tiene `explicitNulls` activo): si el
        // cuerpo los llevara, el backend los leería como "poner en null" contra
        // columnas NOT NULL. Sólo viaja lo que el usuario cambió.
        val request = ExpenseUpdateRequest(concept = "Solo concepto")

        val body = request.toPatchBody()

        assertEquals(
            """{"concept":"Solo concepto"}""",
            body.toString(),
        )
        assertTrue(
            "amount" !in body,
            "🔴 El cuerpo del PATCH manda un campo que el usuario no tocó: el backend lo " +
                "lee como \"poner en null\".",
        )
    }

    @Test
    fun `un PATCH sin cambios viaja vacio`() {
        // El backend responde 400 "Nothing to update" ante un PATCH vacío: es un
        // error del cliente, no un no-op silencioso.
        assertEquals("{}", ExpenseUpdateRequest().toPatchBody().toString())
    }

    @Test
    fun `el cuerpo del PATCH no lleva la contraparte del reparto`() {
        // `splitRatioOther` lo DERIVA el servidor (100 - pagador) y su DTO rechaza
        // con 400 cualquier propiedad que no exista (`forbidNonWhitelisted`).
        val body = ExpenseUpdateRequest(splitRatioPayer = 60).toPatchBody()

        assertTrue(
            "splitRatioOther" !in body,
            "🔴 El PATCH manda `splitRatioOther`: el backend lo deriva y rechaza la " +
                "propiedad con 400, así que la edición fallaría siempre.",
        )
    }

    @Test
    fun `RejectExpenseRequest serializa el motivo`() {
        val request = RejectExpenseRequest(reason = "Falta comprobante")

        val encoded = json.encodeToString(RejectExpenseRequest.serializer(), request)

        assertEquals("""{"reason":"Falta comprobante"}""", encoded)
    }

    @Test
    fun `la conversion de adjunto manda solo el id del adjunto de actividad`() {
        val request = ConvertExpenseAttachmentReq(activityAttachmentId = "activity-attachment-1")

        assertEquals(
            """{"activityAttachmentId":"activity-attachment-1"}""",
            json.encodeToString(ConvertExpenseAttachmentReq.serializer(), request),
        )
    }

    @Test
    fun `los estados se mapean a las query strings del backend`() {
        assertEquals("pending", ExpenseStatus.PENDING.queryValue())
        assertEquals("approved", ExpenseStatus.APPROVED.queryValue())
        assertEquals("rejected", ExpenseStatus.REJECTED.queryValue())
        assertEquals("paid", ExpenseStatus.PAID.queryValue())
    }

    // ── S6 · cuerpo de POST /expenses/{bondId} ─────────────────────────────

    @Test
    fun `el cuerpo del POST lleva los campos obligatorios`() {
        val request = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
        )

        val body = request.toCreateBody()

        assertEquals(
            """
                {"concept":"Cuota","amount":1500.0,"categoryId":"cat-2","expenseDate":"2026-04-01"}
            """.trimIndent().filter { !it.isWhitespace() },
            body.toString().filter { !it.isWhitespace() },
        )
    }

    @Test
    fun `el cuerpo del POST omite la descripcion cuando esta vacia`() {
        val request = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
            description = "  ",
        )

        val body = request.toCreateBody()

        assertTrue(
            "description" !in body,
            "🔴 El POST manda `description` vacía: el backend acepta null, pero no hay " +
                "razón para enviar un campo que el usuario no completó.",
        )
    }

    @Test
    fun `el cuerpo del POST incluye la descripcion cuando tiene valor`() {
        val request = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
            description = "Marzo",
        )

        val body = request.toCreateBody()

        assertEquals("\"Marzo\"", body["description"].toString())
    }

    @Test
    fun `el split por omision no viaja porque el servidor defaultea a 50`() {
        val request = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
        )

        val body = request.toCreateBody()

        assertTrue(
            "splitRatioPayer" !in body,
            "🔴 El POST manda `splitRatioPayer` igual al default del servidor. Como el " +
                "backend defaultea a 50, no hace falta enviarlo y así se reduce la superficie de error.",
        )
    }

    @Test
    fun `el cuerpo del POST incluye splitRatioPayer cuando difiere del default`() {
        val request = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
            splitRatioPayer = 70,
        )

        val body = request.toCreateBody()

        assertEquals("70", body["splitRatioPayer"].toString())
    }

    @Test
    fun `el cuerpo del POST no manda la contraparte del reparto`() {
        val body = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
            splitRatioPayer = 60,
        ).toCreateBody()

        assertTrue(
            "splitRatioOther" !in body,
            "🔴 El POST manda `splitRatioOther`: el backend lo deriva y rechaza la " +
                "propiedad con 400, así que la creación fallaría siempre.",
        )
    }

    @Test
    fun `el cuerpo del POST no inventa childId ni activityId`() {
        val body = CreateExpenseRequest(
            concept = "Cuota",
            amount = 1500.0,
            categoryId = "cat-2",
            expenseDate = "2026-04-01",
        ).toCreateBody()

        assertTrue("childId" !in body, "🔴 El POST manda `childId`, que el backend no conoce.")
        assertTrue("activityId" !in body, "🔴 El POST manda `activityId`, que el backend no conoce.")
    }
}

private fun ExpenseStatus.queryValue(): String = when (this) {
    ExpenseStatus.PENDING -> "pending"
    ExpenseStatus.APPROVED -> "approved"
    ExpenseStatus.REJECTED -> "rejected"
    ExpenseStatus.PAID -> "paid"
}
