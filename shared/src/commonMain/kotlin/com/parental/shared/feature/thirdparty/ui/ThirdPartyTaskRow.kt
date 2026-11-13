package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.ui.formatAgendaDay
import com.parental.shared.feature.activities.ui.formatAgendaMonth
import com.parental.shared.feature.activities.ui.formatTime
import com.parental.shared.feature.activities.ui.statusSemanticLabel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * La fila de una tarea del terciario.
 *
 * **Es la fila de agenda de los progenitores, con una diferencia: la acción es un botón
 * al final, no un gesto.** La anatomía de la agenda se respeta al punto de usar sus
 * mismas medidas (`AgendaGeometry.dateColumn`, el punto de categoría, el tiempo en
 * `TypeTokens.time`) porque el artboard pide explícitamente no inventar una fila nueva;
 * lo que NO se reutiliza es el contenedor de swipe, porque el terciario no puede
 * deslizar nada (S8).
 *
 * La acción vive HERMANA del cuerpo clickeable, nunca dentro: dentro quedaría fusionada
 * en la semántica de la fila y TalkBack anunciaría un solo objetivo donde hay dos (el
 * mismo contrato que ya documenta `ConfirmarChip` en la agenda de los progenitores).
 */
@Composable
internal fun ThirdPartyTaskRow(
    actividad: Actividad,
    zone: TimeZone,
    resolve: (String) -> CategoryInfo?,
    action: ThirdPartyTaskAction,
    showDateColumn: Boolean,
    onClick: () -> Unit,
    onFinalize: () -> Unit,
) {
    val localStart = remember(actividad.scheduledStart, zone) {
        actividad.scheduledStart?.let { raw ->
            runCatching { Instant.parse(raw).toLocalDateTime(zone) }.getOrNull()
        }
    }
    val time = localStart?.let { formatTime(it.hour, it.minute) }
    // El rótulo hablado se compone de lo que la fila dice en palabras: título, hora y
    // estado. El color nunca es la única señal, así que el estado también se dice.
    val spokenLabel = listOfNotNull(
        actividad.title,
        time,
        statusSemanticLabel(actividad.status),
    ).joinToString(", ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(ParentalShapes.medium)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = spokenLabel }
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDateColumn) {
                Column(
                    modifier = Modifier.width(AgendaGeometry.dateColumn),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = localStart?.let { formatAgendaDay(it.date) } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (localStart != null) {
                        Text(
                            text = formatAgendaMonth(localStart.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TagDot(
                color = categoryTagTriple(resolve(actividad.categoryId))?.dot
                    ?: MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(AgendaGeometry.lineNode),
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = actividad.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (time != null) {
                        Text(
                            text = time,
                            style = TypeTokens.time,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusTag(status = actividad.status)
                }
            }
        }
        ThirdPartyTrailingAction(
            action = action,
            title = actividad.title,
            onFinalize = onFinalize,
        )
    }
}

/**
 * La acción de la fila. `FINALIZE` es un botón de verdad porque es la única escritura que
 * el terciario puede hacer; el resto son etiquetas, no botones deshabilitados: un control
 * gris invita a intentarlo, una palabra no.
 */
@Composable
private fun ThirdPartyTrailingAction(
    action: ThirdPartyTaskAction,
    title: String,
    onFinalize: () -> Unit,
) {
    when (action) {
        ThirdPartyTaskAction.FINALIZE -> TextButton(
            onClick = onFinalize,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.md,
                vertical = Spacing.xs,
            ),
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        ThirdPartyTaskAction.WAITING,
        ThirdPartyTaskAction.NONE,
        ThirdPartyTaskAction.CLOSED,
        -> Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = Spacing.sm)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .semantics { contentDescription = "$title: ${action.label}" },
        )
    }
}
