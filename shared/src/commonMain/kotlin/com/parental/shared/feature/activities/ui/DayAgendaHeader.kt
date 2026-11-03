package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Antetítulo + titular del día — `DayAgendaHeader`. Se vivió en `DayAgenda.kt` hasta el
 * 2026-10-03, en que se separó: el título del día es otro trabajo que la lista de
 * actividades, y `DayAgenda.kt` ya sostenía las cuatro piezas de `RR-4`.
 *
 * 🔴 `RR-4` NO SE TOCA. Las cuatro piezas siguen siendo DECLARACIONES separadas — sólo
 * que ahora en dos ficheros. La reversibilidad de C9 (`OD-4`, ¿el nodo lleva título?)
 * depende de la DECLARACIÓN, no del fichero: separarlas aquí las protege igual.
 * El porqué de las cuatro vive en `DayAgenda.kt`.
 */

/**
 * Antetítulo + titular del día — `R1-T2-1` (antetítulo) y `R2-T2-2` (titular). El
 * antetítulo se escribe **`Agenda del día`** y se ve en versalitas gracias al
 * `fontFeatureSettings = "smcp"` de `labelSmall` (`Type.kt:103-109`): **NO se escribe en
 * mayúsculas a mano**, la fuente de verdad es la cadena con su capitalización natural y la
 * forma versal es un efecto de tipografía. Nodo de texto real, se anuncia una sola vez, **no**
 * lleva `contentDescription` y **no** se oculta de la tecnología asistiva: duplica nada,
 * porque las filas de abajo anuncian sus propios títulos.
 */
@Composable
internal fun DayAgendaHeader(date: LocalDate, activityCount: Int, zone: TimeZone) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Agenda del día",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
        )
        // R2-T2-2. Titular `weight(1f)` y conteo de ancho intrínseco: a 320dp el TITULAR se
        // elide, nunca el conteo. Éste no lleva `maxLines` porque el requisito es que no se
        // trunque a NINGÚN ancho. `TextAlign.End` + singular/plural: "1 actividad" / "N
        // actividades". Contar es `FR-13` — hasta T2-2 el número sólo existía en el
        // `contentDescription` fuera de pantalla de la celda de día, nunca en la UI visible.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatLongDate(date, currentDateIn(zone).year),
                style = TypeTokens.date,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            )
            Text(
                text = if (activityCount == 1) "1 actividad" else "$activityCount actividades",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        // R3-T2-3 · LA COPIA SIGUE AL TRATAMIENTO DE FILA QUE SE ENVÍA. Aquí nombra la
        // ACTIVIDAD porque la fila CON título (A4 provisional) es la que se renderiza; la
        // variante que nombra el icono sólo sería cierta para un nodo sin título
        // (`prd-v4:166` guioniza esa OTRA variante). `S3-T2-3a` es un grep LITERAL que
        // exige **0** aciertos sobre `shared/src/`, así que aquí la otra variante se
        // DESCRIBE y no se transcribe: citarla —incluso entre comillas, incluso en un
        // comentario— la convertiría en 1 y haría fallar el guard por algo que no es un
        // defecto del producto.
        // ⚠ LA COSTA DE C9 ASOMA AQUÍ: las DOS variantes están fijadas para que el futuro
        // flip sea un cambio de UNA cadena. Si C9 se confirma "sin título", cambia esta
        // línea y sólo esta. Es la costura que verifica T2-9.
        Text(
            text = "Toca una actividad para ver los detalles",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
