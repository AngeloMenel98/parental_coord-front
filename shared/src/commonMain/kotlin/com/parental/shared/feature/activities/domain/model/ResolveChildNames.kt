package com.parental.shared.feature.activities.domain.model

import com.parental.shared.feature.bonds.domain.model.VinculoChild

/**
 * Resuelve los ids de hijos de una actividad a sus nombres visibles.
 *
 * Consumido por la tarjeta de actividad (T08) y la hoja de detalle (T09).
 *
 * @param children Lista de hijos ya cargados en la vista (`ActivitiesViewModel.children`).
 * @param ids Lista de ids tal como viene del backend; el orden se respeta.
 * @return Lista de nombres "Nombre Apellido" (sin partes en blanco, sin duplicar espacios).
 *         Los ids que no correspondan a un hijo visible se omiten, y también los hijos
 *         sin nombre legible: una etiqueta vacía en la tarjeta es ruido, no información.
 */
internal fun resolveChildNames(
    children: List<VinculoChild>,
    ids: List<String>,
): List<String> {
    val childById = children.associateBy { it.id }
    return ids.mapNotNull { id ->
        childById[id]?.let { child ->
            listOfNotNull(
                child.firstName.trim().takeIf { it.isNotBlank() },
                child.lastName.trim().takeIf { it.isNotBlank() },
            ).joinToString(" ").takeIf { it.isNotBlank() }
        }
    }
}
