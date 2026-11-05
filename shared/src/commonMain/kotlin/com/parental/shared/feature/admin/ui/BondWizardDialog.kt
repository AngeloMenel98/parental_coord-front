package com.parental.shared.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.admin.domain.model.AdminUser

/**
 * F5 · wizard de creación de vínculo — implementación de `admin-forms-ui/03`.
 *
 * Tres pasos en una sola pantalla: nombre y acuerdo, progenitores por búsqueda, hijos.
 * Nada se persiste hasta el paso 3, y el paso 3 exige al menos un hijo porque el producto
 * considera inválido un vínculo sin hijos. El backend crea todo en una transacción
 * (`POST /admin/bonds` con `children`), así que un fallo no deja un vínculo a medias.
 */

private const val STEP_NAME = 0
private const val STEP_MEMBERS = 1
private const val STEP_CHILDREN = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BondWizardDialog(state: AdminUiState, viewModel: AdminViewModel) {
    if (!state.showBondWizard) return

    Dialog(
        onDismissRequest = { viewModel.closeBondWizard() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            // F7 · `safeDrawingPadding` mantiene el contenido fuera de la barra de estado y
            // de los botones/gesto de navegación del teléfono. Sin esto, un botón anclado
            // al pie queda literalmente debajo del botón "atrás" del sistema.
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (state.bondWizardStep == STEP_NAME) {
                                viewModel.closeBondWizard()
                            } else {
                                viewModel.previousBondWizardStep()
                            }
                        },
                    ) {
                        Text(if (state.bondWizardStep == STEP_NAME) "Cancelar" else "Atrás")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Crear vínculo",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Espeja el ancho del botón de la izquierda para centrar el título.
                    Spacer(modifier = Modifier.width(72.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md)) {
                    WizardStepper(state.bondWizardStep)
                }

                // F7 · el botón vuelve a estar anclado al pie (el usuario lo quiere
                // abajo), pero el pie suma el inset de navegación del dispositivo:
                // queda en el borde útil de la pantalla, no debajo de los botones del
                // teléfono. El contenido de cada paso se scrollea por su cuenta.
                Box(modifier = Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                    when (state.bondWizardStep) {
                        STEP_NAME -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            WizardStepName(state, viewModel)
                        }
                        STEP_MEMBERS -> WizardStepMembers(state, viewModel)
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            WizardStepChildren(state, viewModel)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.md,
                            bottom = Spacing.md,
                        ),
                ) {
                    WizardFooter(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun WizardFooter(state: AdminUiState, viewModel: AdminViewModel) {
    Column {
        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        WizardPrimaryAction(state, viewModel)
    }
}

@Composable
private fun WizardPrimaryAction(state: AdminUiState, viewModel: AdminViewModel) {
    val label = when (state.bondWizardStep) {
        STEP_NAME -> "Siguiente"
        STEP_MEMBERS -> "Siguiente: hijos"
        else -> {
            val count = state.bondChildrenDraft.size
            if (count == 0) "Crear vínculo" else "Crear vínculo con $count ${if (count == 1) "hijo" else "hijos"}"
        }
    }
    val enabled = when (state.bondWizardStep) {
        STEP_NAME -> state.newBondTitle.isNotBlank()
        STEP_MEMBERS -> state.bondMemberIds.size == 2
        else -> state.bondChildrenDraft.isNotEmpty() && !state.isCreatingBond
    }

    Button(
        onClick = {
            when (state.bondWizardStep) {
                STEP_CHILDREN -> viewModel.submitBondWizard()
                else -> viewModel.nextBondWizardStep()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        if (state.isCreatingBond) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(label)
        }
    }

}

@Composable
private fun WizardStepper(step: Int) {
    val labels = listOf("Nombre", "Progenitores", "Hijos")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            val done = index < step
            val current = index == step
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            current -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (done) "✓" else (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        current -> MaterialTheme.colorScheme.onPrimary
                        done -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Paso 1 · nombre y acuerdo ───────────────────────────────────────

@Composable
private fun WizardStepName(state: AdminUiState, viewModel: AdminViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(
            value = state.newBondTitle,
            onValueChange = { viewModel.updateBondTitle(it) },
            label = { Text("Nombre del vínculo") },
            supportingText = { Text("Es el nombre que vas a ver en la lista y en el reporte.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "TIPO DE ACUERDO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("formal", "informal").forEach { type ->
                FilterChip(
                    selected = state.newBondAgreementType == type,
                    onClick = { viewModel.updateBondAgreementType(type) },
                    label = { Text(type.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

// ── Paso 2 · progenitores por nombre ────────────────────────────────

@Composable
private fun WizardStepMembers(state: AdminUiState, viewModel: AdminViewModel) {
    val query = state.bondUserQuery.trim()
    val selected = state.users.filter { it.id in state.bondMemberIds }
    val matches = state.users
        .filter { it.id !in state.bondMemberIds }
        .filter { user -> user.matchesUserQuery(query) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "PROGENITORES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.bondMemberIds.size} de 2 · obligatorio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selected.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                selected.forEach { user ->
                    SelectedMemberChip(user) { viewModel.toggleBondMember(user.id) }
                }
            }
        }

        OutlinedTextField(
            value = state.bondUserQuery,
            onValueChange = { viewModel.updateBondUserQuery(it) },
            label = { Text("Buscar por nombre o email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (matches.isEmpty()) {
                item {
                    Text(
                        text = if (query.isEmpty()) {
                            "No quedan más usuarios para elegir."
                        } else {
                            "Nadie coincide con «$query» en nombre ni email."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
                if (query.isNotEmpty()) {
                    // F5 · el caso "esa persona todavía no existe" se resuelve sin salir
                    // del wizard: se crea la cuenta y queda elegida.
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.openNewUserDialog(fromWizard = true) }
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column {
                                Text(
                                    text = "Crear progenitor «$query»",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Nombre, apellido, email y contraseña acá mismo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            items(matches, key = { it.id }) { user ->
                UserPickRow(user) { viewModel.toggleBondMember(user.id) }
            }
        }
    }
}

@Composable
private fun SelectedMemberChip(user: AdminUser, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(user),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text(text = user.displayName(), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onRemove() },
            )
        }
    }
}

@Composable
private fun UserPickRow(user: AdminUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(user),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Paso 3 · hijos (mínimo uno) ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardStepChildren(state: AdminUiState, viewModel: AdminViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "HIJOS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.bondChildrenDraft.size} " +
                    if (state.bondChildrenDraft.size == 1) "cargado" else "cargados",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.bondChildrenDraft.forEachIndexed { index, child ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${child.firstName} ${child.lastName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = child.dateOfBirth?.let(::adminFormatIsoDate)
                                ?: "Sin fecha de nacimiento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.removeBondChildDraft(index) }) {
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = "Agregar un hijo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.bondChildFirstName,
                        onValueChange = { viewModel.updateBondChildFirstName(it) },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.bondChildLastName,
                        onValueChange = { viewModel.updateBondChildLastName(it) },
                        label = { Text("Apellido") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nacimiento: ${adminFormatIsoDate(state.bondChildDob.ifBlank { null })}")
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.addBondChildDraft() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ Agregar hijo")
                }
            }
        }

        Text(
            text = "• El vínculo necesita al menos un hijo. Con cero, el botón de crear queda deshabilitado.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDatePicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = adminIsoToPickerMillis(state.bondChildDob.ifBlank { null }),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        viewModel.updateBondChildDob(adminPickerMillisToIso(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = picker) }
    }
}

// ── helpers ─────────────────────────────────────────────────────────

private fun AdminUser.displayName(): String {
    val name = buildString {
        append(firstName ?: "")
        if (lastName != null) append(" ${lastName}")
    }.trim()
    return name.ifBlank { email }
}

private fun initialsOf(user: AdminUser): String {
    val first = user.firstName?.firstOrNull()?.uppercase()
    val last = user.lastName?.firstOrNull()?.uppercase()
    return when {
        first != null && last != null -> "$first$last"
        first != null -> first
        else -> user.email.first().uppercase()
    }
}
