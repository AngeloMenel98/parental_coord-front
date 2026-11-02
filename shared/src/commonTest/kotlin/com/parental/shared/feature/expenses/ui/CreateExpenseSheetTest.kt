package com.parental.shared.feature.expenses.ui

import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Lógica del formulario de alta de gastos (S6/T27).
 *
 * No toca Compose: este repo no tiene harness de UI en commonTest. Valida la
 * máquina de estados del formulario, las reglas de validación y la conversión a
 * request.
 */
class CreateExpenseSheetTest {

    @Test
    fun `formulario vacio falla en los campos obligatorios`() {
        val form = CreateExpenseForm()

        val errors = validateCreateExpenseForm(form)

        assertNotNull(errors.find { it.field == CreateExpenseField.CONCEPT })
        assertNotNull(errors.find { it.field == CreateExpenseField.AMOUNT })
        assertNotNull(errors.find { it.field == CreateExpenseField.CATEGORY })
        // La fecha defaultea a hoy, así que un formulario recién abierto no falla por fecha.
    }

    @Test
    fun `la categoria muestra el mensaje de la casa`() {
        val form = CreateExpenseForm()

        val error = validateCreateExpenseForm(form).firstOrNull { it.field == CreateExpenseField.CATEGORY }

        assertEquals("Elige una categoría", error?.message)
    }

    @Test
    fun `la fecha muestra el mensaje de la casa`() {
        val form = CreateExpenseForm(expenseDate = null)

        val error = validateCreateExpenseForm(form).firstOrNull { it.field == CreateExpenseField.DATE }

        assertEquals("Elige una fecha", error?.message)
    }

    @Test
    fun `monto en cero no es valido`() {
        val form = validForm().copy(amount = "0")

        val error = validateCreateExpenseForm(form).firstOrNull { it.field == CreateExpenseField.AMOUNT }

        assertNotNull(error)
    }

    @Test
    fun `monto negativo no es valido`() {
        val form = validForm().copy(amount = "-150")

        val error = validateCreateExpenseForm(form).firstOrNull { it.field == CreateExpenseField.AMOUNT }

        assertNotNull(error)
    }

    @Test
    fun `split por defecto es 50 y es valido`() {
        val form = CreateExpenseForm()

        assertEquals("50", form.splitRatioPayer)
        assertNull(validateCreateExpenseForm(form).firstOrNull { it.field == CreateExpenseField.SPLIT })
    }

    @Test
    fun `split fuera de 1 a 99 no es valido`() {
        assertNotNull(
            validateCreateExpenseForm(validForm().copy(splitRatioPayer = "0"))
                .firstOrNull { it.field == CreateExpenseField.SPLIT },
        )
        assertNotNull(
            validateCreateExpenseForm(validForm().copy(splitRatioPayer = "100"))
                .firstOrNull { it.field == CreateExpenseField.SPLIT },
        )
    }

    @Test
    fun `notas en blanco son opcionales`() {
        val form = validForm().copy(description = "   ")

        val errors = validateCreateExpenseForm(form)

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `formulario valido no tiene errores`() {
        val errors = validateCreateExpenseForm(validForm())

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `conversion a request mantiene la fecha ISO`() {
        val request = validForm().toRequest()

        assertNotNull(request)
        assertEquals("2026-04-15", request.expenseDate)
    }

    @Test
    fun `conversion a request envia solo la parte del pagador`() {
        val request = validForm().copy(splitRatioPayer = "60").toRequest()

        assertEquals(60, request?.splitRatioPayer)
    }

    @Test
    fun `la descripcion vacia se omite del request`() {
        val request = validForm().copy(description = "  ").toRequest()

        assertNull(request?.description)
    }

    private fun validForm() = CreateExpenseForm(
        concept = "Cuota escolar",
        amount = "1250,50",
        categoryId = "cat-1",
        expenseDate = LocalDate(2026, 4, 15),
        description = "Marzo",
    )
}
