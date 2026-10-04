package com.parental.shared.feature.activities.ui.swipe

import com.parental.shared.feature.activities.domain.model.Actividad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * R1 · La regla más importante del cliente: leer los `can_*` del servidor, no recalcular.
 *
 * Si estas pruebas pasan por el motivo equivocado, un cambio de reglas en el backend
 * llegaría a la UI como un menú que ofrece acciones que el servidor va a rechazar con
 * 403. De ahí que TODOS los casos usen flags false explícitos en vez de confiar en los
 * valores por defecto del modelo.
 */
class SwipeActionPolicyTest {

    private fun row(
        canDelete: Boolean = false,
        canCancel: Boolean = false,
        canDecline: Boolean = false,
    ) = Actividad(
        id = "a1",
        title = "Visita al médico",
        type = "event",
        status = "assigned",
        categoryId = "c1",
        criticality = "medium",
        canDelete = canDelete,
        canCancel = canCancel,
        canDecline = canDecline,
    )

    @Test
    fun `sin flags no hay acciones — un servidor viejo degrada a sin acciones`() {
        assertEquals(emptyList(), availableSwipeActions(row()))
        assertFalse(row().hasSwipeActions())
    }

    @Test
    fun `cada flag habilita exactamente su acción`() {
        assertEquals(listOf(SwipeAction.DELETE), availableSwipeActions(row(canDelete = true)))
        assertEquals(listOf(SwipeAction.CANCEL), availableSwipeActions(row(canCancel = true)))
        assertEquals(listOf(SwipeAction.DECLINE), availableSwipeActions(row(canDecline = true)))
    }

    @Test
    fun `el orden esCancelar No asistir Eliminar — lo reversible antes de lo destructivo`() {
        val acciones = availableSwipeActions(row(canDelete = true, canCancel = true, canDecline = true))
        assertEquals(
            listOf(SwipeAction.CANCEL, SwipeAction.DECLINE, SwipeAction.DELETE),
            acciones,
        )
    }

    @Test
    fun `los flags no dependen del status ni del rol local`() {
        // 🔴 Si estas Flags se derivaran del estado, cambiar la política del servidor sería
        // insuficiente: aquí se ve que NO se mira `status` en absoluto. Se cambia a un
        // estado terminal y la lista de acciones NO cambia.
        val asignada = row(canDelete = true)
        val vencida = asignada.copy(status = "overdue")
        assertEquals(availableSwipeActions(asignada), availableSwipeActions(vencida))
    }

    @Test
    fun `A1 — sólo cancelar y declinar exigen motivo`() {
        assertFalse(SwipeAction.DELETE.requiresReason)
        assertTrue(SwipeAction.CANCEL.requiresReason)
        assertTrue(SwipeAction.DECLINE.requiresReason)
    }

    @Test
    fun `el botón de confirmar nombra la acción completa`() {
        assertEquals("Eliminar", SwipeAction.DELETE.confirmLabel)
        assertEquals("Cancelar con motivo", SwipeAction.CANCEL.confirmLabel)
        assertEquals("No asistir con motivo", SwipeAction.DECLINE.confirmLabel)
    }
}

/**
 * A1 · El motivo obligatorio. El caso que más veces se cuela en producción es el de los
 * espacios: `"     "` mide 5 caracteres y, sin `trim()` antes de medir, pasaría.
 */
class SwipeReasonValidationTest {

    @Test
    fun `valid`() {
        assertIs<ReasonValidation.Valid>(validateSwipeReason("Hospitalización"))
    }

    @Test
    fun `los espacios NO cuentan como motivo`() {
        assertIs<ReasonValidation.Blank>(validateSwipeReason("     "))
        assertIs<ReasonValidation.Blank>(validateSwipeReason(""))
    }

    @Test
    fun `dos caracteres tras recortar es demasiado corto`() {
        assertEquals(ReasonValidation.TooShort(2), validateSwipeReason("  ab  "))
    }

    @Test
    fun `el minimo exacto se acepta`() {
        assertIs<ReasonValidation.Valid>(validateSwipeReason("abc"))
    }

    @Test
    fun `el maximo exacto se acepta`() {
        assertIs<ReasonValidation.Valid>(validateSwipeReason("a".repeat(REASON_MAX_LENGTH)))
    }

    @Test
    fun `un caracter de mas se rechaza`() {
        assertEquals(
            ReasonValidation.TooLong(REASON_MAX_LENGTH + 1),
            validateSwipeReason("a".repeat(REASON_MAX_LENGTH + 1)),
        )
    }

    @Test
    fun `el recorte ocurre ANTES de medir`() {
        // 🔴 El motivo de que este caso exista: `trim().length` y `length.trim()` no dan lo
        // mismo. Sin recortar primero, `"   ab   "` mide 9 y pasaría como "válido" sin llegar
        // a tener 3 caracteres de contenido.
        assertIs<ReasonValidation.TooShort>(validateSwipeReason("   ab   "))
    }

    @Test
    fun `el mensaje de TooShort dice cuantos caracteres faltan de verdad`() {
        val mensaje = ReasonValidation.TooShort(1).message()
        assertTrue("1" in mensaje, mensaje)
    }

    @Test
    fun `el recorte del dialogo corta al maximo`() {
        assertEquals(REASON_MAX_LENGTH, "  ".plus("a".repeat(500)).trimmedReason().length)
    }

    @Test
    fun `trimmedReason recorta los espacios de los extremos`() {
        assertEquals("abc", "  abc  ".trimmedReason())
    }
}

/**
 * R2 · La ventana de 5000 ms. Lo importante aquí es que el reloj se INYECTA: sin eso, una
 * prueba de caducidad tendría que esperar cinco segundos de reloj real.
 */
class UndoOfferTest {

    private fun offer(expiresAt: Long = 5_000L, action: SwipeAction = SwipeAction.DELETE) = UndoOffer(
        id = "a1",
        action = action,
        previous = Actividad(
            id = "a1",
            title = "T",
            type = "event",
            status = "assigned",
            categoryId = "c",
            criticality = "low",
        ),
        expiresAtEpochMillis = expiresAt,
    )

    @Test
    fun `quedan los 5000 ms completos`() {
        assertEquals(5_000L, offer().remainingMillis(0L))
    }

    @Test
    fun `el limite es INCLUSIVO — a los 5000 ms exactos todavia vive`() {
        assertTrue(offer().isLive(5_000L))
        assertEquals(0L, offer().remainingMillis(5_000L))
    }

    @Test
    fun `un milisegundo despues ya no`() {
        assertFalse(offer().isLive(5_001L))
    }

    @Test
    fun `el restante nunca es NEGATIVO`() {
        // 🔴 `coerceAtLeast(0)` y no un clamp con límite superior: un negativo colándose en
        // un `remaining > 0` con un `>=` mal puesto dejaría el botón visible para siempre.
        assertEquals(0L, offer().remainingMillis(9_999L))
    }

    @Test
    fun `el mensaje de exito usa el participio de la accion`() {
        assertEquals("Actividad eliminada", offer().successMessage())
        assertEquals("Actividad cancelada", offer(action = SwipeAction.CANCEL).successMessage())
        assertEquals("Actividad declinada", offer(action = SwipeAction.DECLINE).successMessage())
    }

    @Test
    fun `la ventana declarada son 5000 ms — si cambia, este test avisa`() {
        // 🔴 El backend también tiene 5000. Este test NO lo ata al servidor: documenta que
        // el valor está DUPLICADO a propósito y que cambiarlo aquí exige cambiarlo allí.
        assertEquals(5_000L, SWIPE_UNDO_WINDOW_MS)
        assertEquals("Deshacer", SWIPE_UNDO_LABEL)
    }
}