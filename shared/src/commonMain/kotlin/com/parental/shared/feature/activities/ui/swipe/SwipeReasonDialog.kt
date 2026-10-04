package com.parental.shared.feature.activities.ui.swipe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.parental.shared.feature.activities.ui.SwipeReasonUiState

/**
 * A1 · Diálogo de motivo de [SwipeAction.CANCEL] y [SwipeAction.DECLINE].
 *
 * Existe uno solo para las dos acciones porque el ROLLO de ambos es idéntico —mismo
 * campo, misma validación, mismo botón— y por eso comparten también [SwipeAction.requiresReason].
 * Dos diálogos habría sido copiar-pegar con la mitad de las reglas de ambos.
 *
 * ⚠ El diálogo NO decide si se puede enviar: [SwipeAction] lleva el `reason` ya recortado
 * por el ViewModel y quien valida es [validateSwipeReason], la misma función que gobierna
 * el botón. Así el botón y la regla no pueden discrepar, que es el mismo criterio con el
 * que ya está escrito `validateCreateForm` en el modal de alta.
 */
@Composable
fun SwipeReasonDialog(
    state: SwipeReasonUiState.Content,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val validation = state.validation
    val inlineError = validation?.takeIf { it !is ReasonValidation.Valid }?.message().orEmpty()

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = {
            Text(if (state.action == SwipeAction.CANCEL) "¿Por qué cancelas?" else "¿Por qué no asistirás?")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    enabled = !state.isSubmitting,
                    label = { Text("Motivo (${REASON_MIN_LENGTH}–$REASON_MAX_LENGTH caracteres)") },
                    isError = inlineError.isNotEmpty() || state.serverError != null,
                    // `Semantics` con `contentDescription` NO se añade: el campo ya es
                    // un nodo de semántica con etiqueta, y `R4-T2-4` cuenta nodos con
                    // precisión. Un `clearAndSetSemantics` aquí REDUCIRÍA el recuento
                    // del formulario y rompería la garantía.
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                )
                // R8 · Si el 422 del servidor se muestra, tiene PRIORIDAD sobre el error
                // de cliente: son la misma comprobación vista desde dos lados, y el del
                // servidor es el que acaba mandando.
                val error = state.serverError ?: inlineError
                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.heightIn(min = 24.dp))
            } else {
                TextButton(onClick = onSubmit, enabled = validation == null || validation is ReasonValidation.Valid) {
                    Text(state.action.confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                Text("Volver")
            }
        },
    )
}