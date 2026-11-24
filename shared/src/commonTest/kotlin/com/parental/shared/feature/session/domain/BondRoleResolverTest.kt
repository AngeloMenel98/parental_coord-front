package com.parental.shared.feature.session.domain

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.bonds.domain.model.VinculoMember
import com.parental.shared.feature.bonds.domain.model.VinculoMemberUser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S5/terceros: por qué el rol no puede salir sólo de `members`.
 *
 * El back redacta `members` para el tercero, así que este resolver combina la fila del
 * miembro (cuando existe) con el grant propio. Cubre el bug reportado: un tercero con
 * `members: []` caía en el Home del progenitor.
 */
class BondRoleResolverTest {
    private fun member(userId: String, role: String) =
        VinculoMember(id = "m-$userId", role = role, user = VinculoMemberUser(id = userId))

    @Test
    fun `la fila del miembro gana cuando existe`() {
        val resolved = resolveViewerRole(
            members = listOf(member("p1", "progenitor")),
            userId = "p1",
            ownGrant = null,
        )

        assertEquals(ViewerRole.Known("progenitor"), resolved)
    }

    @Test
    fun `un tercero con grant vigente se resuelve por su grant`() {
        val resolved = resolveViewerRole(
            members = emptyList(),
            userId = "t1",
            ownGrant = ApiResult.Success(Unit),
        )

        assertEquals(ViewerRole.Known("third_party"), resolved)
    }

    @Test
    fun `un grant revocado tambien es tercero, para que su vista muestre el corte`() {
        val resolved = resolveViewerRole(
            members = emptyList(),
            userId = "t1",
            ownGrant = ApiResult.Error(403, "Tu acceso temporal terminó"),
        )

        assertEquals(ViewerRole.Known("third_party"), resolved)
    }

    @Test
    fun `sin grant no es tercero`() {
        val resolved = resolveViewerRole(
            members = emptyList(),
            userId = "p1",
            ownGrant = ApiResult.Error(404, "No tenés un acceso temporal en este vínculo"),
        )

        assertEquals(ViewerRole.Known(null), resolved)
    }

    @Test
    fun `un fallo de red no concluye nada`() {
        assertEquals(
            ViewerRole.Unknown,
            resolveViewerRole(emptyList(), "t1", ApiResult.Error(0, "Network error")),
        )
        assertEquals(ViewerRole.Unknown, resolveViewerRole(emptyList(), "t1", null))
    }

    @Test
    fun `un 5xx tampoco concluye`() {
        assertEquals(
            ViewerRole.Unknown,
            resolveViewerRole(emptyList(), "t1", ApiResult.Error(502, "Bad gateway")),
        )
    }
}
