package com.parental.shared.core.network

import co.touchlab.kermit.Logger
import com.parental.shared.core.error.ApiResult
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException

const val NETWORK_ERROR_MESSAGE = "No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo."
const val UNEXPECTED_RESPONSE_MESSAGE = "El servidor devolvió una respuesta inesperada."

@PublishedApi
internal val apiLogger = Logger.withTag("Api")

/**
 * Único punto de manejo de errores de red:
 * - 2xx → [ApiResult.Success] con el body deserializado.
 * - no 2xx → [ApiResult.Error] con el mensaje del backend (`message` / `error`).
 * - excepción → [ApiResult.Error] con código 0 y un mensaje apto para el usuario;
 *   el detalle técnico (URL, causa) va solo al log.
 * La cancelación de corrutinas se relanza para no romper la concurrencia estructurada.
 */
suspend inline fun <reified T> safeApiCall(request: () -> HttpResponse): ApiResult<T> {
    return try {
        val response = request()
        if (response.status.isSuccess()) {
            ApiResult.Success(response.body<T>())
        } else {
            ApiResult.Error(response.status.value, response.errorMessage())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ContentConvertException) {
        apiLogger.e(e) { "Could not parse response" }
        ApiResult.Error(0, UNEXPECTED_RESPONSE_MESSAGE)
    } catch (e: SerializationException) {
        apiLogger.e(e) { "Could not parse response" }
        ApiResult.Error(0, UNEXPECTED_RESPONSE_MESSAGE)
    } catch (e: Exception) {
        apiLogger.e(e) { "Request failed" }
        ApiResult.Error(0, NETWORK_ERROR_MESSAGE)
    }
}

/**
 * Extrae el mensaje de error del body (formato NestJS: `message` puede ser
 * string o lista de strings de validación; si no, `error`). Si el body no es
 * JSON, usa la descripción del status HTTP.
 */
@PublishedApi
internal suspend fun HttpResponse.errorMessage(): String {
    val json = try {
        ApiJson.parseToJsonElement(bodyAsText()) as? JsonObject
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    val message = when (val raw = json?.get("message")) {
        is JsonPrimitive -> raw.contentOrNull
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .joinToString("\n")
            .ifBlank { null }
        else -> null
    }
    return message
        ?: (json?.get("error") as? JsonPrimitive)?.contentOrNull
        ?: status.description
}
