package com.parental.shared.feature.notifications.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.notifications.domain.model.NotificationItem
import com.parental.shared.feature.notifications.domain.model.NotificationsPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
private data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val refEntityType: String,
    val refEntityId: String? = null,
    val bondId: String? = null,
    val isRead: Boolean,
    val createdAt: String,
) {
    fun toDomain() = NotificationItem(
        id = id,
        type = type,
        title = title,
        body = body,
        refEntityType = refEntityType,
        refEntityId = refEntityId,
        bondId = bondId,
        isRead = isRead,
        createdAt = createdAt,
    )
}

@Serializable
private data class NotificationsPageDto(
    val items: List<NotificationDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain() = NotificationsPage(
        items = items.map { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}

@Serializable
private data class UnreadCountDto(val unreadCount: Int = 0)

@Serializable
private data class MarkAllReadDto(val affected: Int = 0)

/** Cuerpo de error del backend (`{statusCode, message, error}`), igual en todos los endpoints. */
@Serializable
private data class NotificationApiErrorDto(
    val statusCode: Int? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Read path de notificaciones del usuario autenticado.
 *
 * Los cuatro endpoints son del contrato ya publicado por el backend:
 * `GET /notifications`, `GET /notifications/unread-count`,
 * `PATCH /notifications/{id}/read` y `PATCH /notifications/read-all`.
 *
 * `open` a propósito: los tests de ViewModel sustituyen esta clase por un fake
 * que sobreescribe los métodos (mismo patrón que `ActivitiesApi`/`BondsApi`).
 * El usuario NUNCA se envía: el backend lo toma del token.
 */
open class NotificationsApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    open suspend fun list(
        token: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): ApiResult<NotificationsPage> {
        return try {
            val response = client.get("$baseUrl/notifications") {
                header("Authorization", "Bearer $token")
                parameter("limit", limit)
                parameter("offset", offset)
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body<NotificationsPageDto>().toDomain())
            } else {
                ApiResult.Error(response.status.value, response.errorMessage())
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/notifications failed: ${e.message}")
        }
    }

    open suspend fun unreadCount(token: String): ApiResult<Int> {
        return try {
            val response = client.get("$baseUrl/notifications/unread-count") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body<UnreadCountDto>().unreadCount)
            } else {
                ApiResult.Error(response.status.value, response.errorMessage())
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/notifications/unread-count failed: ${e.message}")
        }
    }

    /**
     * `PATCH /notifications/{id}/read`. El backend responde `{id, isRead:true}`;
     * esta capa devuelve `Unit` porque el único dato útil (ya está leído) lo
     * conoce el llamante. Un 404 significa "no existe o es de otro usuario".
     */
    open suspend fun markRead(token: String, id: String): ApiResult<Unit> {
        return try {
            val response = client.patch("$baseUrl/notifications/$id/read") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(response.status.value, response.errorMessage())
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "PATCH $baseUrl/notifications/$id/read failed: ${e.message}")
        }
    }

    /** `PATCH /notifications/read-all` → nº de filas que cambiaron. */
    open suspend fun markAllRead(token: String): ApiResult<Int> {
        return try {
            val response = client.patch("$baseUrl/notifications/read-all") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body<MarkAllReadDto>().affected)
            } else {
                ApiResult.Error(response.status.value, response.errorMessage())
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "PATCH $baseUrl/notifications/read-all failed: ${e.message}")
        }
    }

    private suspend fun HttpResponse.errorMessage(): String {
        val body = runCatching { body<NotificationApiErrorDto>() }.getOrNull()
        return body?.let { it.message ?: it.error } ?: status.description
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
