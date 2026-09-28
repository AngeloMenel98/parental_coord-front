package com.parental.shared.feature.activities.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.CategoryBadge
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.theme.DetailGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.feature.activities.domain.model.ActividadDetalle
import com.parental.shared.feature.activities.domain.model.canConfirm
import com.parental.shared.feature.activities.ui.NEXT_MSG
import com.parental.shared.feature.activities.ui.criticalityLabel
import com.parental.shared.feature.activities.ui.formatDetailDate

/*
 * Pantalla de detalle de una actividad — `calendar-design-redesign`, T4 (pasos 2 y 7).
 *
 * T4-3 la reubicó tal cual: su única arista de entrada es la rama `selectedActividad != null &&
 * !isDetailLoading` de `ActividadesScreen`, y no referencia ningún símbolo de la lista. Por eso es
 * `internal` (la rama la llama desde otro archivo) mientras `DetailBlockCard` sigue `private`.
 *
 * Los tokens se adoptaron en **T4-9**, no en T4-3: la mudanza debía ser relocation pura (`R3-T4-3`),
 * pero el exit gate `RC-6` exige cero literales en `feature/activities/`. Ningún valor cambia:
 * `Spacing.lg/sm/xs/xl`, `DetailGeometry.progressSize` (18) y `ProgressStroke.thin` (2) valen lo que
 * los literales que sustituyen, y `RoundedCornerShape(12)` → `ParentalShapes.medium` ya valía 12dp.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActividadDetailScreen(
    actividad: ActividadDetalle,
    resolve: (String) -> CategoryInfo?,
    memberNames: Map<String, String>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onInertAction: (String) -> Unit,
    onNavigateBottom: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    currentUserId: String,
) {
    val isSalud = resolve(actividad.categoryId)?.name.equals("Salud", ignoreCase = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            Column {
                // CTA de confirmación — encima del Cargar comprobante inerte (R12/AD-6):
                // estado 2 confirmado → chip inerte; estado 1 canConfirm → botón full-width;
                // estado 0 (no asignado / no asignatario) → nada.
                when {
                    actividad.assignedConfirmed -> AssistChip(
                        onClick = {},
                        label = { Text("Asignación confirmada") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                    )
                    actividad.canConfirm(currentUserId) -> Button(
                        onClick = { onConfirm(actividad.id) },
                        enabled = !isConfirming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                    ) {
                        if (isConfirming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(DetailGeometry.progressSize),
                                strokeWidth = ProgressStroke.thin,
                            )
                        } else {
                            Text("Confirmar asignación")
                        }
                    }
                    else -> {}
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "Cargar comprobante",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInertAction(NEXT_MSG) }
                            .padding(Spacing.lg),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                BottomNavBar(
                    selectedRoute = "activities",
                    onNavigate = onNavigateBottom,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.lg, bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Text(
                    text = "${actividad.code} · " +
                        if (actividad.type == "obligation") "Obligación" else "Evento",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            item {
                Text(
                    text = actividad.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusTag(status = actividad.status)
                    CategoryBadge(categoryId = actividad.categoryId, resolve = resolve)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ParentalShapes.medium,
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = "Descripción",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = actividad.description.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                DetailBlockCard(
                    label1 = "Fecha",
                    value1 = formatDetailDate(actividad.scheduledStart) ?: "—",
                    label2 = "Límite evidencia",
                    value2 = formatDetailDate(actividad.deadline) ?: "—",
                )
            }
            item {
                DetailBlockCard(
                    label1 = "Asignado a",
                    value1 = actividad.assignedTo?.let { memberNames[it] } ?: "Sin asignar",
                    label2 = "Prioridad",
                    value2 = criticalityLabel(actividad.criticality),
                )
            }
            item {
                // Chip contextual inerte — sin llamada API.
                AssistChip(
                    onClick = { onInertAction(NEXT_MSG) },
                    label = {
                        Text(
                            text = if (isSalud) "Adjuntar resumen médico" else "Adjuntar archivo",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailBlockCard(
    label1: String,
    value1: String,
    label2: String,
    value2: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
    ) {
        Row(modifier = Modifier.padding(Spacing.lg)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = value1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label2,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = value2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
