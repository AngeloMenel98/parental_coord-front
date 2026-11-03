package com.parental.shared.feature.activities.ui.attachments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.activities.ui.detail.DetailCard

@Composable
fun AttachmentsSection(
    attachments: List<ActivityAttachmentDto>,
    editable: Boolean,
    onAdd: () -> Unit,
    onRename: (ActivityAttachmentDto) -> Unit,
    onReplace: (ActivityAttachmentDto) -> Unit,
    onDelete: (ActivityAttachmentDto) -> Unit,
) {
    DetailCard(title = "Comprobantes") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (attachments.isEmpty()) {
                Text(
                    text = if (editable) "Sin comprobantes. Podés agregar uno." else "Sin comprobantes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            attachments.forEach { att ->
                AttachmentRow(
                    attachment = att,
                    editable = editable,
                    onRename = { onRename(att) },
                    onReplace = { onReplace(att) },
                    onDelete = { onDelete(att) },
                )
            }
            if (editable) {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ParentalShapes.medium,
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Agregar comprobante")
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: ActivityAttachmentDto,
    editable: Boolean,
    onRename: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    Card(shape = ParentalShapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(attachment.fileType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { attachment.viewUrl?.let { uriHandler.openUri(it) } }) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Ver")
            }
            if (editable) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { expanded = false; onRename() })
                        DropdownMenuItem(text = { Text("Reemplazar") }, onClick = { expanded = false; onReplace() })
                        DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { expanded = false; onDelete() })
                    }
                }
            }
        }
    }
}
