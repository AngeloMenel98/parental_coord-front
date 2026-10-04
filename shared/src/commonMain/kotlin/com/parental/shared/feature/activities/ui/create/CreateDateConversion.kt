package com.parental.shared.feature.activities.ui.create

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import com.parental.shared.feature.activities.domain.model.CreateActividadRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/*
 * Conversión fecha↔instante del modal de alta — `create-activity`, T-FE-04.
 *
 * ⚠ D22, TODO el ida y vuelta del `DatePicker` es en UTC. El picker marca el día elegido con
 *   milisegundos a la MEDIANOCHE UTC, así que:
 *     · ida  → `atStartOfDayIn(TimeZone.UTC)`. Con la zona del dispositivo, un huso NEGATIVO
 *              (UTC-3) devuelve 29/09T21:00Z para el 30 de septiembre y el día se corre.
 *     · vuelta → `toLocalDateTime(TimeZone.UTC).date`. A la inversa se lee, con la zona local,
 *              30/09T00:00Z como 29/09 21:00 y también se corre.
 *   Los dos sentidos están cubiertos por `CreateDateConversionTest`.
 *
 * ⚠ `Instant.toString()` emite ISO-8601 con offset EXPLÍCITO y siempre `Z`: es lo que el
 *   validador del backend exige. Convertir con la zona del usuario y luego `toString()` produce
 *   el instante correcto normalizado a UTC (10:00 -03:00 → `13:00:00Z`), no la wall-clock.
 */

/** [LocalDate] → milis UTC a medianoche, que es lo que el picker consume y devuelve. */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Inverso EXACTO de [toPickerMillis]. ⚠ NUNCA `.date` sobre el instante con la zona por
 * defecto: en un dispositivo con offset negativo devuelve el día anterior.
 */
internal fun pickerMillisToDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

/** Medianoche UTC de hoy — el piso del [NoPastDatesFrom]. */
internal fun todayUtcMillis(): Long =
    Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toPickerMillis()

/** Wall-clock del usuario → ISO-8601 con offset explícito (`…Z`), exigido por el backend. */
internal fun toIsoWithOffset(date: LocalDate, time: TimeOfDay, zone: TimeZone): String =
    LocalDateTime(date, LocalTime(time.hour, time.minute)).toInstant(zone).toString()

/**
 * `dd/mm/aaaa` fijo — AS-10. kotlinx-datetime 0.6.1 no trae formateador, así que el `padStart`
 * va a mano, igual que `formatAgendaDay` (`TimelineFormatters.kt:74`).
 */
internal fun formatDdMmAaaa(date: LocalDate): String =
    "${date.dayOfMonth.toString().padStart(2, '0')}/" +
        "${date.monthNumber.toString().padStart(2, '0')}/${date.year}"

/**
 * Fechas no pasadas para el [androidx.compose.material3.DatePicker].
 *
 * ⚠ [isSelectableDate] usa `>=` y NUNCA `>`: con `>` el propio día de hoy quedaría bloqueado.
 * ⚠ [todayMillis] se calcula UNA vez por vida de la hoja (`remember { NoPastDatesFrom(todayUtcMillis()) }`),
 *   nunca dentro del predicado: el picker consulta esa lambda por celda y por frame, así que
 *   un "hoy" recalculado en cada llamada movería la frontera DENTRO del mismo frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class NoPastDatesFrom(val todayMillis: Long) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis

    @OptIn(ExperimentalMaterial3Api::class)
    override fun isSelectableYear(year: Int): Boolean = year >= pickerMillisToDate(todayMillis).year
}

/**
 * Convierte el formulario a `CreateActividadRequest` (T-FE-08). `criticality` **nunca** aparece
 * en el cuerpo (AS-3). `assignedTo` se deja como `null` cuando no viene (se omite semánticamente).
 * `description` pasa a `null` si está vacía (no `""`). `scheduledStart` termina SIEMPRE en `Z`.
 */
internal fun CreateForm.toRequest(): com.parental.shared.feature.activities.domain.model.CreateActividadRequest {
    val trimmedTitle = title.trim()
    val trimmedDesc = description.trim()
    val zone = TimeZone.currentSystemDefault()

    return CreateActividadRequest(
        title = trimmedTitle,
        description = if (trimmedDesc.isEmpty()) null else trimmedDesc,
        type = "event",
        categoryId = requireNotNull(categoryId) { "categoryId es obligatorio" },
        assignedTo = assignedTo,
        scheduledStart = toIsoWithOffset(requireNotNull(startDate) { "startDate es obligatorio" }, startTime, zone),
        scheduledEnd = null,
        childrenIds = childrenIds.toList(),
    )
}