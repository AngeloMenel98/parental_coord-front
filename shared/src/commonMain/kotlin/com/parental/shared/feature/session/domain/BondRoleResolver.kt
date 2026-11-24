package com.parental.shared.feature.session.domain

import com.parental.shared.core.error.ApiResult
import com.parental.shared.feature.bonds.domain.model.VinculoMember

/** Rol del viewer dentro del vínculo, resuelto con lo que el backend le dejó ver. */
sealed interface ViewerRole {
    /** Conclusión: `role` es el rol del vínculo, o `null` si no hay vínculo/rol. */
    data class Known(val role: String?) : ViewerRole

    /** Todavía no se puede concluir (sin red, 5xx): hay que reintentar. */
    data object Unknown : ViewerRole
}

/**
 * Resuelve el rol del viewer en el vínculo.
 *
 * **Por qué no alcanza con `members`**: por S5 el backend le redacta las colecciones al
 * tercero temporal (`members: []`), así que su propia fila nunca viaja en `GET /bonds` y
 * el rol no se puede leer de ahí — el bug que dejaba al tercero en el Home de progenitor.
 *
 * `GET /third-parties/me` sí lo identifica, y sus respuestas tienen significado distinto:
 * - **200**: tiene un grant, es tercero.
 * - **403**: el grant está revocado/suspendido; sigue siendo tercero y su vista muestra el
 *   corte de acceso ("Tu acceso temporal terminó"), no el Home del progenitor.
 * - **404**: no tiene grant; no es tercero (un progenitor cae acá).
 * - **error de red (0) o 5xx**: no es una respuesta, es un fallo: [ViewerRole.Unknown] para
 *   que la derivación reintente en vez de degradar a una vista equivocada.
 */
fun resolveViewerRole(
    members: List<VinculoMember>,
    userId: String?,
    ownGrant: ApiResult<*>?,
): ViewerRole {
    members.firstOrNull { it.user?.id == userId }?.role?.let { return ViewerRole.Known(it) }

    return when {
        ownGrant is ApiResult.Success -> ViewerRole.Known(THIRD_PARTY_ROLE)
        ownGrant is ApiResult.Error && ownGrant.code == 403 -> ViewerRole.Known(THIRD_PARTY_ROLE)
        ownGrant is ApiResult.Error && ownGrant.code == 404 -> ViewerRole.Known(null)
        else -> ViewerRole.Unknown
    }
}

/** El valor que el back persiste en `bond_member.role` para un tercero temporal. */
const val THIRD_PARTY_ROLE: String = "third_party"
