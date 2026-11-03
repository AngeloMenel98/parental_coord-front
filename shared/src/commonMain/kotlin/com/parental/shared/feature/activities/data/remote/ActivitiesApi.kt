package com.parental.shared.feature.activities.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.ActivityReasonRequest
import com.parental.shared.feature.activities.domain.model.AssignActivityResponse
import com.parental.shared.feature.activities.domain.model.CancelActivityResponse
import com.parental.shared.feature.activities.domain.model.CompleteActivityResponse
import com.parental.shared.feature.activities.domain.model.ConfirmAssignmentResponse
import com.parental.shared.feature.activities.domain.model.CreateActividadRequest
import com.parental.shared.feature.activities.domain.model.CreatedActividad
import com.parental.shared.feature.activities.domain.model.DeclineActivityResponse
import com.parental.shared.feature.activities.domain.model.DeleteActivityResponse
import com.parental.shared.feature.activities.domain.model.ReassignActivityResponse
import com.parental.shared.feature.activities.domain.model.RestoreActivityResponse
import com.parental.shared.feature.activities.domain.model.UndoDeclineResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cuerpo de error del backend. `code` sólo existe si el endpoint lanza
 * `CodedException`; `HttpExceptionFilter` lo omite en el resto, de modo que
 * `null` aquí significa "este endpoint no codifica sus errores" y no un hueco.
 *
 * 🔴 `details` NO se deserializa a propósito: el backend lo mantiene como
 * `unknown` porque su forma depende del endpoint. Deserializarlo aquí ataría el
 * cliente a esa forma y un endpoint nuevo que añada un campo rompería el parseo
 * de TODOS los errores, no sólo los suyos. Los códigos que la UI necesita son
 * estables y bastan.
 */
@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
)

/**
 * Traduce una respuesta fallida a [ApiResult.Error] preservando el código estable.
 *
* Se extrae como función suelta y no en línea en cada endpoint por un motivo que
 * no es estética: los 5 endpoints de acción necesitan leer `code`, y copiar el
 * mismo `runCatching { … }.getOrNull()?.let { … }` cinco veces garantiza que una
 * de ellos se quede sin él. Un solo sitio donde el parseo puede equivocarse es
 * un sitio que se puede probar y corregir.
 *
 * Es `suspend` porque `body()` lo es; no se puede leer el cuerpo de una respuesta
 * fuera de una corrutina.
 */
private suspend fun io.ktor.client.statement.HttpResponse.toApiError(): ApiResult.Error {
    val body = runCatching { body<ApiErrorBody>() }.getOrNull()
    val message = body?.let { it.message ?: it.error }
        ?: status.description
    return ApiResult.Error(status.value, message, body?.code)
}

open class ActivitiesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /activities/bond/:bondId — lista de actividades del vínculo activo,
     * ordenadas scheduledStart → deadline → createdAt (nulls last).
     */
    open suspend fun listActividades(
        token: String,
        bondId: String,
    ): ApiResult<List<Actividad>> {
        return try {
            val response = client.get("$baseUrl/activities/bond/$bondId") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/bond/$bondId failed: ${e.message}")
        }
    }

    /**
     * GET /activities/:id — detalle completo de una actividad.
     */
    open suspend fun getActividad(token: String, id: String): ApiResult<ActividadDetalle> {
        return try {
            val response = client.get("$baseUrl/activities/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/$id failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/confirm — confirma la asignación (flag-only,
     * idempotente). Sin cuerpo. Errores: ApiErrorBody (message ?? error)
     * cuando el backend responde JSON; si no, status.description.
     */
    open suspend fun confirmAssignment(
        token: String,
        id: String,
    ): ApiResult<ConfirmAssignmentResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/confirm") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/confirm failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:bondId/create — crea una actividad nueva.
     */
    open suspend fun createActividad(
        token: String,
        bondId: String,
        request: CreateActividadRequest,
    ): ApiResult<CreatedActividad> {
        return try {
            val response = client.post("$baseUrl/activities/$bondId/create") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$bondId/create failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/complete — marca la actividad como completada.
     */
    open suspend fun completeActivity(
        token: String,
        id: String,
    ): ApiResult<CompleteActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/complete") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/complete failed: ${e.message}")
        }
    }

    /**
     * Cuerpo de POST /activities/:id/assign y de POST /activities/:id/reassign (T21).
     *
     * Se construye CAMPO POR CAMPO: el cliente Ktor tiene `explicitNulls` activo, así
     * que una data class con `assignedTo = null` mandaría `"assignedTo": null` en vez
     * de omitir la clave. El backend rechaza cualquier propiedad que no declare, por
     * eso cuando no hay destino se envía `{}` y cuando hay destino se envía
     * `{ "assignedTo": "<userId>" }`.
     */
    internal fun assignBody(assignedTo: String?): JsonObject = buildJsonObject {
        assignedTo?.let { put("assignedTo", it) }
    }

    /**
     * POST /activities/:id/assign — "Asignar" (T21).
     */
    open suspend fun assignActivity(
        token: String,
        id: String,
        assignedTo: String?,
    ): ApiResult<AssignActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/assign") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(assignBody(assignedTo))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/assign failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/reassign — "Reasignar" (T21).
     *
     * Misma disciplina de cuerpo que [assignActivity]: campo por campo, sin
     * `null` explícito, para que el backend no reciba propiedades que no declara.
     */
    open suspend fun reassignActivity(
        token: String,
        id: String,
        assignedTo: String?,
    ): ApiResult<ReassignActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/reassign") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(assignBody(assignedTo))
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/reassign failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Acciones de swipe (R7)
    //
    // Los cinco verbos son deliberadamente post-hoc: el backend responde 404 si
    // la fila no existe (borrada) y 403 si el rol no vale. En los cinco casos el
    // fallo NO es un error de red, así que el `catch` devuelve `code = 0` sólo
    // para excepciones de transporte — nunca para una respuesta HTTP.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * DELETE /activities/:id — "Eliminar" (A4 · borrado LÓGICO).
     *
     * El servidor pone `deleted_at` y nada más: no hay purga, ni retención de 30
     * días, ni cascada (ruling A4). La respuesta trae `deletedAt` para que la UI
     * pueda mostrar "Deshacer" durante la ventana de 5000 ms.
     */
    open suspend fun deleteActivity(
        token: String,
        id: String,
    ): ApiResult<DeleteActivityResponse> {
        return try {
            val response = client.delete("$baseUrl/activities/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "DELETE $baseUrl/activities/$id failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/restore — deshace "Eliminar".
     *
     * La ventana de 5000 ms la posee el SERVIDOR (R2), que es el único reloj que
     * importa: el del móvil puede ir adelantado, y el del servidor no. Un 409 con
     * `ACTIVITY_UNDO_WINDOW_EXPIRED` significa que la fila sigue borrada y hay que
     * refetchear, no que se puede reintentar.
     */
    open suspend fun restoreActivity(
        token: String,
        id: String,
    ): ApiResult<RestoreActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/restore") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/restore failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/cancel — "Cancelar". A1 · el motivo es obligatorio y
     * lo valida el servidor (3–200) devolviendo 422 `VALIDATION_FAILED`.
     */
    open suspend fun cancelActivity(
        token: String,
        id: String,
        request: ActivityReasonRequest,
    ): ApiResult<CancelActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/cancel") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/cancel failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/decline — "No asistir". A1 · motivo obligatorio.
     * Sólo el asignado (R3); el resto recibe 403.
     */
    open suspend fun declineActivity(
        token: String,
        id: String,
        request: ActivityReasonRequest,
    ): ApiResult<DeclineActivityResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/decline") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/decline failed: ${e.message}")
        }
    }

    /**
     * POST /activities/:id/undo-decline — "Cambié de opinión" (R6 · 5000 ms).
     *
     * El estado previo lo reconstruye el servidor desde `audit_log`, así que este
     * endpoint NO lleva cuerpo: mandar un `status` de vuelta sería permitir que el
     * cliente eligiera a qué estado retroceder.
     */
    open suspend fun undoDeclineActivity(
        token: String,
        id: String,
    ): ApiResult<UndoDeclineResponse> {
        return try {
            val response = client.post("$baseUrl/activities/$id/undo-decline") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$id/undo-decline failed: ${e.message}")
        }
    }

    // --- Attachments ---
    open suspend fun presignAttachment(
        token: String,
        activityId: String,
        req: com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentReq,
    ): ApiResult<com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentRes> {
        return try {
            val response = client.post("$baseUrl/activities/$activityId/attachments/presign") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$activityId/attachments/presign failed: ${e.message}")
        }
    }

    open suspend fun confirmAttachment(
        token: String,
        activityId: String,
        req: com.parental.shared.feature.activities.domain.model.attachments.ConfirmAttachmentReq,
    ): ApiResult<com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto> {
        return try {
            val response = client.post("$baseUrl/activities/$activityId/attachments/confirm") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$activityId/attachments/confirm failed: ${e.message}")
        }
    }

    open suspend fun listAttachments(
        token: String,
        activityId: String,
    ): ApiResult<List<com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto>> {
        return try {
            val response = client.get("$baseUrl/activities/$activityId/attachments") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/$activityId/attachments failed: ${e.message}")
        }
    }

    open suspend fun renameAttachment(
        token: String,
        activityId: String,
        attachmentId: String,
        req: com.parental.shared.feature.activities.domain.model.attachments.RenameAttachmentReq,
    ): ApiResult<com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto> {
        return try {
            val response = client.patch("$baseUrl/activities/$activityId/attachments/$attachmentId") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "PATCH $baseUrl/activities/$activityId/attachments/$attachmentId failed: ${e.message}")
        }
    }

    open suspend fun replaceAttachment(
        token: String,
        activityId: String,
        attachmentId: String,
        req: com.parental.shared.feature.activities.domain.model.attachments.ReplaceAttachmentReq,
    ): ApiResult<com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto> {
        return try {
            val response = client.post("$baseUrl/activities/$activityId/attachments/$attachmentId/replace") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/activities/$activityId/attachments/$attachmentId/replace failed: ${e.message}")
        }
    }

    open suspend fun deleteAttachment(
        token: String,
        activityId: String,
        attachmentId: String,
    ): ApiResult<Unit> {
        return try {
            val response = client.delete("$baseUrl/activities/$activityId/attachments/$attachmentId") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "DELETE $baseUrl/activities/$activityId/attachments/$attachmentId failed: ${e.message}")
        }
    }
}
