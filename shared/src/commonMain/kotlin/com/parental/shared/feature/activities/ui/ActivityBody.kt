package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.components.isDoneStatus
import com.parental.shared.core.ui.components.statusTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import kotlinx.datetime.TimeZone

/*
 * Cuerpo de la fila de actividad — `calendar-design-redesign`, T4 (paso 6). Archivo 10.
 *
 * Vive en su propio archivo por la válvula de escape de T4-8, no por diseño propio.
 * Lo comparten la fila de Agenda (F4) y la fila de día que T2 reescribirá — es la fila
 * que T3 más toca, así que tenerla aislada es justo lo que T3 necesita.
 *
 * `@OptIn(ExperimentalLayoutApi::class)` viaja desde el origen (`FlowRow`), DD-2.
 *
 * ⚠ Tokens sin cambio de valor: `touchTargetLegacy` sigue en 44dp (T3-8 → 48).
 *
 * T5-1 / T5-2: el emoji deja de RENDERIZARSE y en su slot hay exactamente UN vector
 * Material. `CategoryInfo.emoji` sigue existiendo (`CategoryResolver.kt` es de A1) —
 * lo que deja de pintarse es el emoji, no el campo.
 */

/**
 * Vector canónico de la categoría [name], o `null` si no es una de las seis
 * categorías sembradas — `R1-T5-1`, tabla única y local a este archivo.
 *
 * Las claves son los **nombres canónicos ES** de `prd-v4:190-197`. La tabla es
 * deliberadamente una lista de strings, no un `when` sobre `CategoryTokens`:
 * RC-7 prohíbe que `feature/activities/` nombre los objetos de token, y `null`
 * (categoría desconocida) es la señal que T5-2 necesita para pintar el fallback.
 *
 * ⚠ ACOPLAMIENTO CONOCIDO: estas seis cadenas repiten los `esName` de
 * `CategoryTokens.Category`. Si una cambia allí, cambia aquí. Se acepta porque
 * RC-7 cierra la puerta de objeto y la tabla tiene que ser *este* archivo — una
 * fuente compartida tendría que ser un token nuevo, y DD-10 no autoriza ese
 * token en T5. Un nombre no canónico cae en `null` → `Label` en `outline`, que
 * es el fallback correcto de C1, no un error.
 */
internal fun categoryVector(name: String?): ImageVector? = when (name) {
    "Salud" -> Icons.Filled.Favorite
    "Educación" -> Icons.Filled.Edit
    "Familiar" -> Icons.Filled.Home
    "Social" -> Icons.Filled.TheaterComedy
    "Recreación" -> Icons.Filled.Celebration
    "Otros" -> Icons.Filled.Settings
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityBody(
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    semanticLabel: String,
    showTime: Boolean,
    showStatusDot: Boolean = false,
    showCategoryIcon: Boolean = true,
    showMetaChips: Boolean = true,
    showChevron: Boolean = true,
    showConfirmChip: Boolean = false,
    canConfirm: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    isConfirming: Boolean = false,
) {
    val isDone = isDoneStatus(row.actividad.status)
    // R1-T5-1: un vector por fila, en el slot que ocupaba el emoji. R2-T5-2: el
    // fallback NUNCA es un slot vacío — una categoría desconocida se dibuja.
    val vector = categoryVector(info?.name)
    // El tamaño sale de `titleMedium` — el mismo estilo con el que se pintaba el
    // emoji — para no inventar un token de radio/borde que ninguna tarea posee y
    // para que el título no desplace al cambiar de glifo (`S1-T5-1a`: 1 icono).
    val vectorSize = MaterialTheme.typography.titleMedium.fontSize.value.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AgendaGeometry.touchTargetLegacy)
            .clip(ParentalShapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // R1-T5-1: un vector por fila, en el slot que ocupaba el emoji. R2-T5-2: el
        // fallback NUNCA es un slot vacío — una categoría desconocida se dibuja.
        //
        // `showCategoryIcon = false` (T2-6) es la costura con F7: allí el icono lo
        // pinta `AgendaNode` en su disco de 40dp, y dibujarlo aquí además duplicaría
        // el glifo. El default `true` deja a F4 exactamente como estaba — T2 no puede
        // cambiar la anatomía de la fila de Agenda, que es de T5.
        if (showCategoryIcon) {
            Icon(
                imageVector = vector ?: Icons.AutoMirrored.Filled.Label,
                // DECORATIVO: el rótulo fusionado de la fila ya nombra la categoría
                // en palabras, así que un nodo para el icono sería duplicación (AC-5).
                contentDescription = null,
                // Categoría conocida → su color de token. Desconocida → `outline`
                // (4.37:1 claro / 5.83:1 oscuro), NUNCA el `dot` de otra categoría:
                // el fallback no debe insinuar una categoría que no tiene.
                tint = if (vector != null) {
                    categoryTagTriple(info)?.dot ?: MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(vectorSize)
                    .clearAndSetSemantics { },
            )
            if (showStatusDot) {
                Spacer(modifier = Modifier.width(Spacing.xxs))
                TagDot(
                    color = statusTagTriple(row.actividad.status)?.dot
                        ?: MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(AgendaGeometry.statusBadge),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (showTime && row.localStart != null) {
                Text(
                    text = formatTime(row.localStart.hour, row.localStart.minute),
                    style = TypeTokens.time,
                    // R6-T3-6: la hora deja de competir con el título. `primary`
                    // (6.33:1 claro / 10.89:1 oscuro) NO era un problema de
                    // contraste — AA de sobra — sino de ATENCIÓN: era el único
                    // `primary` de la fila, así que el ojo iba a la hora y se
                    // perdía el título, que es lo que `prd-v4:100` pide primero
                    // ("el título es lo que más pesa"). `onSurfaceVariant` mide
                    // 9.18:1 / 10.85:1: más legible, y sin jerarquía falsa.
                    // `SemiBold` se conserva para que la jerarquía se lea por
                    // PESO, no sólo por color — el color nunca es la única señal.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = row.actividad.title,
                style = MaterialTheme.typography.bodyLarge,
                // R3-T3-3: el énfasis depende del estado. `Done` se atenúa; todo
                // lo demás no. Esto cambia peso y color, NUNCA `maxLines`, y
                // NUNCA el orden (C7 es permanente: las filas completadas siguen
                // siendo cronológicas para siempre).
                //
                // La atenuación no es ilegibilidad: `onSurfaceVariant` sobre
                // `surface` mide 9.18:1 claro / 10.85:1 oscuro. Y el color nunca
                // es la única señal — el chip `StatusTag` y la palabra de estado
                // del rótulo accesible siguen ahí.
                fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                color = if (isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.deadline?.let { deadline ->
                Text(
                    text = "Límite: ${formatDeadline(deadline, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // R2-T2-6 · LA FILA DEL DÍA NO LLEVA CHIPS DE CATEGORÍA NI DE ESTADO. En F7 la
            // categoría ya la dice el nodo de 40dp y el estado, su punto sobre el collar;
            // repetir ambos como `CategoryBadge`/`StatusTag` sería la misma información
            // dos veces en la misma fila. `showMetaChips = false` lo apaga SÓLO para F7: el
            // default `true` deja la fila de Agenda (F4) exactamente como la dejó T5, que
            // es la anatomía que esa pantalla envía y T2 no puede tocar.
            if (showMetaChips) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CategoryBadge(
                        categoryId = row.actividad.categoryId,
                        resolve = { info },
                    )
                    StatusTag(status = row.actividad.status)
                }
            }
        }
        // R2-T2-8 · EL CHIP DE CONFIRMAR ES HERMANO DEL CUERPO, NO HIJO. Se dibuja
        // AQUÍ, dentro del `Row` pero FUERA del `Modifier.clickable` del cuerpo, para que
        // la fila siga siendo UN nodo de semántica y el chip el OTRO — no anidarlos en
        // un `Column` como hacía `WeekActivityNode`, donde el chip quedaba dentro del
        // área táctil de la actividad y una pulsación cerca de él abriría el detalle en
        // vez de confirmar. `showConfirmChip = true` + `canConfirm` reproduce aquí el
        // mismo par, y F4 sigue sin pasarlos (defaults `false`) ⇒ F4 no cambia.
        if (showConfirmChip && canConfirm) {
            Spacer(modifier = Modifier.width(Spacing.xxs))
            ConfirmarChip(
                onClick = onConfirm,
                enabled = !isConfirming,
                isConfirming = isConfirming,
            )
        }
        if (showChevron && !canConfirm) {
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
