package com.parental.shared.feature.activities.ui.create

import kotlinx.datetime.LocalDate

/*
 * Estado del formulario de alta — `create-activity`, T-FE-06.
 *
 * ⚠ D19, EL FORMULARIO VIVE FUERA DE `CreateUiState` (que está en `ActivitiesViewModel.kt`,
 *   junto a `DetailUiState`). Un error de red NO puede perder los datos ya escritos —requisito
 *   explícito del spec— y además la UI nunca necesita `!!` para leer un campo.
 * ⚠ D20, `childrenIds` es un `Set<String>`: la multi-selección no puede producir `[a, a]`
 *   POR CONSTRUCCIÓN, no por una comprobación que alguien pueda saltarse.
 */

/** Hora del usuario, sin zona: es la wall-clock que el usuario ve en el picker. */
data class TimeOfDay(val hour: Int, val minute: Int)

/** El formulario. Sus defaults son los del modal recién abierto. */
data class CreateForm(
    val title: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val childrenIds: Set<String> = emptySet(),
    /** `null` == "Sin asignar" (AS-3: la clave no viaja en el cuerpo). */
    val assignedTo: String? = null,
    val startDate: LocalDate? = null,
    val startTime: TimeOfDay = TimeOfDay(9, 0),
)

enum class CreateField {
    TITLE,
    DESCRIPTION,
    CATEGORY,
    CHILDREN,
    DATE,
}

data class CreateFormError(val field: CreateField, val message: String)

/**
 * Valida el formulario. Una lista VACÍA significa formulario válido.
 * Esta es la ÚNICA fuente de verdad del botón "Crear".
 */
fun validateCreateForm(form: CreateForm): List<CreateFormError> = buildList {
    val trimmedTitle = form.title.trim()
    if (trimmedTitle.length < 3) {
        add(CreateFormError(CreateField.TITLE, "Mínimo 3 caracteres"))
    }
    if (form.title.length > 80) {
        add(CreateFormError(CreateField.TITLE, "Máximo 80 caracteres"))
    }
    if (form.description.length > 500) {
        add(CreateFormError(CreateField.DESCRIPTION, "Máximo 500 caracteres"))
    }
    if (form.categoryId == null) {
        add(CreateFormError(CreateField.CATEGORY, "Elige una categoría"))
    }
    if (form.childrenIds.isEmpty()) {
        add(CreateFormError(CreateField.CHILDREN, "Elige al menos un hijo"))
    }
    if (form.startDate == null) {
        add(CreateFormError(CreateField.DATE, "Elige una fecha"))
    }
}