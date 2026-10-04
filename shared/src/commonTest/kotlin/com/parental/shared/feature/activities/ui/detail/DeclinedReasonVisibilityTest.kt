package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.visibleDeclinedReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A3 · EL MOTIVO DE "NO ASISTIR" LLEGA A TODA LA ACTIVIDAD DEL VÍNCULO, no sólo a quien lo
 * escribió. Esta es la regla que gobierna la tarjeta de la hoja de detalle.
 *
 * La prueba va contra la FUNCIÓN y no contra el composable porque no hay infraestructura de
 * UI en este repo (ni `androidTest`, ni `createComposeRule`) y porque la forma de la función
 * es justamente lo que hay que defender: [visibleDeclinedReason] no recibe `createdBy` ni
 * `currentUserId`, así que no existe por dónde colar un filtro de autor. Una función con
 * parámetro de visor tendría que re-derivar A3 en cada llamada; esta no puede.
 */
class DeclinedReasonVisibilityTest {

    private fun detalle(
        declinedReason: String?,
        createdBy: String = "u-creador",
        status: String = "not_assisting",
    ) = ActividadDetalle(
        id = "a1",
        title = "Visita al médico",
        type = "event",
        status = status,
        categoryId = "c1",
        criticality = "medium",
        createdBy = createdBy,
        createdAt = "2026-10-01T10:00:00Z",
        declinedReason = declinedReason,
    )

    @Test
    fun `el motivo llega tal cual cuando existe`() {
        assertEquals("Hospitalización", detalle("Hospitalización").visibleDeclinedReason())
    }

    @Test
    fun `A3 — un miembro que NO es el creador ve el MISMO motivo`() {
        // 🔴 El caso que falla si alguien reintroduce el gateo por autor. Se comparan los
        // DOS modelos: si la función mirara `createdBy`, estos valores dejarían de coincidir.
        val creador = detalle(declinedReason = "Hospitalización", createdBy = "u-creador")
        val otroMiembro = detalle(declinedReason = "Hospitalización", createdBy = "u-otro")
        assertEquals(creador.visibleDeclinedReason(), otroMiembro.visibleDeclinedReason())
        assertEquals("Hospitalización", otroMiembro.visibleDeclinedReason())
    }

    @Test
    fun `sin motivo no se pinta nada — no hay tarjeta vacia`() {
        assertNull(detalle(declinedReason = null).visibleDeclinedReason())
        assertNull(detalle(declinedReason = "").visibleDeclinedReason())
        assertNull(detalle(declinedReason = "     ").visibleDeclinedReason())
    }

    @Test
    fun `el motivo se recorta antes de pintarse`() {
        assertEquals("Guardia", detalle(declinedReason = "  Guardia  ").visibleDeclinedReason())
    }

    @Test
    fun `un motivo en blanco NO se confunde con un motivo presente`() {
        // Por qué el `takeIf`: una tarjeta con el rótulo "Motivo de no asistencia" y el cuerpo
        // en blanco es PEOR que no pintar la tarjeta. El backend recorta y mide antes de
        // guardar, así que esto sólo puede venir de un servidor viejo — y aun así la UI no
        // debe fingir que hay un motivo.
        assertNull(detalle(declinedReason = "   \n  ").visibleDeclinedReason())
    }

    @Test
    fun `A4 — el motivo no se borra ni se marca aparte de deletedAt`() {
        // A4: `deleted_at` es la única marca de borrado. La función de visibilidad no
        // conoce ningún otro estado, así que un motivo presente significa exactamente "el
        // servidor lo manda", ni más ni menos.
        val conMotivo = detalle(declinedReason = "C Ambulancia")
        assertEquals("C Ambulancia", conMotivo.visibleDeclinedReason())
    }
}
