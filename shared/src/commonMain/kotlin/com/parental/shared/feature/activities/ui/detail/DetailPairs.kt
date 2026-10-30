package com.parental.shared.feature.activities.ui.detail

import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.resolveChildNames
import com.parental.shared.feature.activities.ui.criticalityLabel
import com.parental.shared.feature.activities.ui.formatDetailDate
import com.parental.shared.feature.activities.ui.formatDetailDateTime
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.TimeZone

/**
 * S9 · Los pares del bloque de datos del detalle, en el orden en que se pintan.
 *
 * Vive FUERA de la composición a propósito: es la regla de S9 (D-4…D-6), no su pintado, y
 * una regla que sólo existe dentro de un `buildList` de un composable privado no se puede
 * testear sin levantar la UI entera. `DetailContent` se limita a llamarla.
 *
 * R-7: los pares opcionales se OMITEN si no hay valor. "Asignado a" es el único con valor
 * por defecto ("Sin asignar") y "Prioridad" nunca se oculta.
 */
internal fun detailPairs(
    actividad: ActividadDetalle,
    memberNames: Map<String, String>,
    children: List<VinculoChild> = emptyList(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<Pair<String, String>> = buildList {
    // D-4 · El detalle dice CUÁNDO: fecha + hora, y el rango cuando hay fin programado.
    formatDetailDateTime(actividad.scheduledStart, actividad.scheduledEnd, zone)
        ?.let { add("Fecha" to it) }
    formatDetailDate(actividad.deadline, zone)?.let { add("Límite evidencia" to it) }
    add(
        "Asignado a" to
            (actividad.assignedTo?.let { memberNames[it] } ?: "Sin asignar"),
    )
    // D-5 · Los hijos se resuelven contra `children`, NO contra `memberNames`: los hijos del
    // vínculo no son miembros y no están en ese mapa. Un id que no resuelve —vínculo todavía
    // cargando— omite el par en vez de pintar un uuid en pantalla.
    resolveChildNames(children, actividad.childrenIds)
        .takeIf { it.isNotEmpty() }
        ?.let { add("Para" to it.joinToString(", ")) }
    add("Prioridad" to criticalityLabel(actividad.criticality))
}
