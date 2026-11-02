package com.parental.shared.feature.expenses.data.remote

import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.RejectExpenseRequest
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
    fun `los estados se mapean a las query strings del backend`() {
        assertEquals("pending", ExpenseStatus.PENDING.queryValue())
        assertEquals("approved", ExpenseStatus.APPROVED.queryValue())
        assertEquals("rejected", ExpenseStatus.REJECTED.queryValue())
        assertEquals("paid", ExpenseStatus.PAID.queryValue())
    }
}

private fun ExpenseStatus.queryValue(): String = when (this) {
    ExpenseStatus.PENDING -> "pending"
    ExpenseStatus.APPROVED -> "approved"
    ExpenseStatus.REJECTED -> "rejected"
    ExpenseStatus.PAID -> "paid"
}
