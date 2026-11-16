package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.files.rememberFilePicker
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.activities.ui.detail.DetailCard
import com.parental.shared.feature.activities.ui.formatAgendaDate
import com.parental.shared.feature.activities.ui.formatTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Detalle de una actividad del terciario — artboard `03-permiso-explicito`, tercera
 * pantalla.
 *
 * El pie tiene **exactamente una acción de escritura**: finalizar. No se puede marcar
 * inasistencia, reasignar, cancelar ni borrar: esas decisiones son del progenitor que
 * asignó (S7/S8). Adjuntar archivos es la otra capacidad (S10) y también es del asignado.
 *
 * La explicación de las prohibiciones no vive acá adentro: se abre desde el icono de
 * información del encabezado (decisión L7 del canvas), así la hoja operativa no tiene
 * párrafos que nadie lee dos veces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThirdPartyActivitySheet(
    detail: ThirdPartyDetail,
    ownerName: String?,
    resolve: (String) -> CategoryInfo?,
    currentUserId: String,
    zone: TimeZone,
    isCompleting: Boolean,
    onDismiss: () -> Unit,
    onFinalize: (String) -> Unit,
    onAddAttachment: (String, String, String, ByteArray) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ParentalShapes.extraLarge,
    ) {
        when (detail) {
            ThirdPartyDetail.Closed -> Unit

            is ThirdPartyDetail.Loading -> ContentListSkeleton(
                contentDescription = "Cargando la actividad",
                itemCount = 3,
            )

            is ThirdPartyDetail.Failed -> EmptyContentState(
                title = "No pudimos abrir la actividad",
                message = detail.message,
            )

            is ThirdPartyDetail.Content -> DetailBody(
                detalle = detail.detalle,
                attachments = detail.attachments,
                ownerName = ownerName,
                resolve = resolve,
                currentUserId = currentUserId,
                zone = zone,
                isCompleting = isCompleting,
                onFinalize = onFinalize,
                onAddAttachment = onAddAttachment,
            )
        }
    }
}

@Composable
private fun DetailBody(
    detalle: ActividadDetalle,
    attachments: List<ActivityAttachmentDto>,
    ownerName: String?,
    resolve: (String) -> CategoryInfo?,
    currentUserId: String,
    zone: TimeZone,
    isCompleting: Boolean,
    onFinalize: (String) -> Unit,
    onAddAttachment: (String, String, String, ByteArray) -> Unit,
) {
    var showLimits by remember(detalle.id) { mutableStateOf(false) }
    val localStart = remember(detalle.scheduledStart, zone) {
        detalle.scheduledStart?.let { raw ->
            runCatching { Instant.parse(raw).toLocalDateTime(zone) }.getOrNull()
        }
    }
    val categoryName = resolve(detalle.categoryId)?.name
    val whenText = localStart?.let { "${formatAgendaDate(it.date)} ${formatTime(it.hour, it.minute)}" }
    val picker = rememberFilePicker()
    val canAttach = thirdPartyCanAttach(detalle.status, detalle.assignedTo, currentUserId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = detalle.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(top = Spacing.sm),
            )
            IconButton(onClick = { showLimits = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    // Abre el popup con las prohibiciones: no es decorativo.
                    contentDescription = "Por qué no puedo rechazarla ni cancelarla",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTag(status = detalle.status)
            Text(
                text = listOfNotNull(whenText, categoryName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StageRail(stage = thirdPartyStage(detalle.status))

        if (detalle.description.isNotBlank()) {
            DetailCard(title = "Qué tenés que hacer", body = detalle.description)
        }

        DetailCard(title = "Archivos") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (attachments.isEmpty()) {
                    Text(
                        text = "Todavía no hay archivos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                attachments.forEach { attachment ->
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (canAttach) {
                    Text(
                        text = "Podés adjuntar la foto de la libreta firmada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = Spacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        if (canAttach) {
            OutlinedButton(
                onClick = {
                    picker.launch { file ->
                        if (file != null) {
                            onAddAttachment(detalle.id, file.name, file.mimeType, file.bytes)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Adjuntar archivo")
            }
        }

        if (detalle.canComplete) {
            Button(
                onClick = { onFinalize(detalle.id) },
                enabled = !isCompleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (isCompleting) "Finalizando…" else "Marcar como finalizada")
            }
        }

        Text(
            text = "Después la cierra ${ownerName ?: "quien te la asignó"} al verificar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xl),
        )
    }

    if (showLimits) {
        LimitsDialog(onDismiss = { showLimits = false })
    }
}

/**
 * El riel de cuatro pasos. Se pinta con `primary` para lo alcanzado y `outlineVariant`
 * para lo que falta, y cada paso lleva su rótulo en palabras: el color no es la única
 * señal, y "Verificar" tiene que poder leerse aunque el punto esté apagado.
 */
@Composable
private fun StageRail(stage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        THIRD_PARTY_STAGES.forEachIndexed { index, label ->
            val reached = index < stage
            val current = index == stage - 1
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (reached) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (reached) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
