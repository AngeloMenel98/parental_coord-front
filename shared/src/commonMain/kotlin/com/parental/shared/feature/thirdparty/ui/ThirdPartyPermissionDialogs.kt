package com.parental.shared.feature.thirdparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.formatAgendaDate

/**
 * Los dos popups del artboard 03 — y **los únicos lugares donde el producto explica los
 * permisos**.
 *
 * Decisión de L7 (comentarios del canvas): nada de notas explicativas dentro de la
 * pantalla. El permiso se cuenta una vez al entrar (y después desde el icono de
 * información) y las prohibiciones del detalle viven en su propio popup, arriba a la
 * derecha. Así la pantalla operativa queda limpia y la explicación sigue disponible.
 */
@Composable
internal fun PermitOnboardingDialog(
    permit: ThirdPartyPermitUi?,
    onDismiss: () -> Unit,
) {
    // Sin fecha de vencimiento no hay permiso temporal que anunciar (D3): el popup no se
    // abre con datos incompletos en vez de prometer una vigencia que no sabemos.
    val endDate = permit?.endDate ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Tu permiso, en concreto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "${permit.authorizedByName} te autorizó a entrar hasta el " +
                        "${formatAgendaDate(endDate)}. Esto es todo lo que podés hacer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PermitLine(allowed = true, text = "Finalizar una tarea asignada a vos.")
                PermitLine(allowed = true, text = "Adjuntar archivos a esa tarea.")
                PermitLine(allowed = false, text = "Crear actividades.")
                PermitLine(
                    allowed = false,
                    text = "Aceptar o rechazar: al asignarte, la tarea ya cuenta como que vas.",
                )
                PermitLine(
                    allowed = false,
                    text = "Ver gastos, cumplimiento ni los datos del vínculo.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        },
    )
}

@Composable
internal fun LimitsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Lo que no podés hacer acá") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "La actividad te la asignó un progenitor y sólo esa persona puede " +
                        "cambiarla de dueño o cerrarla. Vos la finalizás cuando la hiciste.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PermitLine(allowed = false, text = "Rechazarla o marcarte como no asistente.")
                PermitLine(allowed = false, text = "Cancelarla o borrarla.")
                PermitLine(allowed = false, text = "Reasignarla a otra persona.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        },
    )
}

/**
 * Una línea de la lista de permisos. El ✓/✕ es DECORATIVO y el texto dice el permiso en
 * palabras: el símbolo solo no es una señal accesible, y el color tampoco (AC-5).
 */
@Composable
private fun PermitLine(allowed: Boolean, text: String) {
    Row(
        modifier = Modifier.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (allowed) "✓" else "✕",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (allowed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
