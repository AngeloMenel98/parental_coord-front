package com.parental.shared.feature.activities.ui.create

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * T-FE-05 — validación del formulario (D19).
 *
 * El formulario vive fuera de `CreateUiState` para que un error de red no borre lo que el
 * usuario escribió. Esta única función de validación es la **fuente de verdad** del botón
 * "Crear".
 *
 * ⚠ S3/T18 · El reloj se INYECTA (`now`). La regla de las 48 horas es relativa al instante de
 *   validación: con el reloj real, `startDate` caduca sola y la suite se pondría roja un día
 *   cualquiera sin que nadie tocara el código.
 */
class CreateFormValidationTest {

    /** Reloj fijo: cuatro días por delante de `validForm.startDate` en cualquier huso. */
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun validate(form: CreateForm) = validateCreateForm(form, now)

    private val validForm = CreateForm(
        title = "Ir al médico",
        description = "Revisión trimestral",
        categoryId = "cat-salud",
        childrenIds = setOf("c-1"),
        startDate = LocalDate(2026, 10, 5),
        startTime = TimeOfDay(9, 0),
    )

    /** Instante real del `startDate` del formulario en la zona del dispositivo. */
    private fun startInstant(form: CreateForm): Instant {
        val date = requireNotNull(form.startDate)
        return LocalDateTime(date, LocalTime(form.startTime.hour, form.startTime.minute))
            .toInstant(TimeZone.currentSystemDefault())
    }

    @Test
    fun `formulario vacio no es valido`() {
        val errors = validate(CreateForm())
        assertFalse(errors.isEmpty())
        // Debe marcar los campos obligatorios
        assertTrue(errors.any { it.field == CreateField.TITLE })
        assertTrue(errors.any { it.field == CreateField.CATEGORY })
        assertTrue(errors.any { it.field == CreateField.CHILDREN })
        assertTrue(errors.any { it.field == CreateField.DATE })
    }

    @Test
    fun `titulo con 2 caracteres es invalido`() {
        val errors = validate(validForm.copy(title = "AB"))
        assertTrue(errors.any { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 3 caracteres es valido`() {
        val errors = validate(validForm.copy(title = "ABC"))
        assertTrue(errors.none { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 80 caracteres es valido`() {
        val title = "A".repeat(80)
        val errors = validate(validForm.copy(title = title))
        assertTrue(errors.none { it.field == CreateField.TITLE })
    }

    @Test
    fun `titulo con 81 caracteres es invalido`() {
        val title = "A".repeat(81)
        val errors = validate(validForm.copy(title = title))
        assertTrue(errors.any { it.field == CreateField.TITLE })
    }

    @Test
    fun `descripcion con 501 caracteres es invalida`() {
        val desc = "A".repeat(501)
        val errors = validate(validForm.copy(description = desc))
        assertTrue(errors.any { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `descripcion con 500 caracteres es valida`() {
        val desc = "A".repeat(500)
        val errors = validate(validForm.copy(description = desc))
        assertTrue(errors.none { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `descripcion vacia es valida`() {
        val errors = validate(validForm.copy(description = ""))
        assertTrue(errors.none { it.field == CreateField.DESCRIPTION })
    }

    @Test
    fun `sin categoria es invalido`() {
        val errors = validate(validForm.copy(categoryId = null))
        assertTrue(errors.any { it.field == CreateField.CATEGORY })
    }

    @Test
    fun `sin hijos es invalido`() {
        val errors = validate(validForm.copy(childrenIds = emptySet()))
        assertTrue(errors.any { it.field == CreateField.CHILDREN })
    }

    @Test
    fun `sin fecha es invalido`() {
        val errors = validate(validForm.copy(startDate = null))
        assertTrue(errors.any { it.field == CreateField.DATE })
    }

    @Test
    fun `con mas de un hijo es valido`() {
        val errors = validate(validForm.copy(childrenIds = setOf("c-1", "c-2", "c-3")))
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
        val errors = validate(
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
        val errors = validate(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 5),
                endTime = TimeOfDay(8, 30),
            ),
        )
        assertTrue(errors.any { it.field == CreateField.END_DATE })
    }

    @Test
    fun `fin programado posterior al inicio es valido`() {
        val errors = validate(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 5),
                endTime = TimeOfDay(10, 0),
            ),
        )
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }

    @Test
    fun `fin programado al dia siguiente es valido`() {
        val errors = validate(
            validForm.copy(
                endDate = kotlinx.datetime.LocalDate(2026, 10, 6),
                endTime = TimeOfDay(8, 0),
            ),
        )
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }

    @Test
    fun `sin fin programado no se valida la fecha fin`() {
        val errors = validate(validForm.copy(endDate = null))
        assertTrue(errors.none { it.field == CreateField.END_DATE })
    }

    // ── antelación mínima de 48 h (S3/T18) ───────────────────────────────

    @Test
    fun `menos de 48 horas de antelacion es invalido con el mensaje exacto del servidor`() {
        val start = startInstant(validForm)

        val errors = validateCreateForm(validForm, start.minus(47.hours))

        assertEquals(
            "La actividad debe programarse con al menos 48 horas de anticipación",
            errors.first { it.field == CreateField.DATE }.message,
        )
    }

    @Test
    fun `justo 48 horas de antelacion es valido`() {
        // ⚠ LÍMITE INCLUSIVO: el servidor compara con `<`, no con `<=`. Rechazar las 48h
        //   exactas sería una regla MÁS dura que la del backend: el front se adelanta al 400,
        //   no inventa prohibiciones.
        val start = startInstant(validForm)

        val errors = validateCreateForm(validForm, start.minus(48.hours))

        assertTrue(errors.none { it.field == CreateField.DATE })
    }

    @Test
    fun `mas de 48 horas de antelacion es valido`() {
        val start = startInstant(validForm)

        val errors = validateCreateForm(validForm, start.minus(49.hours))

        assertTrue(errors.none { it.field == CreateField.DATE })
    }

    @Test
    fun `el mensaje de antelacion es una constante unica, no un literal duplicado`() {
        // Si alguien "mejora" la redacción en un sitio y no en el otro, el usuario acabaría
        // viendo un texto distinto del que devuelve el servidor ante el mismo formulario.
        assertEquals(
            "La actividad debe programarse con al menos 48 horas de anticipación",
            MIN_LEAD_TIME_MESSAGE,
        )
    }
}