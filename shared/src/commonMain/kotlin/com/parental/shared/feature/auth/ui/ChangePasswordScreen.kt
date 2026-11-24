package com.parental.shared.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing

/**
 * El cambio obligatorio de contraseña del primer ingreso (S2), sobre el artboard
 * `design/cambio-password-ui/03-app-cerrada.html`: la app se ve DETRÁS, apagada, y el
 * cambio es una hoja que no se puede cerrar.
 *
 * 🔴 Lo que se ve detrás es decoración, no la app. No hay navegación montada ni una sola
 * request: mientras el flag esté encendido el back responde `403 PASSWORD_CHANGE_REQUIRED`
 * en todas las rutas autenticadas, así que cargar el Inicio real sería pedir un error a
 * propósito. El fondo también explica la forma de la pantalla: el bloqueo es global, no una
 * pantalla más del recorrido.
 *
 * La hoja no se puede cerrar (ni con el gesto ni tocando el scrim): es la única salida del
 * bloqueo y eso es lo que se está comunicando. Sí se puede [onLogout] o corregir el
 * formulario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    viewModel: ChangePasswordViewModel,
    onLogout: () -> Unit,
    onSuccess: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val success by viewModel.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onSuccess()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ClosedAppBackdrop()

        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            // El routing ya decidió que no hay app detrás: arrastrar la hoja abajo dejaría
            // al usuario mirando el esqueleto, sin formulario y sin forma de salir.
            confirmValueChange = { it != SheetValue.Hidden },
        )

        ModalBottomSheet(
            onDismissRequest = { /* obligatoria: no se descarta con scrim ni con "atrás" */ },
            sheetState = sheetState,
            shape = ParentalShapes.extraLarge,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChangePasswordForm(
                    uiState = uiState,
                    onCurrentPasswordChange = viewModel::onCurrentPasswordChange,
                    onNewPasswordChange = viewModel::onNewPasswordChange,
                    onRepeatPasswordChange = viewModel::onRepeatPasswordChange,
                    onSubmit = viewModel::submit,
                    onLogout = onLogout,
                )
            }
        }
    }
}

/**
 * La hoja: una sola tarea, sin nav y sin topbar.
 *
 * El error va INLINE y no en un diálogo porque el formulario sigue a la vista: en una hoja
 * obligatoria, un cartel encima taparía justo los campos que hay que corregir.
 */
@Composable
private fun ChangePasswordForm(
    uiState: ChangePasswordUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val error = uiState.error
    val currentInvalid = error == CHANGE_PASSWORD_CURRENT_MISSING ||
        error == CHANGE_PASSWORD_CURRENT_INVALID
    val newInvalid = error == CHANGE_PASSWORD_NEW_TOO_SHORT ||
        error == CHANGE_PASSWORD_NEW_NEEDS_LETTER ||
        error == CHANGE_PASSWORD_NEW_NEEDS_DIGIT ||
        error == CHANGE_PASSWORD_NEW_SAME_AS_CURRENT
    val repeatInvalid = error == CHANGE_PASSWORD_REPEAT_MISMATCH

    Column(
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "No podés seguir todavía",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // El título es serif porque `headlineSmall` lo es: la hoja habla con la misma voz
        // que el resto de la app, no con la de un formulario de sistema.
        Text(
            text = "Cambiá la contraseña provisoria",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "La que te enviaron por mail sirve una sola vez. " +
                "Elegí una tuya y la app se abre completa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        PasswordField(
            value = uiState.currentPassword,
            onValueChange = onCurrentPasswordChange,
            label = "Contraseña provisoria",
            enabled = !uiState.isSaving,
            isError = currentInvalid,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        PasswordField(
            value = uiState.newPassword,
            onValueChange = onNewPasswordChange,
            label = "Nueva contraseña",
            enabled = !uiState.isSaving,
            isError = newInvalid,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            supportingText = NEW_PASSWORD_HINT,
        )
        PasswordField(
            value = uiState.repeatPassword,
            onValueChange = onRepeatPasswordChange,
            label = "Repetí la nueva",
            enabled = !uiState.isSaving,
            isError = repeatInvalid,
            imeAction = ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                onSubmit()
            },
        )

        error?.let { InlineError(it) }

        Button(
            onClick = onSubmit,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AgendaGeometry.touchTargetLegacy),
            shape = ParentalShapes.extraLarge,
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Cambiar y continuar",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        TextButton(
            onClick = onLogout,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AgendaGeometry.touchTargetLegacy),
        ) {
            Text(
                text = "Cerrar sesión",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Campo de contraseña con la transformación de ocultamiento y la etiqueta VISIBLE arriba.
 *
 * En una hoja obligatoria con tres campos iguales, un placeholder que desaparece al
 * escribir deja tres cajas idénticas de puntos: el label persistente es lo que distingue
 * "provisoria" de "repetí la nueva".
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        supportingText = supportingText?.let { hint ->
            { Text(text = hint, style = MaterialTheme.typography.labelSmall) }
        },
    )
}

/**
 * El error del formulario, anunciado como live region para que un lector de pantalla lo
 * diga al aparecer: la persona no puede ver un borde rojo.
 */
@Composable
private fun InlineError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * La app "apagada": topbar inerte y tres tarjetas esqueleto.
 *
 * Es decoración y por eso está fuera del árbol de accesibilidad
 * ([clearAndSetSemantics]): un lector de pantalla que anunciara "Inicio, tarjeta, tarjeta"
 * antes de la hoja haría creer que hay algo que tocar ahí. Nada de esto es pulsable.
 */
@Composable
private fun ClosedAppBackdrop() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TOP_BAR_HEIGHT)
                        .padding(horizontal = Spacing.lg),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Inicio",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.hairline)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(BACKDROP_CARD_COUNT) { BackdropCard() }
        }
    }
}

@Composable
private fun BackdropCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(BACKDROP_AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BackdropBar(width = 120.dp, height = Spacing.md)
                BackdropBar(width = 88.dp, height = Spacing.sm)
            }
        }
    }
}

@Composable
private fun BackdropBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(ParentalShapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** El texto de la política, idéntico al del artboard: es lo que el back exige. */
private const val NEW_PASSWORD_HINT = "8 caracteres o más, con una letra y un número."

/** Alto de la topbar de 56dp del artboard — medida de chrome, no de la escala de espaciado. */
private val TOP_BAR_HEIGHT = 56.dp

/** Diámetro del avatar del esqueleto: el mismo de las filas de terceros (40dp). */
private val BACKDROP_AVATAR_SIZE = AgendaGeometry.nodeCard

/** Tres tarjetas alcanzan para leerse como "hay una lista detrás", sin fingir más app. */
private const val BACKDROP_CARD_COUNT = 3
