package com.parental.shared.feature.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.Spacing

/**
 * F5 · alta de progenitor con validación (artboard `admin-forms-ui/03`, paso de cuenta).
 *
 * Antes era una tarjeta con cuatro campos siempre visible: sin validación, empujaba la
 * lista fuera de la pantalla y, cuando el backend rechazaba un email repetido, el error
 * aparecía detrás del formulario. Acá el error de formato es local (se ve antes de enviar)
 * y el del backend se muestra dentro del mismo diálogo.
 */
@Composable
fun NewProgenitorDialog(state: AdminUiState, viewModel: AdminViewModel) {
    if (!state.showNewUserDialog) return

    var localError by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    val email = state.newUserEmail.trim()
    val emailLooksValid = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(email)
    val passwordValid = state.newUserPassword.length >= 6

    AlertDialog(
        onDismissRequest = { viewModel.closeNewUserDialog() },
        title = { Text("Nuevo progenitor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.newUserFirstName,
                        onValueChange = { viewModel.updateUserFirstName(it) },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.newUserLastName,
                        onValueChange = { viewModel.updateUserLastName(it) },
                        label = { Text("Apellido") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = state.newUserEmail,
                    onValueChange = { viewModel.updateUserEmail(it) },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = email.isNotEmpty() && !emailLooksValid,
                    supportingText = {
                        if (email.isNotEmpty() && !emailLooksValid) {
                            Text("Falta el dominio: $email")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.newUserPassword,
                    onValueChange = { viewModel.updateUserPassword(it) },
                    label = { Text("Contraseña provisoria") },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    isError = state.newUserPassword.isNotEmpty() && !passwordValid,
                    supportingText = {
                        Row {
                            Text(
                                text = if (state.newUserPassword.isNotEmpty() && !passwordValid) {
                                    "Mínimo 6 caracteres."
                                } else {
                                    "Mínimo 6 caracteres. Se la pasás a la persona."
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text(if (showPassword) "Ocultar" else "Mostrar")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Se crea con rol progenitor y queda activo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val message = localError ?: state.error
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    localError = when {
                        state.newUserFirstName.isBlank() || state.newUserLastName.isBlank() ->
                            "Completá nombre y apellido"
                        !emailLooksValid -> "Revisá el email"
                        !passwordValid -> "La contraseña necesita al menos 6 caracteres"
                        else -> null
                    }
                    if (localError == null) viewModel.createUser()
                },
                enabled = !state.isCreatingUser,
            ) {
                if (state.isCreatingUser) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear progenitor")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeNewUserDialog() }) { Text("Cancelar") }
        },
    )
}
