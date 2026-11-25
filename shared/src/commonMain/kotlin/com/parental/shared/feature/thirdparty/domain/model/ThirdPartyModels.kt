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
 *
 * **Las cuatro salidas están congeladas** (Familiar / Cuidador / Médico / Educativo): la
 * vista del terciario las muestra así desde T8 y cambiarlas sería un cambio de contrato
 * de lectura, no un detalle de esta pantalla. El alta del progenitor usa etiquetas de
 * ACCIÓN distintas ([ThirdPartyRelationshipOption.label]: "Cuidador/a", "Docente"),
 * porque ahí la palabra es lo que la persona elige, no lo que se informa.
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

/**
 * El acceso temporal tal como lo ve el PROGENITOR que lo otorgó
 * (`GET /bonds/:bondId/third-parties`, T2 del back).
 *
 * Es un tipo distinto de [ThirdPartyGrant] a propósito: aquél es la lectura del propio
 * visitante (`/third-parties/me`, sin `id`, `status`, `note` ni `revokedAt`) y éste es la
 * consola del dueño, que necesita el `id` para editar y revocar y el `status` para decir
 * si el acceso sigue vigente. Fundirlos en uno obligaría a que cada pantalla arrastre
 * campos que no puede usar.
 *
 * **Todos los campos tienen default**: el endpoint ya existe, pero un campo nuevo del
 * servidor no puede romper la deserialización de la lista entera. `endDate` viaja como
 * `YYYY-MM-DD` (columna `date` del back), sin hora ni zona que puedan correr el día.
 */
@Serializable
data class ThirdPartyAccess(
    val id: String = "",
    val bondId: String = "",
    val userId: String = "",
    /** Nombre del visitante dentro del vínculo; `null` si no tiene datos personales. */
    val displayName: String? = null,
    /** S1 (thirdparty-identity): identidad declarada; nullable para grants previos. */
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val document: String? = null,
    /** `family|caregiver|medical|educational`. */
    val relationship: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    /** `active|suspended|revoked|expired`; lo decide el servidor, nunca el cliente. */
    val status: String? = null,
    val authorizedBy: String? = null,
    val authorizedByName: String? = null,
    val note: String? = null,
    val revokedAt: String? = null,
)

/**
 * El cuerpo del alta (`POST /bonds/:bondId/third-parties`).
 *
 * `email` puede no tener cuenta: el alta la crea con una contraseña provisoria y le manda
 * los datos por mail (S1/D2). Si el correo no sale, el back revierte cuenta y acceso y
 * responde un error que esta capa muestra tal cual, sin inventar un texto propio que se
 * desincronice del back.
 */
@Serializable
data class CreateThirdPartyAccessRequest(
    val email: String,
    /** S1 (thirdparty-identity): identidad declarada por el progenitor. */
    val firstName: String = "",
    val lastName: String = "",
    val phone: String? = null,
    val document: String? = null,
    val relationship: String,
    /** `YYYY-MM-DD`, obligatorio (D3). */
    val accessEnd: String,
    val note: String? = null,
)

/**
 * El cuerpo del PATCH: sólo relación, vencimiento y nota.
 *
 * No incluye el estado porque el back no lo acepta: extender la fecha sí, reactivar o
 * revocar no (revocar es `DELETE`, y el terciario nunca se reactiva solo).
 */
@Serializable
data class UpdateThirdPartyAccessRequest(
    val relationship: String? = null,
    val accessEnd: String? = null,
    val note: String? = null,
)

/**
 * El estado del grant en palabras, para la etiqueta de la tarjeta del progenitor.
 *
 * `active|expired|revoked|suspended` son los cuatro valores que el back persiste; un
 * valor desconocido se muestra tal cual (misma política que [relationshipLabel]) para que
 * un estado nuevo del servidor sea visible en vez de quedar vacío. `null`/vacío no es un
 * estado: no hay etiqueta que pintar.
 */
fun thirdPartyStatusLabel(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (trimmed.lowercase()) {
        "active" -> "Activo"
        "expired" -> "Vencido"
        "revoked" -> "Revocado"
        "suspended" -> "Suspendido"
        else -> trimmed
    }
}

/**
 * Las cuatro relaciones del back, con la etiqueta con la que se ELIGEN en el asistente.
 *
 * `value` es el string exacto del enum del servidor (minúsculas, `ThirdPartyRelationship`
 * del back); `label` es la del artboard `04-vigencia-a-la-vista.html` (Familiar /
 * Cuidador/a / Médico/a / Docente). No se reusa [relationshipLabel] para las dos cosas
 * porque ese helper informa ("Educativo") y este elige ("Docente"), y forzar la misma
 * palabra en los dos lugares cambiaría una de las dos lecturas.
 */
enum class ThirdPartyRelationshipOption(val value: String, val label: String) {
    FAMILY("family", "Familiar"),
    CAREGIVER("caregiver", "Cuidador/a"),
    MEDICAL("medical", "Médico/a"),
    EDUCATIONAL("educational", "Docente"),
    ;

    companion object {
        /** El valor del back (venga en el caso que venga) → la opción, o `null` si no está. */
        fun fromValue(raw: String?): ThirdPartyRelationshipOption? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}
