package com.parental.shared.feature.expenses.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.data.remote.ApiErrorBody
import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import com.parental.shared.feature.expenses.domain.model.ExpenseActionResponse
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseSummaryDto
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.PaginatedExpensesDto
import com.parental.shared.feature.expenses.domain.model.RejectExpenseRequest
import com.parental.shared.feature.expenses.domain.model.attachments.ConvertExpenseAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.ConfirmAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentRes
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
 * El cuerpo del POST de alta, CAMPO POR CAMPO (S6/T27).
 *
 * Igual razón que [toPatchBody]: `explicitNulls` activo + `forbidNonWhitelisted`
 * en el backend. Se envía `splitRatioPayer` sólo cuando difiere del default del
 * servidor (50); `description` sólo cuando tiene valor. No se envía la
 * contraparte del reparto ni identificadores ajenos al DTO del backend.
 */
internal fun CreateExpenseRequest.toCreateBody(): JsonObject = buildJsonObject {
    put("concept", concept)
    put("amount", amount)
    put("categoryId", categoryId)
    put("expenseDate", expenseDate)
    description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
    if (splitRatioPayer != 50) put("splitRatioPayer", splitRatioPayer)
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
     * POST /expenses/{bondId} — alta de un gasto (S6/T27).
     */
    open suspend fun createExpense(
        token: String,
        bondId: String,
        request: CreateExpenseRequest,
    ): ApiResult<ExpenseResponseDto> {
        return try {
            val body = request.toCreateBody()
            val response = client.post("$baseUrl/expenses/$bondId") {
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
            ApiResult.Error(0, "POST $baseUrl/expenses/$bondId failed: ${e.message}")
        }
    }

    /** POST /expenses/:id/attachments/presign — URL temporal para el archivo del gasto. */
    open suspend fun presignExpenseAttachment(
        token: String,
        expenseId: String,
        request: PresignAttachmentReq,
    ): ApiResult<PresignAttachmentRes> {
        return try {
            val response = client.post("$baseUrl/expenses/$expenseId/attachments/presign") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) ApiResult.Success(response.body()) else response.toApiError()
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$expenseId/attachments/presign failed: ${e.message}")
        }
    }

    /** POST /expenses/:id/attachments/confirm — registra el objeto ya subido. */
    open suspend fun confirmExpenseAttachment(
        token: String,
        expenseId: String,
        request: ConfirmAttachmentReq,
    ): ApiResult<Unit> {
        return try {
            val response = client.post("$baseUrl/expenses/$expenseId/attachments/confirm") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit) else response.toApiError()
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$expenseId/attachments/confirm failed: ${e.message}")
        }
    }

    /** POST /expenses/:id/attachments/convert — vincula el objeto ya asociado a la actividad. */
    open suspend fun convertExpenseAttachment(
        token: String,
        expenseId: String,
        request: ConvertExpenseAttachmentReq,
    ): ApiResult<Unit> {
        return try {
            val response = client.post("$baseUrl/expenses/$expenseId/attachments/convert") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit) else response.toApiError()
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/expenses/$expenseId/attachments/convert failed: ${e.message}")
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
