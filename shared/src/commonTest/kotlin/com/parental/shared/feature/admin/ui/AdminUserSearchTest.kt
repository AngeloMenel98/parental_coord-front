package com.parental.shared.feature.admin.ui

import com.parental.shared.feature.admin.domain.model.AdminUser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F6 · el buscador de personas: nombre, apellido o email, sin acentos ni mayúsculas. */
class AdminUserSearchTest {
    private val maria = AdminUser(
        id = "u1",
        email = "maria.paz@mail.com",
        systemRole = "user",
        firstName = "María",
        lastName = "Paz",
    )
    private val ivan = AdminUser(
        id = "u2",
        email = "ivan.sosa@mail.com",
        systemRole = "user",
        firstName = "Iván",
        lastName = "Sosa",
    )

    @Test
    fun `finds by first name ignoring accents and case`() {
        assertTrue(maria.matchesUserQuery("maria"))
        assertTrue(maria.matchesUserQuery("MARÍA"))
    }

    @Test
    fun `finds by last name`() {
        assertTrue(ivan.matchesUserQuery("sosa"))
        assertTrue(ivan.matchesUserQuery("SO"))
    }

    @Test
    fun `finds by email`() {
        assertTrue(ivan.matchesUserQuery("ivan.sosa@"))
    }

    @Test
    fun `an empty query matches everyone`() {
        assertTrue(maria.matchesUserQuery(""))
        assertTrue(maria.matchesUserQuery("   "))
    }

    @Test
    fun `does not match a different person`() {
        assertFalse(maria.matchesUserQuery("sosa"))
        assertFalse(ivan.matchesUserQuery("gomez"))
    }
}
