package com.parental.shared.feature.expenses.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.data.remote.ApiErrorBody
import com.parental.shared.feature.expenses.domain.model.ExpenseActionResponse
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.PaginatedExpensesDto
import com.parental.shared.feature.expenses.domain.model.RejectExpenseRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private suspend fun HttpResponse.toApiError(): ApiResult.Error {
    val body = runCatching { body<ApiErrorBody>() }.getOrNull()
    val message = body?.let { it.message ?: it.error } ?: status.description
    return ApiResult.Error(status.value, message, body?.code)
}

/**
 * El cuerpo del PATCH, CAMPO POR CAMPO.
 *
 * No se serializa la data class: el cliente Ktor tiene `explicitNulls` activo, así que
 * mandaría `null` en todos los campos que el usuario no tocó — y el backend los lee
 * como "poner en null" (no como "no tocar"), contra columnas NOT NULL. Además el
 * backend rechaza con 400 cualquier propiedad que no exista en su DTO
 * (`forbidNonWhitelisted`), y la contraparte del reparto la DERIVA el servidor.
 *
 * Es una función pura a propósito: el cuerpo que viaja es exactamente el que se
 * puede afirmar en un test.
 */
internal fun ExpenseUpdateRequest.toPatchBody(): JsonObject = buildJsonObject {
    concept?.let { put("concept", it) }
    amount?.let { put("amount", it) }
    categoryId?.let { put("categoryId", it) }
    expenseDate?.let { put("expenseDate", it) }
    description?.let { put("description", it) }
    splitRatioPayer?.let { put("splitRatioPayer", it) }
}

/**
 * Cliente de lectura y acciones de gastos.
 *
 * El contrato HTTP está fijado por S1/T28; este archivo no inventa endpoints.
 */
open class ExpensesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /expenses/bond/:bondId — lista paginada y filtrada de gastos.
     */
    open suspend fun listExpenses(
        token: String,
        bondId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        status: ExpenseStatus? = null,
        categoryId: String? = null,
        from: String? = null,
        to: String? = null,
    ): ApiResult<PaginatedExpensesDto> {
        return try {
            val response = client.get("$baseUrl/expenses/bond/$bondId") {
                header("Authorization", "Bearer $token")
                parameter("limit", limit)
                parameter("offset", offset)
                if (status != null) parameter("status", status.serialName())
                if (categoryId != null) parameter("categoryId", categoryId)
                if (from != null) parameter("from", from)
                if (to != null) parameter("to", to)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/expenses/bond/$bondId failed: ${e.message}")
        }
    }

    /**
     * GET /expenses/bond/:bondId/summary — saldos y conteos desde el punto de vista
     * del usuario autenticado.
     */
    open suspend fun getSummary(
        token: String,
        bondId: String,
    ): ApiResult<ExpenseSummaryDto> {
        return try {
            val response = client.get("$baseUrl/expenses/bond/$bondId/summary") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/expenses/bond/$bondId/summary failed: ${e.message}")
        }
    }

    /**
     * GET /expenses/:id — detalle de un gasto.
     */
    open suspend fun getExpense(
        token: String,
        id: String,
    ): ApiResult<ExpenseResponseDto> {
        return try {
            val response = client.get("$baseUrl/expenses/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/expenses/$id failed: ${e.message}")
        }
    }

    /**
     * PATCH /expenses/:id — edición de campos permitidos.
     */
    open suspend fun updateExpense(
        token: String,
        id: String,
        request: ExpenseUpdateRequest,
    ): ApiResult<ExpenseResponseDto> {
        return try {
            // El cuerpo se arma CAMPO POR CAMPO, no serializando la data class:
            // el cliente Ktor tiene `explicitNulls` activo, así que una data class
            // mandaría `null` en todos los campos que el usuario no tocó — y el
            // backend los leería como "poner en null" (no como "no tocar"), contra
            // columnas NOT NULL. Además el backend rechaza con 400 cualquier
            // propiedad que no exista en su DTO (`forbidNonWhitelisted`), y la
            // contraparte del reparto la DERIVA el servidor: mandarla es un 400.
            val body = request.toPatchBody()
            val response = client.patch("$baseUrl/expenses/$id") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "PATCH $baseUrl/expenses/$id failed: ${e.message}")
        }
    }

    /**
     * DELETE /expenses/:id — cancelar/borrar un gasto.
     */
    open suspend fun deleteExpense(
        token: String,
        id: String,
    ): ApiResult<Unit> {
        return try {
            val response = client.delete("$baseUrl/expenses/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "DELETE $baseUrl/expenses/$id failed: ${e.message}")
        }
    }

    /**
     * POST /expenses/:id/approve.
     */
    open suspend fun approveExpense(
        token: String,
        id: String,
    ): ApiResult<ExpenseActionResponse> {
        return try {
            val response = client.post("$baseUrl/expenses/$id/approve") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$id/approve failed: ${e.message}")
        }
    }

    /**
     * POST /expenses/:id/reject con motivo opcional.
     */
    open suspend fun rejectExpense(
        token: String,
        id: String,
        reason: String? = null,
    ): ApiResult<ExpenseActionResponse> {
        return try {
            val response = client.post("$baseUrl/expenses/$id/reject") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                if (reason != null) setBody(RejectExpenseRequest(reason))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$id/reject failed: ${e.message}")
        }
    }

    /**
     * POST /expenses/:id/pay.
     */
    open suspend fun payExpense(
        token: String,
        id: String,
    ): ApiResult<ExpenseActionResponse> {
        return try {
            val response = client.post("$baseUrl/expenses/$id/pay") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$id/pay failed: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

private fun ExpenseStatus.serialName(): String = when (this) {
    ExpenseStatus.PENDING -> "pending"
    ExpenseStatus.APPROVED -> "approved"
    ExpenseStatus.REJECTED -> "rejected"
    ExpenseStatus.PAID -> "paid"
}
