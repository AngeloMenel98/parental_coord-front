package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.SheetGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.ui.create.formatDdMmAaaa
import com.parental.shared.feature.activities.ui.create.pickerMillisToDate
import com.parental.shared.feature.activities.ui.create.todayUtcMillis
import com.parental.shared.feature.activities.ui.create.toPickerMillis
import com.parental.shared.feature.expenses.domain.model.CreateExpenseRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Estado editable del formulario de alta de gastos (S6/T27).
 *
 * Los campos numéricos se guardan como texto para no perder lo que el usuario
 * escribe mientras tipea; la validación se encarga de parsearlos.
 */
data class CreateExpenseForm(
    val concept: String = "",
    val amount: String = "",
    val categoryId: String? = null,
    val expenseDate: LocalDate? = today(),
    val description: String = "",
    val splitRatioPayer: String = "50",
)

enum class CreateExpenseField {
    CONCEPT,
    AMOUNT,
    CATEGORY,
    DATE,
    DESCRIPTION,
    SPLIT,
}

data class CreateExpenseFormError(
    val field: CreateExpenseField,
    val message: String,
)

internal fun String.toExpenseConcept(): String =
    substringBeforeLast('.').trim().ifEmpty { trim() }

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Valida el formulario de alta. Una lista vacía significa que se puede enviar.
 */
fun validateCreateExpenseForm(form: CreateExpenseForm): List<CreateExpenseFormError> = buildList {
    if (form.concept.trim().isEmpty()) {
        add(CreateExpenseFormError(CreateExpenseField.CONCEPT, "Ingresa un concepto"))
    }

    val amount = form.amount.replace(',', '.').toDoubleOrNull()
    when {
        amount == null -> add(CreateExpenseFormError(CreateExpenseField.AMOUNT, "Ingresa un monto"))
        amount <= 0 -> add(CreateExpenseFormError(CreateExpenseField.AMOUNT, "El monto debe ser mayor a 0"))
    }

    if (form.categoryId == null) {
        add(CreateExpenseFormError(CreateExpenseField.CATEGORY, "Elige una categoría"))
    }

    if (form.expenseDate == null) {
        add(CreateExpenseFormError(CreateExpenseField.DATE, "Elige una fecha"))
    }

    val split = form.splitRatioPayer.replace(',', '.').toIntOrNull()
    if (split == null || split !in 1..99) {
        add(CreateExpenseFormError(CreateExpenseField.SPLIT, "Elige un porcentaje válido"))
    }
}

/**
 * Convierte el formulario validado en el request que entiende el backend.
 * Devuelve `null` si la validación falla (no debería llamarse sin validar antes).
 */
fun CreateExpenseForm.toRequest(): CreateExpenseRequest? {
    val concept = concept.trim().takeIf { it.isNotEmpty() } ?: return null
    val amount = amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    val categoryId = categoryId ?: return null
    val date = expenseDate ?: return null
    val split = splitRatioPayer.replace(',', '.').toIntOrNull()?.coerceIn(1, 99) ?: 50

    return CreateExpenseRequest(
        concept = concept,
        amount = amount,
        categoryId = categoryId,
        expenseDate = date.toString(),
        description = description.trim().takeIf { it.isNotEmpty() },
        splitRatioPayer = split,
    )
}

/**
 * Modal "+ Solicitar" gasto (S6/T27).
 *
 * - Campos: Concepto, Monto, Categoría, Fecha del gasto, Notas, División.
 * - La división default es 50/50; sólo se envía la parte del solicitante.
 * - No abre un selector de documento: T30 convierte un adjunto existente de actividad.
 *   El alta directa desde Gastos conserva el aviso no bloqueante de documento faltante.
 * - Al crear con éxito se cierra la hoja; el error va al snackbar de la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseSheet(
    categories: Map<String, CategoryInfo>,
    isLoading: Boolean,
    initialConcept: String? = null,
    hasPendingAttachment: Boolean = false,
    onSubmit: (CreateExpenseRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(CreateExpenseForm(concept = initialConcept.orEmpty())) }
    val errors = remember(form) { validateCreateExpenseForm(form) }
    var touched by remember { mutableStateOf(emptySet<CreateExpenseField>()) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun touch(field: CreateExpenseField) {
        touched = touched + field
    }

    fun errorFor(field: CreateExpenseField) = errors.firstOrNull { it.field == field }
    fun showError(field: CreateExpenseField) =
        if (field in touched) errorFor(field)?.message else null

    val request = form.toRequest()
    val canSubmit = request != null && !isLoading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = Spacing.maxContentWidth,
        dragHandle = null,
    ) {
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * SheetGeometry.maxHeightRatio),
            ) {
                CreateExpenseHeader(onClose = onDismiss)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Spacer(Modifier.height(Spacing.sm))

                    OutlinedTextField(
                        value = form.concept,
                        onValueChange = {
                            touch(CreateExpenseField.CONCEPT)
                            form = form.copy(concept = it)
                        },
                        label = { Text("Concepto") },
                        singleLine = true,
                        isError = errorFor(CreateExpenseField.CONCEPT) != null,
                        supportingText = showError(CreateExpenseField.CONCEPT)?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.amount,
                        onValueChange = {
                            touch(CreateExpenseField.AMOUNT)
                            form = form.copy(amount = it)
                        },
                        label = { Text("Monto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
                        isError = errorFor(CreateExpenseField.AMOUNT) != null,
                        supportingText = showError(CreateExpenseField.AMOUNT)?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ExpenseCategoryPicker(
                        categories = categories,
                        selectedId = form.categoryId,
                        error = showError(CreateExpenseField.CATEGORY),
                        onSelect = {
                            touch(CreateExpenseField.CATEGORY)
                            form = form.copy(categoryId = it)
                        },
                        label = "Categoría",
                    )

                    OutlinedTextField(
                        value = form.expenseDate?.let(::formatDdMmAaaa).orEmpty(),
                        onValueChange = {},
                        label = { Text("Fecha del gasto") },
                        placeholder = { Text("dd/mm/aaaa") },
                        readOnly = true,
                        isError = errorFor(CreateExpenseField.DATE) != null,
                        supportingText = showError(CreateExpenseField.DATE)?.let { { Text(it) } },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    touch(CreateExpenseField.DATE)
                                    showDatePicker = true
                                },
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir fecha")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.description,
                        onValueChange = {
                            touch(CreateExpenseField.DESCRIPTION)
                            form = form.copy(description = it)
                        },
                        label = { Text("Notas") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.splitRatioPayer,
                        onValueChange = {
                            touch(CreateExpenseField.SPLIT)
                            form = form.copy(splitRatioPayer = it)
                        },
                        label = { Text("División") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        isError = errorFor(CreateExpenseField.SPLIT) != null,
                        supportingText = {
                            val error = showError(CreateExpenseField.SPLIT)
                            if (error != null) {
                                Text(error)
                            } else {
                                val payer = form.splitRatioPayer.toIntOrNull() ?: 50
                                val other = 100 - payer
                                Text("Pagás $payer% / La otra parte $other%")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (hasPendingAttachment) {
                        PendingActivityAttachmentNotice()
                    } else {
                        MissingDocumentWarning()
                    }

                    Spacer(Modifier.height(Spacing.sm))
                }

                CreateExpenseFooter(
                    enabled = canSubmit,
                    loading = isLoading,
                    onSubmit = {
                        touched = CreateExpenseField.entries.toSet()
                        request?.let(onSubmit)
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.expenseDate?.toPickerMillis() ?: todayUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(::pickerMillisToDate)?.let { date ->
                            form = form.copy(expenseDate = date)
                        }
                        showDatePicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun CreateExpenseHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nuevo gasto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Solicitar gasto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
        }
    }
}

/**
 * Aviso no bloqueante de documento faltante (FR-9).
 *
 * El formulario no abre un selector propio: T30 parte del adjunto de una actividad y
 * lo convierte tras crear el gasto. El aviso cubre el alta iniciada desde Gastos.
 */
@Composable
private fun MissingDocumentWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Este gasto se registrará sin documento de respaldo. " +
                    "Podrás adjuntarlo más tarde.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PendingActivityAttachmentNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = "El comprobante seleccionado se vinculará a este gasto al crearlo.",
            modifier = Modifier.padding(Spacing.lg),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CreateExpenseFooter(
    enabled: Boolean,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Solicitar")
            }
        }
    }
}

/** [LocalDate] → milis UTC a medianoche, reutilizado del picker de actividades. */
private fun LocalDate.toPickerMillis(): Long = this.toPickerMillis()
