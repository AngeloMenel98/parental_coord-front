package com.parental.shared.feature.expenses.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.files.FileData
import com.parental.shared.core.files.rememberFilePicker
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.util.formatMoney
import com.parental.shared.core.util.formatShortDate
import com.parental.shared.feature.expenses.domain.ExpenseStepState
import com.parental.shared.feature.expenses.domain.ExpenseTimelineStep
import com.parental.shared.feature.expenses.domain.expenseTimeline
import com.parental.shared.feature.expenses.domain.model.ExpenseResponseDto
import com.parental.shared.feature.expenses.domain.model.ExpenseStatus
import com.parental.shared.feature.expenses.domain.model.ExpenseUpdateRequest
import com.parental.shared.feature.expenses.domain.model.displayName
import com.parental.shared.feature.expenses.domain.viewerShare
import kotlinx.datetime.LocalDate

/**
 * Detalle del gasto como "expediente" (design/expense-detail-ui/01).
 *
 * Encabezado con estado, categoría, monto y división; datos en grilla; descripción; documento
 * con acción de adjuntar; y la trazabilidad en un bloque desplegable con una línea temporal que
 * sólo suma pasos ocurridos. Las acciones del pie salen de los flags del SERVIDOR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    viewModel: ExpensesViewModel,
    onBack: () -> Unit,
    currentUserId: String? = null,
    onLogout: () -> Unit = {},
) {
    val uiState = viewModel.uiState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Gasto",
                onLogout = onLogout,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val detail = uiState.detail) {
                is DetailUiState.Loading -> DetailLoadingState()
                is DetailUiState.Error -> DetailErrorState(
                    message = detail.message,
                    onRetry = { viewModel.openDetail(detail.id) },
                )
                is DetailUiState.Content -> ExpenseDetailContent(
                    expense = detail.expense,
                    currentUserId = currentUserId,
                    onApprove = { viewModel.approve(detail.expense.id) },
                    onReject = { reason -> viewModel.reject(detail.expense.id, reason) },
                    onPay = { viewModel.pay(detail.expense.id) },
                    onCancel = { viewModel.cancel(detail.expense.id); onBack() },
                    onEdit = { request -> viewModel.update(detail.expense.id, request) },
                    onAttach = { file -> viewModel.attachDocument(detail.expense.id, file.name, file.mimeType, file.bytes) },
                )
                DetailUiState.Closed -> {
                    LaunchedEffect(Unit) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun ExpenseDetailContent(
    expense: ExpenseResponseDto,
    currentUserId: String?,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onEdit: (ExpenseUpdateRequest) -> Unit,
    onAttach: (FileData) -> Unit,
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showRejectDialog) {
        ReasonDialog(
            title = "Rechazar gasto",
            confirmLabel = "Rechazar",
            onConfirm = { reason ->
                showRejectDialog = false
                onReject(reason)
            },
            onDismiss = { showRejectDialog = false },
        )
    }

    if (showEditDialog) {
        EditExpenseDialog(
            expense = expense,
            onConfirm = { request ->
                showEditDialog = false
                onEdit(request)
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("¿Cancelar este gasto?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancel()
                    },
                ) {
                    Text("Cancelar gasto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Volver")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HeroCard(expense = expense, currentUserId = currentUserId)
            FactsCard(expense = expense)
            DescriptionCard(expense = expense)
            DocumentCard(expense = expense, onAttach = onAttach)
            TimelineCard(expense = expense)
        }
        DetailActionsBar(
            expense = expense,
            onApprove = onApprove,
            onReject = { showRejectDialog = true },
            onPay = onPay,
            onCancel = { showCancelDialog = true },
            onEdit = { showEditDialog = true },
        )
    }
}

// ── S1 · encabezado ───────────────────────────────────────────────────────────

@Composable
private fun HeroCard(expense: ExpenseResponseDto, currentUserId: String?) {
    val dark = isSystemInDarkTheme()
    val share = viewerShare(expense, currentUserId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                StatusChip(status = expense.status)
                CategoryChip(name = expense.categoryName)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = expense.concept,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoney(expense.amount),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (share != null) {
                Text(
                    text = "Tu parte: ${formatMoney(share)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            SplitBar(
                payer = expense.splitRatioPayer,
                other = expense.splitRatioOther,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pagador ${expense.splitRatioPayer}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Otro ${expense.splitRatioOther}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** S1 · dos tramos que suman el reparto; si el servidor manda 0/0 se dibuja mitad y mitad. */
@Composable
private fun SplitBar(payer: Int, other: Int) {
    val payerWeight = payer.coerceAtLeast(0).toFloat()
    val otherWeight = other.coerceAtLeast(0).toFloat()
    val total = payerWeight + otherWeight
    val (first, second) = if (total <= 0f) 1f to 1f else payerWeight to otherWeight
    val dark = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.sm)
            .clip(MaterialTheme.shapes.small),
    ) {
        Box(
            modifier = Modifier
                .weight(first)
                .fillMaxSize()
                .background(StatusTokens.triple(StatusTokens.Status.InProgress, dark).content),
        )
        Box(
            modifier = Modifier
                .weight(second)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
    }
}

@Composable
private fun StatusChip(status: ExpenseStatus) {
    val triple = StatusTokens.triple(status.toTokenStatus(), isSystemInDarkTheme())
    Surface(
        color = triple.container,
        contentColor = triple.content,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.xxs)
                    .clip(CircleShape)
                    .background(triple.dot),
            )
            Text(
                text = status.displayName(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** S1 · la categoría va con su color y SIN ícono. */
@Composable
private fun CategoryChip(name: String) {
    val dark = isSystemInDarkTheme()
    val triple = CategoryTokens.resolve(name)?.let { CategoryTokens.triple(it, dark) }
    val container = triple?.container ?: MaterialTheme.colorScheme.surfaceVariant
    val content = triple?.content ?: MaterialTheme.colorScheme.onSurfaceVariant

    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── S2/S3 · datos y descripción ───────────────────────────────────────────────

@Composable
private fun FactsCard(expense: ExpenseResponseDto) {
    SectionCard(title = "Datos del gasto") {
        Row(modifier = Modifier.fillMaxWidth()) {
            Fact(
                label = "Fecha",
                value = formatShortDateString(expense.expenseDate),
                modifier = Modifier.weight(1f),
            )
            Fact(
                label = "Solicitó",
                value = expense.requestedByName ?: expense.requestedBy,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            Fact(
                label = "Contraparte",
                value = expense.counterpartName ?: expense.counterpartId ?: "—",
                modifier = Modifier.weight(1f),
            )
            // El contrato todavía no expone los hijos del gasto: se muestra el hueco, nunca
            // un nombre inventado.
            Fact(
                label = "Hijos",
                value = "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DescriptionCard(expense: ExpenseResponseDto) {
    SectionCard(title = "Descripción") {
        Text(
            text = expense.description?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── S4 · documento ────────────────────────────────────────────────────────────

@Composable
private fun DocumentCard(expense: ExpenseResponseDto, onAttach: (FileData) -> Unit) {
    val picker = rememberFilePicker()

    SectionCard(title = "Documento") {
        if (expense.hasDocument) {
            Text(
                text = "Comprobante adjunto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            return@SectionCard
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sin comprobante adjunto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "El gasto se registró sin documento de respaldo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = { picker.launch { file -> if (file != null) onAttach(file) } },
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Adjuntar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── S5 · trazabilidad plegable ────────────────────────────────────────────────

@Composable
private fun TimelineCard(expense: ExpenseResponseDto) {
    var expanded by remember(expense.id) { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = MotionTokens.durationStandard),
        label = "timeline-chevron",
    )
    val steps = remember(expense) { expenseTimeline(expense) }

    SectionCard(title = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Trazabilidad",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Ocultar trazabilidad" else "Mostrar trazabilidad",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            steps.forEachIndexed { index, step ->
                TimelineRow(step = step, isLast = index == steps.lastIndex)
            }
        }
    }
}

@Composable
private fun TimelineRow(step: ExpenseTimelineStep, isLast: Boolean) {
    val dotColor = when (step.state) {
        ExpenseStepState.Done -> MaterialTheme.colorScheme.primary
        ExpenseStepState.Current -> StatusTokens.triple(StatusTokens.Status.InProgress, isSystemInDarkTheme()).content
        ExpenseStepState.Rejected -> MaterialTheme.colorScheme.error
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .size(Spacing.sm + 2.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else Spacing.sm),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            step.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── S6 · acciones ─────────────────────────────────────────────────────────────

@Composable
private fun DetailActionsBar(
    expense: ExpenseResponseDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when {
                expense.canApprove || expense.canReject -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (expense.canReject) {
                            OutlinedButton(
                                onClick = onReject,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("Rechazar")
                            }
                        }
                        if (expense.canApprove) {
                            Button(
                                onClick = onApprove,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("Aprobar")
                            }
                        }
                    }
                    Text(
                        text = "Solo la contraparte puede aprobar o rechazar mientras siga pendiente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                expense.canMarkPaid -> Button(
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Marcar como pagado")
                }

                expense.canEdit || expense.canCancel -> Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (expense.canEdit) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Editar")
                        }
                    }
                    if (expense.canCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Cancelar")
                        }
                    }
                }

                expense.isTerminal -> Text(
                    text = "Este gasto está cerrado y no admite más acciones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── compartidos ───────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            content()
        }
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motivo (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Volver")
            }
        },
    )
}

@Composable
private fun EditExpenseDialog(
    expense: ExpenseResponseDto,
    onConfirm: (ExpenseUpdateRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var concept by remember { mutableStateOf(expense.concept) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var expenseDate by remember { mutableStateOf(expense.expenseDate) }
    var description by remember { mutableStateOf(expense.description ?: "") }
    var splitPayer by remember { mutableStateOf(expense.splitRatioPayer.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar gasto") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Concepto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // La categoría se MUESTRA pero no se edita aquí: pedir un UUID a mano no es
                // una interfaz. El selector de la alta (una sola taxonomía, R2) es el dueño.
                Text(
                    "Categoría: ${expense.categoryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = expenseDate,
                    onValueChange = { expenseDate = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextField(
                        value = splitPayer,
                        onValueChange = { splitPayer = it },
                        label = { Text("% pagador") },
                        modifier = Modifier.weight(1f),
                    )
                    // La contraparte la DERIVA el servidor: no es un campo que el usuario mande.
                    Text(
                        "La otra parte: ${expense.splitRatioOther}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ExpenseUpdateRequest(
                            concept = concept.takeIf { it != expense.concept },
                            amount = amount.toDoubleOrNull()?.takeIf { it != expense.amount },
                            expenseDate = expenseDate.takeIf { it != expense.expenseDate },
                            description = description.takeIf { it != (expense.description ?: "") },
                            splitRatioPayer = splitPayer.toIntOrNull()?.takeIf { it != expense.splitRatioPayer },
                        ),
                    )
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun DetailLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Reintentar")
            }
        }
    }
}

private fun ExpenseStatus.toTokenStatus(): StatusTokens.Status = when (this) {
    ExpenseStatus.PENDING -> StatusTokens.Status.InProgress
    ExpenseStatus.APPROVED -> StatusTokens.Status.Done
    ExpenseStatus.PAID -> StatusTokens.Status.Paid
    ExpenseStatus.REJECTED -> StatusTokens.Status.Rejected
}

private fun formatShortDateString(isoOrDate: String): String =
    runCatching { LocalDate.parse(isoOrDate) }
        .getOrNull()
        ?.let { formatShortDate(it) }
        ?: isoOrDate
