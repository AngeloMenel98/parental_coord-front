package com.parental.shared.feature.activities.domain

import com.parental.shared.feature.activities.domain.model.ActividadAttribution

/**
 * La línea de atribución que pinta la card, en UNA sola función (S5 de
 * `odd/tasks/thirdparty-identity.md`): "en las cards de activities tiene que
 * mostrarse el nombre del terciario para el que lo autorizo, para los demas solo
 * muestra quien lo autorizo".
 *
 * La visibilidad la decidió el servidor (S7): `name` con el nombre real llega
 * únicamente al autorizante del grant. Acá sólo se TRADUCE a línea de UI:
 *
 * - `kind != "third_party"` → `null`: lo que creó un miembro sigue pintándose como
 *   siempre (asignatario + hijos); una línea nueva para todos sería un cambio de
 *   UI que nadie pidió.
 * - terciario con `name` (el autorizante mira) → el nombre real: "Pablo Charles",
 *   la MISMA frase que vería en la lista de miembros del vínculo.
 * - terciario sin `name` (los demás miran) → "Autorizado por <autorizante>"; sin
 *   autorizante con nombre (grant muerto, back viejo) → `null`: un hueco honesto
 *   en vez de una identidad filtrada o una frase inventada.
 */
fun attributionLine(attribution: ActividadAttribution?): String? {
    if (attribution?.kind != "third_party") return null
    attribution.name?.takeIf { it.isNotBlank() }?.let { return it }
    val authorizedBy = attribution.authorizedByName?.takeIf { it.isNotBlank() } ?: return null
    return "Autorizado por $authorizedBy"
}
