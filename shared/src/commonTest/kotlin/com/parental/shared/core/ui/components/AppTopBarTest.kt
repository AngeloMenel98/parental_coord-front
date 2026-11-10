package com.parental.shared.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S12 · G-3 y G-5 — las dos reglas de la barra global que son LÓGICA, no pintado.
 *
 * El resto de S12 (la barra presente en todas las vistas, dos íconos, 44×44 dp y las
 * etiquetas accesibles) es estructura del composable: este repo no tiene infraestructura
 * de UI (`createComposeRule`, `androidTest`), así que no se puede afirmar desde un test.
 * Lo que SÍ se puede afirmar —y es donde viven los dos defectos del PRD, el tope del
 * contador y la salida sin confirmar— se prueba acá.
 */
class AppTopBarTest {

    // ── G-5 · el badge se topa en "9+" ─────────────────────────────────

    @Test
    fun `G5 sin nada sin leer no hay burbuja`() {
        assertNull(unreadBadgeLabel(0))
    }

    @Test
    fun `G5 un contador negativo tampoco pinta burbuja`() {
        // El backend no debería mandar negativos, pero un `-1` en la burbuja sería peor
        // que ninguna burbuja: no hay "menos uno sin leer".
        assertNull(unreadBadgeLabel(-1))
    }

    @Test
    fun `G5 de uno a nueve muestra el numero exacto`() {
        assertEquals("1", unreadBadgeLabel(1))
        assertEquals("5", unreadBadgeLabel(5))
        assertEquals("9", unreadBadgeLabel(9))
    }

    @Test
    fun `G5 desde diez el tope es 9+`() {
        // El PRD pide el tope en 9+, no en 99+: dos caracteres entran en la burbuja.
        assertEquals("9+", unreadBadgeLabel(10))
        assertEquals("9+", unreadBadgeLabel(42))
        assertEquals("9+", unreadBadgeLabel(120))
    }

    // ── G-3 · la salida pide confirmación ──────────────────────────────

    @Test
    fun `G3 pedir la confirmacion abre el dialogo y NO cierra la sesion`() {
        var cierres = 0
        val state = LogoutConfirmationState()

        state.request()

        assertTrue(state.isVisible)
        assertEquals(0, cierres, "tocar el icono no puede terminar la sesión")
    }

    @Test
    fun `G3 confirmar cierra la sesion una sola vez y baja el dialogo`() {
        var cierres = 0
        val state = LogoutConfirmationState()
        val onLogout: () -> Unit = { cierres++ }

        state.request()
        state.confirm(onLogout)

        assertFalse(state.isVisible)
        assertEquals(1, cierres)

        // Un segundo toque sobre el botón ya no debería existir; si llega, no re-cierra.
        state.dismiss()
        assertEquals(1, cierres)
    }

    @Test
    fun `G3 descartar el dialogo no cierra la sesion`() {
        var cierres = 0
        val state = LogoutConfirmationState()

        state.request()
        state.dismiss()

        assertFalse(state.isVisible)
        assertEquals(0, cierres)
    }
}
