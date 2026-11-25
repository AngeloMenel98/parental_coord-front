package com.parental.shared.feature.thirdparty.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.activities.data.remote.ApiErrorBody
import com.parental.shared.feature.thirdparty.domain.model.CreateThirdPartyAccessRequest
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyAccess
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyGrant
import com.parental.shared.feature.thirdparty.domain.model.UpdateThirdPartyAccessRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Traduce una respuesta fallida a [ApiResult.Error] preservando el código estable del back.
 *
 * Mismo criterio que `ActivitiesApi` y `ExpensesApi`: el mensaje del servidor es el que ve
 * la persona. Acá no es un detalle de estilo — el motivo por el que un alta no se pudo
 * completar (mail que no salió, conflicto de acceso, fecha inválida) sólo se puede
 * explicar bien con el texto del servidor.
 */
private suspend fun HttpResponse.toApiError(): ApiResult.Error {
    val body = runCatching { body<ApiErrorBody>() }.getOrNull()
    val message = body?.let { it.message ?: it.error } ?: status.description
    return ApiResult.Error(status.value, message, body?.code)
}

/**
 * El cuerpo del alta, CAMPO POR CAMPO.
 *
 * El cliente Ktor tiene `explicitNulls` activo, así que serializar la data class mandaría
 * `"note": null` cuando no hay nota. Se omite en vez de mandarlo nulo porque el DTO del
 * back declara `note?: string` y un `null` explícito es información que nadie pidió. Los
 * tres campos obligatorios viajan siempre; el email va recortado porque así lo normaliza
 * el propio DTO del servidor.
 *
 * Es una función pura a propósito: el cuerpo que viaja se puede afirmar en un test.
 */
internal fun CreateThirdPartyAccessRequest.toCreateBody(): JsonObject = buildJsonObject {
    put("email", email.trim())
    put("firstName", firstName.trim())
    put("lastName", lastName.trim())
    phone?.takeIf { it.isNotBlank() }?.let { put("phone", it.trim()) }
    document?.takeIf { it.isNotBlank() }?.let { put("document", it.trim()) }
    put("relationship", relationship)
    put("accessEnd", accessEnd.trim())
    note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
}

/**
 * El cuerpo del PATCH, CAMPO POR CAMPO.
 *
 * Misma razón que [toCreateBody], y una más: el back aplica **sólo** los campos presentes
 * (`dto.accessEnd !== undefined`), así que mandar `null` en los que no se tocaron borraría
 * el vencimiento. La nota sí se puede vaciar a propósito enviando `""`.
 */
internal fun UpdateThirdPartyAccessRequest.toPatchBody(): JsonObject = buildJsonObject {
    relationship?.let { put("relationship", it) }
    accessEnd?.let { put("accessEnd", it.trim()) }
    note?.let { put("note", it) }
}

/**
 * Cliente del acceso temporal, en las dos direcciones.
 *
 * - **Lectura del visitante** (T7–T13): [getMyGrant], sin `bondId` en la ruta.
 * - **Consola del progenitor dueño** (T15): [listForBond], [create], [update], [revoke].
 *
 * Es `open` con métodos `open`, igual que el resto de las APIs del proyecto, para que los
 * tests puedan sustituirlo sin red ni servidor. Ninguna ruta se inventa acá: las cinco
 * existen en el back con `JwtAuthGuard` y el servicio vuelve a resolver la tenencia en
 * cada request (el token no autoriza nada).
 */
open class ThirdPartiesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    /**
     * GET /third-parties/me — el grant propio: quién lo autorizó, la relación y la
     * vigencia. **Sin `bondId` en la ruta**: el backend resuelve el vínculo del token y
     * un terciario sólo puede tener un grant vivo por vínculo (índice único parcial de
     * T1). Pedirlo por ruta abriría la puerta a consultar el grant de otro.
     *
     * Un error NO es fatal para la pantalla: la tarjeta de permiso desaparece y la lista
     * de tareas se sigue pintando (ver `ThirdPartyViewModel.load`).
     */
    open suspend fun getMyGrant(token: String): ApiResult<ThirdPartyGrant> {
        return try {
            val response = client.get("$baseUrl/third-parties/me") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    /**
     * GET /bonds/:bondId/third-parties — los accesos que **este** progenitor otorgó.
     *
     * El filtro por dueño lo aplica el servidor (S7): el otro progenitor del vínculo no
     * los ve, y esta pantalla no puede pedirlos. El `status` que llega es el del servidor:
     * la UI no lo deduce de la fecha (un grant vencido que nadie barrió sigue llegando
     * `active` y se pinta como vigente hasta que el back lo marque).
     */
    open suspend fun listForBond(
        token: String,
        bondId: String,
    ): ApiResult<List<ThirdPartyAccess>> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/third-parties") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/bonds/$bondId/third-parties failed: ${e.message}")
        }
    }

    /**
     * POST /bonds/:bondId/third-parties — otorgar el acceso.
     *
     * El back crea la cuenta con contraseña provisoria si el email no existe y manda las
     * credenciales por mail (S1/D2). Errores que la pantalla muestra inline: el fallo del
     * envío (502, sin dejar cuenta ni acceso), **409** si esa persona ya tiene un acceso
     * vivo en el vínculo, **400** si `accessEnd` no es una fecha real o ya pasó.
     */
    open suspend fun create(
        token: String,
        bondId: String,
        request: CreateThirdPartyAccessRequest,
    ): ApiResult<ThirdPartyAccess> {
        return try {
            val response = client.post("$baseUrl/bonds/$bondId/third-parties") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request.toCreateBody())
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "POST $baseUrl/bonds/$bondId/third-parties failed: ${e.message}")
        }
    }

    /**
     * PATCH /third-parties/:id — relación, vencimiento y nota de un acceso **vigente**.
     *
     * El back responde 409 si el grant ya no está `active`: no se reactiva desde acá (D3).
     * Un grant ajeno responde 404, no 403, así que la pantalla nunca puede distinguir
     * "no existe" de "no es tuyo".
     */
    open suspend fun update(
        token: String,
        id: String,
        request: UpdateThirdPartyAccessRequest,
    ): ApiResult<ThirdPartyAccess> {
        return try {
            val response = client.patch("$baseUrl/third-parties/$id") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request.toPatchBody())
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "PATCH $baseUrl/third-parties/$id failed: ${e.message}")
        }
    }

    /**
     * DELETE /third-parties/:id — revocación inmediata.
     *
     * Devuelve el grant ya revocado (el back responde 200 con el DTO), así que la pantalla
     * puede marcarlo sin volver a pedir la lista. Idempotente sobre un grant ya revocado;
     * sobre uno vencido responde 409, porque no hay nada que revocar.
     */
    open suspend fun revoke(
        token: String,
        id: String,
    ): ApiResult<ThirdPartyAccess> {
        return try {
            val response = client.delete("$baseUrl/third-parties/$id") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                response.toApiError()
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "DELETE $baseUrl/third-parties/$id failed: ${e.message}")
        }
    }
}
