package com.parental.shared.feature.notifications.domain.model

/**
 * Una notificación tal como la devuelve `GET /api/notifications`.
 *
 * `createdAt` se guarda como el ISO-8601 **crudo** del backend: el formato es
 * responsabilidad de la capa de UI (`formatRelativeTime`), no del transporte.
 * `refEntityType`/`refEntityId` se conservan aunque esta slice no navegue: son
 * el contrato que necesitará el deep-link de la slice 3.
 */
data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val refEntityType: String,
    val refEntityId: String?,
    val bondId: String?,
    val isRead: Boolean,
    val createdAt: String,
)

/**
 * Página de notificaciones. `total` es el total que casa con los filtros, NO el
 * tamaño de [items] — por eso la UI decide "Cargar más" con
 * `items.size < total`, nunca con `items.size < limit`.
 */
data class NotificationsPage(
    val items: List<NotificationItem>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)
