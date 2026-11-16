package com.parental.shared.feature.thirdparty.domain.model

import kotlinx.serialization.Serializable

/**
 * `GET /third-parties/me` — el grant del visitante temporal, tal como lo devuelve el
 * backend (`auth_third_party` endurecida, T1/T2 de
 * `odd/tasks/terciarios-temporales.md`).
 *
 * **Todos los campos tienen default, y eso es la aplicación de S9.** El endpoint todavía
 * no existe en el back (T2 está pendiente), así que el cliente no puede exigir nada: un
 * grant vacío tiene que degradar a "no hay tarjeta de permiso" en vez de romper la
 * deserialización. Lo que NO hace este DTO es inventar la fecha: si `endDate` viene
 * `null`, la UI no dibuja vigencia ni barra de período.
 *
 * `authorizedBy` es el dueño del permiso (S7) y `authorizedByName` su nombre ya resuelto
 * por el servidor. El nombre se usa SÓLO para la frase "Autorizado por …" (S12/T12): el
 * terciario nunca ve el resto de los miembros del vínculo.
 */
@Serializable
data class ThirdPartyGrant(
    val bondId: String = "",
    val userId: String = "",
    /** `FAMILY|CAREGIVER|MEDICAL|EDUCATIONAL`, o texto libre si el back lo manda así. */
    val relationship: String? = null,
    /** ISO-8601. Inicio del período: sin él no hay denominador para la barra. */
    val startDate: String? = null,
    /** ISO-8601. Obligatorio por D3 en el back; nullable acá sólo por degradación. */
    val endDate: String? = null,
    val authorizedBy: String? = null,
    val authorizedByName: String? = null,
)

/**
 * La relación en palabras, para la línea secundaria de la tarjeta de permiso.
 *
 * El enum del back es `FAMILY|CAREGIVER|MEDICAL|EDUCATIONAL`; un valor que no esté en la
 * tabla se muestra tal cual en vez de esconderse, porque es información que el progenitor
 * escribió y el terciario puede necesitar entender.
 */
fun relationshipLabel(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (trimmed.uppercase()) {
        "FAMILY" -> "Familiar"
        "CAREGIVER" -> "Cuidador"
        "MEDICAL" -> "Médico"
        "EDUCATIONAL" -> "Educativo"
        else -> trimmed
    }
}
