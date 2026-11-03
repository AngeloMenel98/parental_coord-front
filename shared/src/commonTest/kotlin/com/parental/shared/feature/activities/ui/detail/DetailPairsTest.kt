package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S9 · D-4…D-6 — los pares del bloque de datos del detalle.
 *
 * Va contra la función y no contra el composable: el repo no tiene infraestructura de UI
 * (`createComposeRule`), y lo que hay que defender es la REGLA —qué par aparece, en qué
 * orden y con qué texto—, no su pintado.
 */
class DetailPairsTest {

    private fun detalle(
        scheduledStart: String? = "2026-08-05T16:30:00Z",
        scheduledEnd: String? = null,
        deadline: String? = null,
        assignedTo: String? = "u-maria",
        criticality: String = "medium",
        childrenIds: List<String> = emptyList(),
    ) = ActividadDetalle(
        id = "a1",
        title = "Clases de natación",
        type = "event",
        status = "assigned",
        categoryId = "c1",
        criticality = criticality,
        createdBy = "u-carlos",
        createdAt = "2026-08-01T10:00:00Z",
        scheduledStart = scheduledStart,
        scheduledEnd = scheduledEnd,
        deadline = deadline,
        assignedTo = assignedTo,
        childrenIds = childrenIds,
    )

    private val ana = VinculoChild(id = "c1", firstName = "Ana", lastName = "García")
    private val luis = VinculoChild(id = "c2", firstName = "Luis", lastName = "García")

    private fun pares(
        actividad: ActividadDetalle,
        children: List<VinculoChild> = emptyList(),
    ) = detailPairs(
        actividad = actividad,
        memberNames = mapOf("u-maria" to "María G."),
        children = children,
        zone = TimeZone.UTC,
    )

    @Test
    fun `D4 la fecha del detalle lleva la hora del inicio`() {
        assertEquals(
            "Fecha" to "5 ago 2026 · 16:30",
            pares(detalle()).first(),
        )
    }

    @Test
    fun `D4 con fin programado la fecha muestra el rango`() {
        val fecha = pares(detalle(scheduledEnd = "2026-08-05T18:00:00Z")).first()
        assertEquals("Fecha" to "5 ago 2026 · 16:30 – 18:00", fecha)
    }

    @Test
    fun `D6 asignado a resuelve el uuid contra memberNames`() {
        assertTrue(pares(detalle()).contains("Asignado a" to "María G."))
    }

    @Test
    fun `D6 sin asignar es un valor, no un par ausente`() {
        // Regla R-7: "Asignado a" SIEMPRE se pinta; el resto de los opcionales se omiten.
        assertTrue(pares(detalle(assignedTo = null)).contains("Asignado a" to "Sin asignar"))
    }

    @Test
    fun `D5 los hijos se resuelven como nombres separados por coma`() {
        val conHijos = detalle(childrenIds = listOf("c1", "c2"))
        assertTrue(pares(conHijos, listOf(ana, luis)).contains("Para" to "Ana García, Luis García"))
    }

    @Test
    fun `D5 sin hijos no hay par Para`() {
        val claves = pares(detalle(childrenIds = emptyList()), listOf(ana, luis)).map { it.first }
        assertTrue("Para" !in claves)
    }

    @Test
    fun `D5 un id que no resuelve se omite, nunca se pinta el uuid`() {
        // Vínculo todavía cargando: `children` vacío pero el detalle ya trae ids.
        val claves = pares(detalle(childrenIds = listOf("c1"))).map { it.first }
        assertTrue("Para" !in claves)
        assertTrue(
            pares(detalle(childrenIds = listOf("c1"))).none { it.second.contains("c1") },
            "un uuid crudo no debe llegar a la pantalla",
        )
    }

    @Test
    fun `D5 un id resuelto y otro no pinta solo el que resuelve`() {
        val paresConUno = pares(detalle(childrenIds = listOf("c1", "c-desconocido")), listOf(ana))
        assertTrue(paresConUno.contains("Para" to "Ana García"))
    }

    @Test
    fun `R7 la prioridad nunca se oculta`() {
        assertTrue(pares(detalle(criticality = "critical")).contains("Prioridad" to "Alta"))
    }

    @Test
    fun `R7 el limite de evidencia se omite cuando no hay deadline`() {
        val claves = pares(detalle(deadline = null)).map { it.first }
        assertTrue("Límite evidencia" !in claves)
        val conLimite = pares(detalle(deadline = "2026-08-06T00:00:00Z")).map { it.first }
        assertTrue("Límite evidencia" in conLimite)
    }
}
