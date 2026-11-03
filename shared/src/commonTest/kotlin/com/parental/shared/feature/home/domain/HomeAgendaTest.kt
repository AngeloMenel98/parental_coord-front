package com.parental.shared.feature.home.domain

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.activities.domain.model.Actividad
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S2 · la agenda de hoy.
 *
 * Todo se juega con una fecha y una zona fijas: una prueba de "hoy" que dependa del
 * reloj no prueba nada, cambia de resultado cada medianoche.
 */
class HomeAgendaTest {

    private val today = LocalDate(2026, 11, 3)
    private val utc = TimeZone.UTC

    private fun activity(
        id: String,
        title: String = "Actividad $id",
        status: String = "assigned",
        scheduledStart: String? = null,
        categoryId: String = "cat-salud",
    ) = Actividad(
        id = id,
        title = title,
        type = "event",
        status = status,
        categoryId = categoryId,
        criticality = "medium",
        scheduledStart = scheduledStart,
    )

    private val categories = mapOf(
        "cat-salud" to CategoryInfo(name = "Salud", colorHex = null, emoji = null),
    )

    @Test
    fun `sin lista devuelve null, no una agenda vacia`() {
        assertNull(
            selectTodayAgenda(null, today, utc, categories),
            "Sin lista no se sabe nada: null es un hueco, no 'no hay actividades'.",
        )
    }

    @Test
    fun `incluye solo las actividades del dia pedido`() {
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("ayer", scheduledStart = "2026-11-02T09:00:00Z"),
                activity("hoy-manana", scheduledStart = "2026-11-03T08:30:00Z"),
                activity("hoy-tarde", scheduledStart = "2026-11-03T17:00:00Z"),
                activity("manana", scheduledStart = "2026-11-04T08:00:00Z"),
            ),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals(listOf("hoy-manana", "hoy-tarde"), agenda?.activities?.map { it.id })
        assertEquals(2, agenda?.total)
    }

    @Test
    fun `ordena por instante y no por el orden de la lista`() {
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("tarde", scheduledStart = "2026-11-03T17:00:00Z"),
                activity("temprano", scheduledStart = "2026-11-03T08:30:00Z"),
            ),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals(listOf("temprano", "tarde"), agenda?.activities?.map { it.id })
    }

    @Test
    fun `muestra dos como maximo y cuenta el resto en total`() {
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("a", scheduledStart = "2026-11-03T08:00:00Z"),
                activity("b", scheduledStart = "2026-11-03T09:00:00Z"),
                activity("c", scheduledStart = "2026-11-03T10:00:00Z"),
            ),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals(listOf("a", "b"), agenda?.activities?.map { it.id })
        assertEquals(3, agenda?.total, "El total cuenta todas las de hoy, no las mostradas.")
    }

    @Test
    fun `las actividades sin fecha no son de hoy`() {
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("sin-fecha", scheduledStart = null),
                activity("con-fecha", scheduledStart = "2026-11-03T09:00:00Z"),
            ),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals(listOf("con-fecha"), agenda?.activities?.map { it.id })
        assertEquals(1, agenda?.total)
    }

    @Test
    fun `una fecha ilegible no rompe la agenda`() {
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("rota", scheduledStart = "no-es-una-fecha"),
                activity("buena", scheduledStart = "2026-11-03T09:00:00Z"),
            ),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals(listOf("buena"), agenda?.activities?.map { it.id })
    }

    @Test
    fun `el dia y la hora se leen en la zona pedida`() {
        // 01:00 UTC del 4 es 22:00 del 3 en Buenos Aires (UTC-3): es "hoy", y la hora
        // que se muestra es la local, no la del ISO.
        val buenosAires = TimeZone.of("America/Argentina/Buenos_Aires")
        val agenda = selectTodayAgenda(
            activities = listOf(
                activity("tarde", scheduledStart = "2026-11-04T01:00:00Z"),
            ),
            today = today,
            timeZone = buenosAires,
            categories = categories,
        )

        assertEquals(listOf("tarde"), agenda?.activities?.map { it.id })
        assertEquals("22:00", agenda?.activities?.first()?.timeLabel)
    }

    @Test
    fun `la hora se rellena a dos digitos`() {
        val agenda = selectTodayAgenda(
            activities = listOf(activity("temprano", scheduledStart = "2026-11-03T08:05:00Z")),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals("08:05", agenda?.activities?.first()?.timeLabel)
    }

    @Test
    fun `una categoria desconocida deja el nombre en null`() {
        val agenda = selectTodayAgenda(
            activities = listOf(activity("x", scheduledStart = "2026-11-03T09:00:00Z", categoryId = "uuid-raro")),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertNull(agenda?.activities?.first()?.categoryName)
    }

    @Test
    fun `resuelve nombre y color de la categoria conocida`() {
        val agenda = selectTodayAgenda(
            activities = listOf(activity("x", scheduledStart = "2026-11-03T09:00:00Z")),
            today = today,
            timeZone = utc,
            categories = categories,
        )

        assertEquals("Salud", agenda?.activities?.first()?.categoryName)
    }

    @Test
    fun `una lista vacia es una agenda de cero, no un hueco`() {
        val agenda = selectTodayAgenda(emptyList(), today, utc, categories)

        assertEquals(0, agenda?.total)
        assertEquals(emptyList(), agenda?.activities)
    }
}
