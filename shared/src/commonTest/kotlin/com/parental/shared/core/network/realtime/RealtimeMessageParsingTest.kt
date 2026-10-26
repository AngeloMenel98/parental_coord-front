package com.parental.shared.core.network.realtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Discriminación por sobre (S6): un canal, varios tipos.
 *
 * El parseo decide con `type` y NUNCA deja pasar un frame que no sepa rutear,
 * porque un frame desconocido tratado como `activity.changed` terminaba
 * disparando un refetch de actividades.
 */
class RealtimeMessageParsingTest {
    private val activityJson = """
        {"v":1,"type":"activity.changed","eventId":"e1","activityId":"a1","bondId":"b1",
         "from":"assigned","to":"assisting","actor":{"type":"user","id":"u1"},
         "occurredAt":"2026-10-16T14:22:31.412Z"}
    """.trimIndent().replace("\n", "")

    /** Frame tal como lo escribe el gateway: sin `userId` (lo quita antes de enviar). */
    private val notificationJson = """
        {"v":1,"type":"notification.created","notificationId":"n1",
         "notificationType":"activity_assigned","refEntityType":"activity",
         "refEntityId":"a1","occurredAt":"2026-10-26T10:00:00.000Z"}
    """.trimIndent().replace("\n", "")

    @Test
    fun `parses an activity frame`() {
        val message = parseRealtimeMessage(activityJson)

        val activity = assertIs<ActivityChangedMessage>(message)
        assertEquals("a1", activity.activityId)
        assertEquals("b1", activity.bondId)
    }

    @Test
    fun `parses a notification frame`() {
        val message = parseRealtimeMessage(notificationJson)

        val notification = assertIs<NotificationCreatedMessage>(message)
        assertEquals("n1", notification.notificationId)
        assertEquals("activity_assigned", notification.notificationType)
        assertEquals("a1", notification.refEntityId)
    }

    @Test
    fun `a notification without a target entity keeps refEntityId null`() {
        val message = parseRealtimeMessage(
            """{"v":1,"type":"notification.created","notificationId":"n1","notificationType":"webhook_disabled","refEntityType":"webhook_subscription"}""",
        )

        assertNull(assertIs<NotificationCreatedMessage>(message).refEntityId)
    }

    @Test
    fun `drops an unknown frame type`() {
        assertNull(parseRealtimeMessage(activityJson.replace("activity.changed", "bond.deleted")))
    }

    @Test
    fun `drops a frame with another protocol version`() {
        assertNull(parseRealtimeMessage(activityJson.replace("\"v\":1", "\"v\":2")))
    }

    @Test
    fun `drops a notification frame without notificationId`() {
        assertNull(parseRealtimeMessage(notificationJson.replace("\"notificationId\":\"n1\",", "")))
    }

    @Test
    fun `drops a body that is not json`() {
        assertNull(parseRealtimeMessage("{not json"))
    }

    @Test
    fun `drops an unknown extra type without breaking the caller`() {
        assertNull(parseRealtimeMessage("""{"v":1,"type":"activity.teleported","bondId":"b1"}"""))
    }
}
