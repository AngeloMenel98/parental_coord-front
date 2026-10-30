package com.parental.shared.feature.activities.ui.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-FE-05 — validación del formulario (D19).
 *
 * El formulario vive fuera de `CreateUiState` para que un error de red no borre lo que el
 * usuario escribió. Esta única función de validación es la **fuente de verdad** del botón
 * "Crear".
 */
class CreateFormValidationTest {

    private val validForm = CreateForm(
        title = "Ir al médico",
        description = "Revisión trimestral",
        categoryId = "cat-salud",
        childrenIds = setOf("c-1"),
        startDate = kotlinx.datetime.LocalDate(2026, 10, 5),
        startTime = TimeOfDay(9, 0),
    )

    @Test
    fun `formulario vacio no es valido`() {
        val errors = validateCreateForm(CreateForm())
        assertFalse(errors.isEmpty())
        // Debe marcar los campos obligatorios
        assertTrue(errors.any { it.field == CreateField.TITLE })
        assertTrue(errors.any { it.field == CreateField.CATEGORY })
        assertTrue(errors.any { it.field == CreateField.CHILDREN })
        assertTrue(errors.any { it.field == CreateField.DATE })
    }

    @Test
    fun `titulo con 2 caracteres es invalido`() {
        val errors = validateCreateForm(validForm.copy(title = "AB"))
        assertTrue(errors.any { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 3 caracteres es valido`() {
        val errors = validateCreateForm(validForm.copy(title = "ABC"))
        assertTrue(errors.none { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 80 caracteres es valido`() {
        val title = "A".repeat(80)
        val errors = validateCreateForm(validForm.copy(title = title))
        assertTrue(errors.none { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 81 caracteres es invalido`() {
        val title = "A".repeat(81)
        val errors = validateCreateForm(validForm.copy(title = title))
        assertTrue(errors.any { it.field == CreateField.TITLE })
    }

    @Test
    fun `descripcion con 501 caracteres es invalida`() {
        val desc = "A".repeat(501)
        val errors = validateCreateForm(validForm.copy(description = desc))
        assertTrue(errors.any { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `descripcion con 500 caracteres es valida`() {
        val desc = "A".repeat(500)
        val errors = validateCreateForm(validForm.copy(description = desc))
        assertTrue(errors.none { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `descripcion vacia es valida`() {
        val errors = validateCreateForm(validForm.copy(description = ""))
        assertTrue(errors.none { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `sin categoria es invalido`() {
        val errors = validateCreateForm(validForm.copy(categoryId = null))
        assertTrue(errors.any { it.field == CreateField.CATEGORY })
    }

    @Test
    fun `sin hijos es invalido`() {
        val errors = validateCreateForm(validForm.copy(childrenIds = emptySet()))
        assertTrue(errors.any { it.field == CreateField.CHILDREN })
    }

    @Test
    fun `sin fecha es invalido`() {
        val errors = validateCreateForm(validForm.copy(startDate = null))
        assertTrue(errors.any { it.field == CreateField.DATE })
    }

    @Test
    fun `con mas de un hijo es valido`() {
        val errors = validateCreateForm(validForm.copy(childrenIds = setOf("c-1", "c-2", "c-3")))
        assertTrue(errors.none { it.field == CreateField.CHILDREN })
    }

    @Test
    fun `set impide duplicados por construccion`() {
        val set = setOf("a", "a", "b")
        assertEquals(2, set.size)
        assertTrue(set.contains("a"))
        assertTrue(set.contains("b"))
    }

    @Test
    fun `fin programado igual al inicio es invalido`() {
        val errors = validateCreateForm(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 5),
                endTime = TimeOfDay(9, 0),
            ),
        )
        assertTrue(errors.any { it.field == CreateField.END_DATE })
        assertEquals(
            "El fin programado debe ser posterior al inicio",
            errors.first { it.field == CreateField.END_DATE }.message,
        )
    }

    @Test
    fun `fin programado anterior al inicio es invalido`() {
        val errors = validateCreateForm(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 5),
                endTime = TimeOfDay(8, 30),
            ),
        )
        assertTrue(errors.any { it.field == CreateField.END_DATE })
    }

    @Test
    fun `fin programado posterior al inicio es valido`() {
        val errors = validateCreateForm(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 5),
                endTime = TimeOfDay(10, 0),
            ),
        )
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }

    @Test
    fun `fin programado al dia siguiente es valido`() {
        val errors = validateCreateForm(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 6),
                endTime = TimeOfDay(8, 0),
            ),
        )
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }

    @Test
    fun `sin fin programado no se valida la fecha fin`() {
        val errors = validateCreateForm(validForm.copy(endDate = null))
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }
}