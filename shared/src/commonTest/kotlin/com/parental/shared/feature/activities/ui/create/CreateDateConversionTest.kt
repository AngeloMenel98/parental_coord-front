package com.parental.shared.feature.activities.ui.create

import com.parental.shared.feature.activities.ui.create.NoPastDatesFrom
import com.parental.shared.feature.activities.ui.create.formatDdMmAaaa
import com.parental.shared.feature.activities.ui.create.pickerMillisToDate
import com.parental.shared.feature.activities.ui.create.toIsoWithOffset
import com.parental.shared.feature.activities.ui.create.toPickerMillis
import com.parental.shared.feature.activities.ui.create.todayUtcMillis
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-FE-03 — conversión fecha↔instante (D22).
 *
 * El `DatePicker` de material3 habla en **milis UTC a medianoche**. Redondear por la zona del
 * dispositivo desplaza el día un día entero en los husos negativos (UTC-3): 2026-09-30T00:00Z
 * es 29/09 21:00 en São Paulo. Estos tests existen para que ese desplazamiento NO vuelva.
 */
class CreateDateConversionTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo") // UTC-3

    @Test
    fun `el picker marca el dia a medianoche UTC`() {
        val millis = LocalDate(2026, 9, 30).toPickerMillis()

        assertEquals("2026-09-30T00:00:00Z", Instant.fromEpochMilliseconds(millis).toString())
    }

    @Test
    fun `el round trip no corre el dia en un dispositivo con offset negativo`() {
        val date = LocalDate(2026, 9, 30)
        val millis = date.toPickerMillis()

        // ⚠ Leer esos millis con la ZONA DEL DISPOSITIVO sí correría el día: -03:00 deja el
        //   instante en 29/09 21:00 locales. Por eso el inverso es toLocalDateTime(UTC), no
        //   la zona por defecto (D22).
        assertEquals(
            LocalDate(2026, 9, 29),
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(saoPaulo).date,
        )

        // El inverso correcto devuelve el día que el usuario eligió.
        assertEquals(date, pickerMillisToDate(millis))
    }

    @Test
    fun `las dos direcciones son inversas`() {
        val dates = listOf(
            LocalDate(2026, 1, 1),
            LocalDate(2026, 2, 28),
            LocalDate(2026, 12, 31),
            LocalDate(2028, 2, 29),
        )

        dates.forEach { date ->
            assertEquals(date, pickerMillisToDate(date.toPickerMillis()), "round trip de $date")
        }
    }

    @Test
    fun `todayUtcMillis cae en la medianoche UTC`() {
        val millis = todayUtcMillis()
        val utc = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)

        assertEquals(0, utc.hour)
        assertEquals(0, utc.minute)
        assertEquals(0, utc.second)
        assertEquals(millis % 86_400_000L, 0L, "medianoche UTC ⇒ múltiplo exacto de 86.400.000")
        assertEquals(Clock.System.now().toLocalDateTime(TimeZone.UTC).date, pickerMillisToDate(millis))
    }

    @Test
    fun `el dia de hoy es seleccionable pero el de ayer no`() {
        // ⚠ `>=` y NUNCA `>`: con `>` el picker bloquearía el propio día de hoy (el
        //   off-by-one al revés) y el usuario no podría agendar para hoy.
        val today = todayUtcMillis()
        val selectable = NoPastDatesFrom(today)

        assertTrue(selectable.isSelectableDate(today), "hoy debe ser seleccionable")
        assertTrue(!selectable.isSelectableDate(today - 86_400_000L), "ayer no debe ser seleccionable")
        assertTrue(selectable.isSelectableYear(pickerMillisToDate(today).year))
        assertTrue(!selectable.isSelectableYear(pickerMillisToDate(today).year - 1))
    }

    @Test
    fun `toIsoWithOffset convierte con la zona del usuario y emite siempre Z`() {
        val iso = toIsoWithOffset(
            date = LocalDate(2026, 10, 5),
            time = TimeOfDay(10, 0),
            zone = saoPaulo,
        )

        // 10:00 -03:00 es 13:00Z: el servidor normaliza a UTC, no a la zona del cliente.
        assertEquals("2026-10-05T13:00:00Z", iso)
        assertTrue(iso.endsWith("Z"), "ISO-8601 con offset explícito obligatorio: $iso")
    }

    @Test
    fun `toIsoWithOffset con zona UTC tambien emite Z`() {
        val iso = toIsoWithOffset(
            date = LocalDate(2026, 10, 5),
            time = TimeOfDay(9, 30),
            zone = TimeZone.UTC,
        )

        assertEquals("2026-10-05T09:30:00Z", iso)
    }

    @Test
    fun `la fecha se presenta en dd mm aaaa`() {
        // AS-10: formato FIJO, no el de la configuración regional.
        assertEquals("01/10/2026", formatDdMmAaaa(LocalDate(2026, 10, 1)))
        assertEquals("09/09/2026", formatDdMmAaaa(LocalDate(2026, 9, 9)))
        assertEquals("31/12/2028", formatDdMmAaaa(LocalDate(2028, 12, 31)))
    }
}