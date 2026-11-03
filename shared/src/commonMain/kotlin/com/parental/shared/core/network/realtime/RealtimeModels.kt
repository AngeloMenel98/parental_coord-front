package com.parental.shared.core.network.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versión de protocolo aceptada; un frame con otra `v` se descarta. */
const val REALTIME_PROTOCOL_VERSION: Int = 1

private const val TYPE_ACTIVITY_CHANGED = "activity.changed"
private const val TYPE_NOTIFICATION_CREATED = "notification.created"
private const val TYPE_EXPENSE_CHANGED = "expense.changed"

/**
 * Todo frame que puede llegar por el canal de realtime.
 *
 * Un solo canal transporta varios tipos: quien consume DEBE ramificar por `type`.
 * Asumir que todo frame es un `activity.changed` es lo que hacía que un frame
 * ajeno terminara disparando un refetch de actividades.
 */
sealed interface RealtimeMessage {
    val v: Int
    val type: String
}

@Serializable
data class ActivityChangedMessage(
    @SerialName("v") override val v: Int,
    @SerialName("type") override val type: String,
    @SerialName("eventId") val eventId: String? = null,
    @SerialName("activityId") val activityId: String,
    @SerialName("bondId") val bondId: String,
    @SerialName("from") val from: String? = null,
    @SerialName("to") val to: String? = null,
) : RealtimeMessage

/**
 * Frame mínimo de notificación creada (slice 3).
 *
 * No trae `title`/`body` ni `userId`: el servidor quita la clave de ruteo antes
 * de escribir el frame. El cliente lo usa como señal ("tenés algo nuevo") y
 * relee por REST, que sigue siendo la fuente de verdad.
 */
@Serializable
data class NotificationCreatedMessage(
    @SerialName("v") override val v: Int,
    @SerialName("type") override val type: String,
    @SerialName("notificationId") val notificationId: String,
    @SerialName("notificationType") val notificationType: String,
    @SerialName("refEntityType") val refEntityType: String,
    @SerialName("refEntityId") val refEntityId: String? = null,
    @SerialName("occurredAt") val occurredAt: String? = null,
) : RealtimeMessage

/**
 * Frame `expense.changed` (S8/T26).
 *
 * Señal de refetch y NADA MÁS: el cliente no ramifica por el payload, sólo usa
 * `v`/`type` para aceptarlo y después relee por REST, que es la fuente de verdad
 * (el resumen y la lista se refrescan enteros). `expenseId` y `bondId` son
 * obligatorios a propósito: un frame sin ellos no identifica nada y se descarta,
 * igual que en el backend. El `actor` del contrato no se declara porque nadie lo
 * lee y `ignoreUnknownKeys` lo descarta.
 */
@Serializable
data class ExpenseChangedMessage(
    @SerialName("v") override val v: Int,
    @SerialName("type") override val type: String,
    @SerialName("eventId") val eventId: String? = null,
    @SerialName("expenseId") val expenseId: String,
    @SerialName("bondId") val bondId: String,
    @SerialName("status") val status: String? = null,
    @SerialName("occurredAt") val occurredAt: String? = null,
) : RealtimeMessage

private val realtimeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Sobre mínimo para decidir a qué tipo pertenece el frame crudo. */
@Serializable
private data class RealtimeEnvelope(
    @SerialName("v") val v: Int,
    @SerialName("type") val type: String,
)

/**
 * Decodifica un frame crudo por el `type` de su sobre.
 *
 * Devuelve `null` (el frame se descarta, sin romper la conexión) cuando:
 * el JSON no decodifica, la versión de protocolo no es [REALTIME_PROTOCOL_VERSION],
 * el `type` es desconocido, o el cuerpo no matchea la forma de ese tipo.
 */
fun parseRealtimeMessage(text: String): RealtimeMessage? {
    val envelope = runCatching { realtimeJson.decodeFromString<RealtimeEnvelope>(text) }.getOrNull()
        ?: return null
    if (envelope.v != REALTIME_PROTOCOL_VERSION) return null

    return runCatching {
        when (envelope.type) {
            TYPE_ACTIVITY_CHANGED -> realtimeJson.decodeFromString<ActivityChangedMessage>(text)
            TYPE_NOTIFICATION_CREATED -> realtimeJson.decodeFromString<NotificationCreatedMessage>(text)
            TYPE_EXPENSE_CHANGED -> realtimeJson.decodeFromString<ExpenseChangedMessage>(text)
            else -> null
        }
    }.getOrNull()
}
