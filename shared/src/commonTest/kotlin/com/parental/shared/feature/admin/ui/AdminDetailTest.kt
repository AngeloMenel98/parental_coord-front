package com.parental.shared.feature.admin.ui

import com.parental.shared.feature.admin.domain.model.AdminBond
import com.parental.shared.feature.admin.domain.model.AdminBondReport
import com.parental.shared.feature.admin.domain.model.AdminReportChild
import com.parental.shared.feature.admin.domain.model.AdminReportMember
import com.parental.shared.feature.admin.domain.model.BondMemberInfo
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L21 · detalle del vínculo (artboard `admin-ui/01`, frame 2 rehecho): salud en la cabecera,
 * hijos con edad y actividad, progenitores con nombre y participación. Los números vienen del
 * reporte cargado en background; el merge con `bond.members` es puro para que la fila del
 * detalle y el reporte no puedan decir cosas distintas.
 */
class AdminDetailTest {

    private val report = AdminBondReport(
        bondId = "b1",
        bondTitle = "Paz–Gómez",
        dateFrom = "2026-06-01",
        dateTo = "2026-06-30",
        generatedAt = "2026-07-01T12:00:00Z",
        children = listOf(
            AdminReportChild("c1", "Mateo", "Paz", "2020-05-15", ageInYears = 6, activityCount = 18),
            AdminReportChild("c2", "Lucía", "Paz", activityCount = 11),
        ),
        members = listOf(
            AdminReportMember("u1", "Mario", "Paz", "parent", assigned = 22, completed = 17),
        ),
    )

    private fun bondOf(vararg members: BondMemberInfo) = AdminBond(
        id = "b1",
        title = "Paz–Gómez",
        agreementType = "formal",
        members = members.toList(),
    )

    private fun member(id: String, email: String?, role: String = "parent") =
        BondMemberInfo(id = id, email = email, role = role)

    @Test
    fun `S24 la edad del hijo sale de la fecha de nacimiento y el dia de hoy`() {
        val today = LocalDate.parse("2026-06-01")

        assertEquals("6 años", childAgeLabel("2020-05-15", today))
        assertEquals("6 años", childAgeLabel("2020-05-15T00:00:00Z", today))
        assertNull(childAgeLabel(null, today))
        assertNull(childAgeLabel("no-una-fecha", today))
    }

    @Test
    fun `S24 el cumpleaños que no llego todavia resta un año`() {
        val today = LocalDate.parse("2026-03-01")

        assertEquals("5 años", childAgeLabel("2020-05-15", today))
    }

    @Test
    fun `S24 un solo año se dice en singular`() {
        assertEquals("1 año", childAgeLabel("2025-01-10", LocalDate.parse("2026-06-01")))
    }

    @Test
    fun `S24 la actividad del hijo viene del reporte y un hijo de fuera da null`() {
        assertEquals(18, childActivityCount(report, "c1"))
        assertEquals(11, childActivityCount(report, "c2"))
        assertNull(childActivityCount(report, "c99"))
        assertNull(childActivityCount(null, "c1"))
    }

    @Test
    fun `S24 los miembros combinan el rol del vínculo con la participación del reporte`() {
        val rows = buildDetailMembers(
            bondOf(member("u1", "mario.paz@mail.com")),
            report,
        )

        assertEquals(1, rows.size)
        assertEquals("Mario Paz", rows[0].name)
        assertEquals("Progenitor", rows[0].roleLabel)
        assertEquals(17, rows[0].completed)
        assertEquals(22, rows[0].assigned)
        assertEquals(77.27, rows[0].percentage)
    }

    @Test
    fun `S24 sin reporte el miembro cae al email y sin números`() {
        val rows = buildDetailMembers(bondOf(member("u1", "mario.paz@mail.com")), null)

        assertEquals("mario.paz", rows[0].name)
        assertEquals(0, rows[0].assigned)
        assertEquals(0.0, rows[0].percentage)
    }

    @Test
    fun `S24 un miembro del vínculo que el reporte no conoce no desaparece`() {
        val rows = buildDetailMembers(
            bondOf(member("u1", "mario.paz@mail.com"), member("u2", "lucia@mail.com")),
            report,
        )

        assertEquals(2, rows.size)
        assertEquals("lucia", rows[1].name)
        assertEquals(0, rows[1].assigned)
    }

    @Test
    fun `S24 el rol desconocido se muestra capitalizado`() {
        assertEquals("Progenitor", memberRoleLabel("parent"))
        assertEquals("Padre", memberRoleLabel("father"))
        assertEquals("Madre", memberRoleLabel("mother"))
        assertEquals("Tutor", memberRoleLabel("tutor"))
        assertEquals("Abuelo", memberRoleLabel("abuelo"))
    }

    @Test
    fun `S24 las iniciales del avatar toman la primera letra de nombre y apellido`() {
        assertEquals("MP", initialsOf("Mario Paz"))
        assertEquals("M", initialsOf("Mario"))
        assertEquals("?", initialsOf("  "))
    }

    @Test
    fun `S24 un rol o nombre vacio no rompe la fila`() {
        val rows = buildDetailMembers(bondOf(member("u9", null, role = "")), report)

        assertEquals(1, rows.size)
        assertTrue(rows[0].name.isNotBlank(), "la fila necesita un nombre visible")
        assertEquals("Miembro", rows[0].roleLabel)
    }
}
