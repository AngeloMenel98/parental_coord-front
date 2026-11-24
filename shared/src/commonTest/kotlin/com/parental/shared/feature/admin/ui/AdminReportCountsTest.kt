package com.parental.shared.feature.admin.ui

import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.feature.admin.domain.model.AdminBondActivity
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminReportActivitySection
import com.parental.shared.feature.admin.domain.model.AdminReportMember
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F7 · S20/S21 — el bug que el usuario reportó, pineado.
 *
 * Prueba de campo: la tarjeta mostraba "cumplidas (1) vencidas (3)" y un 20% de
 * cumplimiento, pero el mismo bloque decía "overdue 4 / done 1". El 20% era 1 sobre un
 * total de 5, y los chips sumaban 4: una actividad no aparecía en ningún chip y el par
 * "overdue/done" salía de otra definición (predicado por deadline, no por estado).
 */
class AdminReportCountsTest {

    private fun report(
        byStatus: Map<String, Int>,
        total: Int = byStatus.values.sum(),
        overdue: Int = 0,
        completed: Int = byStatus["done"] ?: 0,
        members: List<AdminReportMember> = emptyList(),
    ) = AdminBondReport(
        bondId = "b1",
        bondTitle = "Paz–Gómez",
        dateFrom = "2026-06-01",
        dateTo = "2026-06-30",
        generatedAt = "2026-07-01T12:00:00Z",
        activities = AdminReportActivitySection(
            total = total,
            completed = completed,
            overdue = overdue,
            completionRate = 0.0,
            byStatus = byStatus,
        ),
        members = members,
    )

    @Test
    fun `S20 el contador de vencidas sale del estado y nunca del predicado por deadline`() {
        // El backend manda overdue = 4 (deadline vencido) y status overdue = 3.
        val counts = buildActivityCounts(
            report(byStatus = mapOf("done" to 1, "assigned" to 2, "overdue" to 3), overdue = 4),
        )

        val vencidas = counts.buckets.first { it.key == OVERDUE_KEY }
        assertEquals(3, vencidas.count, "la tarjeta no puede decir 4 vencidas si el estado dice 3")
        assertEquals(1, counts.completed)
        assertEquals(6, counts.total)
    }

    @Test
    fun `S20 la leyenda suma el total y el porcentaje divide por ese mismo total`() {
        val counts = buildActivityCounts(
            report(
                byStatus = mapOf(
                    "done" to 31,
                    "assigned" to 5,
                    "in_progress" to 3,
                    "overdue" to 3,
                ),
            ),
        )

        assertEquals(42, counts.total)
        assertEquals(42, counts.buckets.sumOf { it.count }, "la suma de la leyenda es el total")
        assertEquals(31, counts.completed)
        assertEquals(11, counts.notCompleted)
        assertEquals(73.81, counts.completionRate)
        assertEquals(26.19, counts.notCompletedRate)
        assertEquals(100.0, roundTo2(counts.completionRate + counts.notCompletedRate))
    }

    @Test
    fun `S20 un estado que el backend agregue despues no desaparece del total`() {
        val counts = buildActivityCounts(
            report(byStatus = mapOf("done" to 2, "frozen" to 3)),
        )

        assertEquals(5, counts.total)
        assertEquals(5, counts.buckets.sumOf { it.count })
        val otros = counts.buckets.first { it.key == OTHER_KEY }
        assertEquals(3, otros.count)
        assertEquals(60.0, otros.percentage)
    }

    @Test
    fun `S20 sin actividades no hay porcentaje ni tramos`() {
        val counts = buildActivityCounts(report(byStatus = emptyMap(), total = 0))

        assertEquals(0, counts.total)
        assertEquals(0.0, counts.completionRate)
        assertEquals(0.0, counts.notCompletedRate)
        assertTrue(counts.buckets.isEmpty())
    }

    @Test
    fun `S20 sin byStatus el total cae al que manda el reporte`() {
        val counts = buildActivityCounts(report(byStatus = emptyMap(), total = 7))

        assertEquals(7, counts.total)
        assertEquals(0, counts.completed)
        assertEquals(7, counts.notCompleted)
    }

    @Test
    fun `S20 el desglose por progenitor usa los numeros del miembro`() {
        val members = listOf(
            AdminReportMember("u1", "Mario", "Paz", "parent", assigned = 22, completed = 17),
            AdminReportMember("u2", "Lucía", "Gómez", "parent", assigned = 20, completed = 14),
            AdminReportMember("u3", "", "", "parent", assigned = 0, completed = 0),
        )

        val rows = buildMemberCompletions(members)

        assertEquals("Mario Paz", rows[0].name)
        assertEquals(17, rows[0].completed)
        assertEquals(22, rows[0].assigned)
        assertEquals(77.27, rows[0].percentage)
        assertEquals(70.0, rows[1].percentage)
        assertEquals("Progenitor", rows[2].name)
        assertEquals(0.0, rows[2].percentage)
    }

    @Test
    fun `S21 el detalle filtra por tramo y por progenitor`() {
        val activities = listOf(
            activity("a1", "done", "u1"),
            activity("a2", "overdue", "u1"),
            activity("a3", "in_progress", "u2"),
        )

        assertEquals(3, filterReportActivities(activities).size)
        assertEquals(1, filterReportActivities(activities, DONE_KEY).size)
        assertEquals(1, filterReportActivities(activities, OVERDUE_KEY).size)
        assertEquals(1, filterReportActivities(activities, OPEN_KEY).size)
        assertEquals(2, filterReportActivities(activities, FILTER_ALL, "u1").size)
        assertEquals(0, filterReportActivities(activities, DONE_KEY, "u2").size)
    }

    @Test
    fun `S21 cada estado del backend cae en un tramo conocido`() {
        assertEquals(DONE_KEY, bucketKeyOf("DONE"))
        assertEquals(OVERDUE_KEY, bucketKeyOf("overdue"))
        assertEquals(OPEN_KEY, bucketKeyOf("assigned"))
        assertEquals(OPEN_KEY, bucketKeyOf("in_progress"))
        assertEquals(OPEN_KEY, bucketKeyOf("verify"))
        assertEquals(CLOSED_KEY, bucketKeyOf("cancelled"))
        assertEquals(CLOSED_KEY, bucketKeyOf("not_assisting"))
        assertEquals(OTHER_KEY, bucketKeyOf("frozen"))
    }

    @Test
    fun `S21 las fechas se leen en el huso que se le pasa`() {
        val iso = "2026-06-12T09:30:00Z"

        assertEquals("12", activityDayLabel(iso, TimeZone.UTC))
        assertEquals("JUN", activityMonthLabel(iso, TimeZone.UTC))
        assertEquals("09:30", activityTimeLabel(iso, TimeZone.UTC))
        assertEquals("12/06", activityShortDate(iso, TimeZone.UTC))
        assertEquals("—", activityDayLabel(null, TimeZone.UTC))
    }

    @Test
    fun `S21 el limite se lee en pasado solo cuando la actividad esta vencida`() {
        val overdue = activity("a1", "overdue", "u1").copy(deadline = "2026-06-18T16:00:00Z")
        val open = activity("a2", "in_progress", "u1").copy(deadline = "2026-07-02T08:30:00Z")
        val withoutDeadline = activity("a3", "assigned", "u1")

        assertEquals("venció 18/06", activityDeadlineLabel(overdue, TimeZone.UTC))
        assertEquals("vence 02/07", activityDeadlineLabel(open, TimeZone.UTC))
        assertNull(activityDeadlineLabel(withoutDeadline, TimeZone.UTC))
    }

    @Test
    fun `S1 el CTA del detalle copia el diseño con la cuenta`() {
        assertEquals("Ver las 42 actividades", adminActivityDetailCta(42))
        assertEquals("Ver la actividad", adminActivityDetailCta(1))
        assertEquals("Ver actividades", adminActivityDetailCta(0))
    }

    @Test
    fun `S1 la fila del detalle resuelve la categoria como la agenda`() {
        // colorHex nulo: `categoryTagTriple` resuelve por nombre con los mismos tokens
        // que usa la fila de la agenda real.
        assertEquals(
            CategoryInfo(name = "Salud", colorHex = null, emoji = null),
            adminCategoryInfo("Salud"),
        )
        // Sin categoría (o vacía) no se fabrica info: la fila cae al outline neutro.
        assertNull(adminCategoryInfo(null))
        assertNull(adminCategoryInfo(""))
        assertNull(adminCategoryInfo("   "))
    }

    private fun activity(id: String, status: String, assignee: String?) = AdminBondActivity(
        id = id,
        title = "Actividad $id",
        status = status,
        assignedTo = assignee,
    )
}
