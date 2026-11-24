package com.parental.shared.feature.session

import com.parental.shared.feature.session.data.SessionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El vínculo de una sesión NO se hereda en la siguiente (R1-002).
 *
 * `bondRole` es lo que decide si la app monta la vista de terceros, así que un logout seguido
 * del login de otra persona no puede enrutar al nuevo usuario con el rol del anterior. Se
 * afirma sobre el estado EN MEMORIA del [SessionManager] real: la persistencia
 * (`Platform.saveString`) es un no-op en JVM —sin `init()` no hay prefs— y por eso acá no se
 * prueba la escritura en disco.
 */
class SessionManagerTest {

    @Test
    fun `un login nuevo no hereda el rol del vínculo anterior`() {
        val session = SessionManager()
        session.setSession(token = "token-a", userName = "Ana", role = "user", bondId = "bond-a")
        session.updateBondRole("third_party")

        session.setSession(token = "token-b", userName = "Beto", role = "user", bondId = "bond-b")

        assertNull(
            session.bondRole,
            "🔴 R1-002: el rol del vínculo es del usuario anterior; si sobrevive, un tercero " +
                "enruta la sesión que no es suya.",
        )
        assertNull(
            session.bondId,
            "🔴 R1-002 (integración PR #9): el vínculo ya no viaja en la respuesta del login; " +
                "setSession siempre lo arranca en null y lo fija la derivación " +
                "(`GET /bonds`) después. Lo importante es el mismo: NADA del login anterior " +
                "sobrevive.",
        )
        // La derivación llega después y trae el vínculo de ESTE login — sin rol heredado.
        session.updateBondId("bond-b")
        assertEquals(
            "bond-b",
            session.bondId,
            "PRESERVE: el vínculo NUEVO llega por la derivación, no heredado.",
        )
        assertNull(
            session.bondRole,
            "🔴 R1-002: el rol nuevo también llega por la derivación, nunca del login anterior.",
        )
    }

    @Test
    fun `un login nuevo sin vínculo no conserva el bondId anterior`() {
        val session = SessionManager()
        session.setSession(token = "token-a", userName = "Ana", role = "user", bondId = "bond-a")

        session.setSession(token = "token-b", userName = "Beto", role = "user")

        assertNull(
            session.bondId,
            "🔴 R1-002: sin vínculo en la respuesta, el anterior no puede quedar pegado.",
        )
        assertNull(
            session.bondRole,
            "🔴 R1-002: sin vínculo tampoco hay rol que heredar.",
        )
    }

    @Test
    fun `clear no deja rastro del vínculo`() {
        val session = SessionManager()
        session.setSession(token = "token-a", userName = "Ana", role = "user", bondId = "bond-a")
        session.updateBondRole("third_party")

        session.clear()

        assertNull(session.bondId, "🔴 R1-002: tras el logout no queda vínculo en memoria.")
        assertNull(session.bondRole, "🔴 R1-002: ni rol del vínculo.")
        assertNull(session.token, "PRESERVE: clear() sigue cerrando la sesión.")
        assertFalse(session.isLoggedIn, "PRESERVE: sin token no hay sesión.")
        assertFalse(session.mustChangePassword, "PRESERVE: el bloqueo tampoco sobrevive.")
        assertFalse(
            session.state.value.loginPending,
            "PRESERVE: el login pendiente se descarta.",
        )
    }

    @Test
    fun `setSession sigue escribiendo token, nombre, rol y flag`() {
        val session = SessionManager()

        session.setSession(
            token = "token-a",
            userName = "Ana",
            role = "admin",
            mustChangePassword = true,
        )

        assertEquals("token-a", session.token, "PRESERVE: el token sigue entrando por acá.")
        assertEquals("Ana", session.userName, "PRESERVE: y el nombre también.")
        assertEquals("admin", session.role, "PRESERVE: el `system_role` sigue entrando por acá.")
        assertTrue(session.mustChangePassword, "PRESERVE: el flag también.")
        assertTrue(session.isLoggedIn, "PRESERVE: con token hay sesión.")
    }
}
