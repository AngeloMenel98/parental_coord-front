package com.parental.shared.feature.activities.ui.create

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

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
    /** El fin existe SÓLO si hay fecha; la hora es la wall-clock del picker (A-14…A-16). */
    val endDate: LocalDate? = null,
    val endTime: TimeOfDay = TimeOfDay(10, 0),
)

enum class CreateField {
    TITLE,
    DESCRIPTION,
    CATEGORY,
    CHILDREN,
    DATE,
    END_DATE,
}

data class CreateFormError(val field: CreateField, val message: String)

/**
 * Antelación mínima del alta — regla del SERVIDOR (`ActivityPolicyService`). El front la
 * duplica para no dejar pulsar "Crear" y comerse un 400: por eso el mensaje es el MISMO
 * literal, palabra por palabra, y no una paráfrasis.
 */
internal const val MIN_LEAD_TIME_HOURS = 48

/** Literal EXACTO del backend. No reescribir: una prueba lo fija y el servidor lo compara. */
internal const val MIN_LEAD_TIME_MESSAGE =
    "La actividad debe programarse con al menos 48 horas de anticipación"

/**
 * Valida el formulario. Una lista VACÍA significa formulario válido.
 * Esta es la ÚNICA fuente de verdad del botón "Crear".
 *
 * [now] es inyectable para que las pruebas fijen el reloj: la regla de las
 * [MIN_LEAD_TIME_HOURS] horas es relativa al instante de validación.
 */
fun validateCreateForm(
    form: CreateForm,
    now: Instant = Clock.System.now(),
): List<CreateFormError> = buildList {
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
    val zone = TimeZone.currentSystemDefault()
    val start = form.startDate?.let { date ->
        LocalDateTime(date, LocalTime(form.startTime.hour, form.startTime.minute)).toInstant(zone)
    }
    if (start == null) {
        add(CreateFormError(CreateField.DATE, "Elige una fecha"))
    } else if (start < now + MIN_LEAD_TIME_HOURS.hours) {
        // ⚠ El límite es INCLUSIVO: exactamente 48h es válido. El servidor usa `<`, igual
        // que esta comparación; si aquí fuera `<=` rechazaríamos lo que el back acepta.
        add(CreateFormError(CreateField.DATE, MIN_LEAD_TIME_MESSAGE))
    }
    val endDate = form.endDate
    if (endDate != null && start != null) {
        val end = LocalDateTime(endDate, LocalTime(form.endTime.hour, form.endTime.minute))
            .toInstant(zone)
        if (end <= start) {
            add(
                CreateFormError(
                    CreateField.END_DATE,
                    "El fin programado debe ser posterior al inicio",
                ),
            )
        }
    }
}