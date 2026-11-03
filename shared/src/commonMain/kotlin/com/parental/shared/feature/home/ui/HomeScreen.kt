package com.parental.shared.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.StatusTag
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.ui.theme.TypeTokens
import com.parental.shared.core.util.formatMoney
import com.parental.shared.feature.home.domain.ChildStatus
import com.parental.shared.feature.home.domain.COMPLIANCE_RING_SEGMENTS
import com.parental.shared.feature.home.domain.TodayActivity
import com.parental.shared.feature.home.domain.TodayAgenda
import com.parental.shared.feature.home.domain.complianceBand
import com.parental.shared.feature.home.domain.complianceSegmentsFilled
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceMember
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeMetrics
import com.parental.shared.feature.home.domain.model.HomeSummary
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Inicio — "Hoy, con anillo" (design/home-ui/04-hoy-con-anillo.html).
 *
 * El orden de la pantalla es el de la spec S1..S5: saludo centrado con el vínculo,
 * tarjeta "Hoy" que es UN solo objetivo táctil, franja de tres métricas pulsables,
 * card de cumplimiento con anillo de 12 segmentos, y los hijos con su carga vencida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit = {},
    onNavigateBottom: (String) -> Unit = {},
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error
    val summary = uiState.summary

    Scaffold(
        topBar = {
            // S12 · G-1…G-5 — la barra global: campana con contador y salir con
            // confirmación. El botón de texto "Cerrar sesión" se fue con ella: el ícono
            // ocupa menos y el diálogo evita el cierre accidental.
            AppTopBar(
                title = "Coordinación Parental",
                unreadCount = unreadCount,
                onOpenNotifications = onOpenNotifications,
                onLogout = onLogout,
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedRoute = "home",
                onNavigate = onNavigateBottom,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                uiState.isLoading -> HomeLoadingState()
                error != null -> HomeErrorState(
                    message = error,
                    onRetry = { viewModel.loadHomeData() },
                )
                summary == null -> HomeUnavailableState(
                    onRetry = { viewModel.loadHomeData() },
                )
                else -> HomeContent(
                    summary = summary,
                    userName = uiState.userName,
                    onOpenActivities = { onNavigateBottom("activities") },
                    onOpenExpenses = { onNavigateBottom("expenses") },
                    onRetryCompliance = { viewModel.loadHomeData() },
                )
            }
        }
    }
}

@Composable
private fun HomeLoadingState(modifier: Modifier = Modifier) {
    ContentListSkeleton(
        contentDescription = "Cargando el inicio",
        itemCount = 3,
        modifier = modifier,
    )
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredHomeState(modifier = modifier) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = "Error"
                        contentDescription = message
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun HomeUnavailableState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredHomeState(modifier = modifier) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription =
                                "Inicio no disponible. No hay datos para mostrar en este momento."
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "El inicio no está disponible",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "No hay datos para mostrar en este momento.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun CenteredHomeState(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HomeContent(
    summary: HomeSummary,
    userName: String,
    onOpenActivities: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onRetryCompliance: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                GreetingSection(
                    userName = userName,
                    bondActive = summary.compliance?.bondActive ?: (summary.bondId != null),
                    showBond = summary.bondId != null,
                )
            }

            if (summary.bondId == null) {
                item { NoBondState() }
            } else {
                item {
                    TodayCard(
                        agenda = summary.today,
                        onOpen = onOpenActivities,
                    )
                }

                item {
                    HomeMetricStrip(
                        metrics = summary.metrics,
                        onOpenActivities = onOpenActivities,
                        onOpenExpenses = onOpenExpenses,
                    )
                }

                item {
                    when {
                        summary.compliance != null -> ComplianceRingCard(
                            compliance = summary.compliance,
                            userName = userName,
                        )
                        else -> ComplianceUnavailableCard(onRetry = onRetryCompliance)
                    }
                }

                item {
                    ChildrenSection(
                        childStatuses = summary.childStatuses,
                        children = summary.children,
                    )
                }
            }
        }
    }
}

// ── S1 · saludo centrado ─────────────────────────────────────────────────

@Composable
private fun GreetingSection(
    userName: String,
    bondActive: Boolean,
    showBond: Boolean,
    modifier: Modifier = Modifier,
) {
    val localDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val dayOfWeek = when (localDate.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Lunes"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Martes"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Miércoles"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Jueves"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Viernes"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sábado"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Domingo"
        else -> ""
    }
    val monthName = MONTH_ABBREVIATIONS[localDate.monthNumber - 1]
    val displayName = userName.trim()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$dayOfWeek, ${localDate.dayOfMonth} $monthName ${localDate.year}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = if (displayName.isBlank()) "Hola" else "Hola, $displayName",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (showBond) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            BondStatusPill(
                label = if (bondActive) "Vínculo activo" else "Vínculo inactivo",
                active = bondActive,
            )
        }
    }
}

/**
 * El chip del vínculo: el semáforo global de la casa.
 *
 * Usa la tríada `Ok`/`Ko` de [StatusTokens] (container + content), no un par suelto:
 * es el mismo verde que el resto de la app usa para "esto está bien".
 */
@Composable
private fun BondStatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val status = if (active) StatusTokens.Status.Ok else StatusTokens.Status.Ko
    val triple = StatusTokens.triple(status, dark)

    Surface(
        modifier = modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
            stateDescription = label
        },
        color = triple.container,
        contentColor = triple.content,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(triple.content),
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ── S2 · tarjeta "Hoy": un solo objetivo táctil ──────────────────────────

@Composable
private fun TodayCard(
    agenda: TodayAgenda?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hoy",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = agenda?.let { activityCountLabel(it.total) } ?: "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                agenda == null -> TodayEmptyLine(text = "No se pudo cargar la agenda de hoy")
                agenda.total == 0 -> TodayEmptyLine(text = "No hay actividades para hoy")
                else -> {
                    agenda.activities.forEachIndexed { index, activity ->
                        if (index > 0) {
                            HairlineDivider()
                        }
                        TodayRow(activity = activity)
                    }
                    val remaining = agenda.total - agenda.activities.size
                    if (remaining > 0) {
                        HairlineDivider()
                        Text(
                            text = if (remaining == 1) {
                                "1 actividad más hoy"
                            } else {
                                "$remaining actividades más hoy"
                            },
                            modifier = Modifier.padding(
                                horizontal = Spacing.xs,
                                vertical = Spacing.sm,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayEmptyLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.md),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TodayRow(activity: TodayActivity, modifier: Modifier = Modifier) {
    // La fila es parte de la tarjeta-botón: no es un objetivo propio, así que no
    // declara acción. El estado va como texto (`StatusTag`), nunca sólo color.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = activity.timeLabel,
            modifier = Modifier
                .width(44.dp)
                .padding(top = 2.dp),
            style = TypeTokens.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val categoryInfo = activity.categoryName?.let {
                    com.parental.shared.core.ui.components.CategoryInfo(
                        name = it,
                        colorHex = activity.categoryColorHex,
                        emoji = null,
                    )
                }
                val triple = categoryTagTriple(categoryInfo)
                if (triple != null) {
                    TagDot(color = triple.dot, size = 8.dp)
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text(
                        text = activity.categoryName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                StatusTag(status = activity.status)
            }
        }
    }
}

private fun activityCountLabel(total: Int): String =
    if (total == 1) "1 actividad" else "$total actividades"

// ── S3 · franja de métricas, cada celda pulsable ─────────────────────────

@Composable
private fun HomeMetricStrip(
    metrics: HomeMetrics,
    onOpenActivities: () -> Unit,
    onOpenExpenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCell(
                label = "Asignadas",
                value = metrics.assignedToMe?.toString() ?: METRIC_UNAVAILABLE,
                modifier = Modifier.weight(1f),
                onClick = onOpenActivities,
                contentDescription = "Asignadas a vos: ${metrics.assignedToMe ?: "sin dato"}. Abrir Actividades",
            )
            StripHairline()
            MetricCell(
                label = "Vencidas",
                value = metrics.overdue?.toString() ?: METRIC_UNAVAILABLE,
                modifier = Modifier.weight(1f),
                valueColor = if (metrics.overdue != null && metrics.overdue > 0) {
                    overdueContentColor()
                } else {
                    Color.Unspecified
                },
                onClick = onOpenActivities,
                contentDescription = "Vencidas: ${metrics.overdue ?: "sin dato"}. Abrir Actividades",
            )
            StripHairline()
            MetricCell(
                label = "Saldo gastos",
                value = metrics.balance?.let { formatMoney(it) } ?: METRIC_UNAVAILABLE,
                modifier = Modifier.weight(1.4f),
                valueColor = balanceValueColor(metrics.balance),
                onClick = onOpenExpenses,
                contentDescription = "Saldo de gastos: " +
                    (metrics.balance?.let { formatMoney(it) } ?: "sin dato") +
                    ". Abrir Gastos",
            )
        }
    }
}

@Composable
private fun StripHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(StripHeight)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private val StripHeight = 70.dp

@Composable
private fun MetricCell(
    label: String,
    value: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 70.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Lo que se muestra cuando una métrica no se pudo calcular: un hueco, nunca un `0`.
 *
 * Un `0` de mentira ("no tenés ninguna") es peor que un hueco: el usuario lo lee
 * como un dato. `null` significa que la lista o el resumen no llegaron.
 */
private const val METRIC_UNAVAILABLE = "—"

@Composable
private fun overdueContentColor(): Color =
    StatusTokens.triple(StatusTokens.Status.Overdue, isSystemInDarkTheme()).content

/**
 * El color del saldo: verde si me deben, rojo si debo, neutro si es cero o no llegó.
 *
 * Los colores salen de los TOKENS de status (`Ok`/`Ko`), no de un hex suelto: el
 * semáforo del saldo es el mismo que el de "vínculo activo" en cualquier otra parte
 * de la app.
 */
@Composable
private fun balanceValueColor(balance: Double?): Color {
    if (balance == null || balance == 0.0) return Color.Unspecified
    val status = if (balance > 0) StatusTokens.Status.Ok else StatusTokens.Status.Ko
    return StatusTokens.triple(status, isSystemInDarkTheme()).content
}

@Composable
private fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

// ── S4 · cumplimiento con anillo ─────────────────────────────────────────

@Composable
private fun ComplianceRingCard(
    compliance: ComplianceResponse,
    userName: String,
    modifier: Modifier = Modifier,
) {
    val currentMemberIndex = findCurrentMemberIndex(
        members = compliance.members,
        userName = userName,
    )
    val currentMember = compliance.members.getOrNull(currentMemberIndex)
    val otherMembers = if (currentMemberIndex >= 0) {
        compliance.members.filterIndexed { index, _ -> index != currentMemberIndex }
    } else {
        compliance.members
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cumplimiento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = compliancePeriodLabel(
                        period = compliance.period,
                        completed = currentMember?.completed,
                        total = currentMember?.total,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (compliance.members.isEmpty() || currentMember == null) {
                TodayEmptyLine(text = "No hay datos de cumplimiento")
            } else {
                Spacer(modifier = Modifier.height(Spacing.sm))
                ComplianceRing(
                    percentage = currentMember.percentage,
                    completed = currentMember.completed,
                    total = currentMember.total,
                )
            }

            otherMembers.forEach { member ->
                HairlineDivider()
                MemberComplianceRow(member = member)
            }
        }
    }
}

private fun compliancePeriodLabel(period: String, completed: Int?, total: Int?): String {
    val name = period.trim().ifBlank { "Este mes" }
    return if (completed != null && total != null) {
        "$name · $completed de $total tareas"
    } else {
        name
    }
}

/**
 * El anillo de 12 segmentos (S4): el color va de rojo a verde con el avance del mes.
 *
 * El relleno NO es un degradé: cada banda es un status del alfabeto (`Overdue`,
 * `InProgress`, `Verify`, `Done`) y el anillo usa su par dot/content. Un solo nodo de
 * semántica porque el número y el anillo dicen lo mismo.
 */
@Composable
private fun ComplianceRing(
    percentage: Double,
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val band = complianceBand(percentage)
    val triple = StatusTokens.triple(band, dark)
    val filled = complianceSegmentsFilled(percentage)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val percentageLabel = formatPercentage(percentage)
    val description = "Cumplimiento de $percentageLabel, $completed de $total tareas"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(124.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            val strokeWidth = 11.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(
                x = (size.width - radius * 2f) / 2f,
                y = (size.height - radius * 2f) / 2f,
            )
            val arcSize = Size(radius * 2f, radius * 2f)
            val sweep = 360f / COMPLIANCE_RING_SEGMENTS
            val gap = 3f

            repeat(COMPLIANCE_RING_SEGMENTS) { index ->
                drawArc(
                    color = if (index < filled) triple.dot else trackColor,
                    startAngle = -90f + index * sweep + gap / 2f,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }
        }

        // El número repite el color de la banda; el "X de Y" lo aclara en palabras.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentageLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = triple.content,
            )
            Text(
                text = "$completed de $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberComplianceRow(
    member: ComplianceMember,
    modifier: Modifier = Modifier,
) {
    val memberName = displayName(member.firstName, member.lastName).ifBlank {
        "Miembro sin nombre"
    }
    val percentage = formatPercentage(member.percentage)
    val progress = normalizedPercentage(member.percentage) / 100f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = memberName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = percentage,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ComplianceUnavailableCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No se pudo cargar el cumplimiento",
                modifier = Modifier.clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = "Error de cumplimiento"
                    contentDescription = "No se pudo cargar el cumplimiento"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Reintentar")
            }
        }
    }
}

/** Se conserva para el estado de carga del cumplimiento (contrato previo del Home). */
@Composable
fun ComplianceCardLoading(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Cargando cumplimiento"
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Cargando cumplimiento",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── S5 · hijos ───────────────────────────────────────────────────────────

@Composable
private fun ChildrenSection(
    childStatuses: List<ChildStatus>,
    children: List<ChildProfile>,
    modifier: Modifier = Modifier,
) {
    // `childStatuses` vacío = no se pudo calcular la carga vencida: se muestran los
    // nombres sin estado en vez de afirmar "Al día" sobre un dato que no llegó.
    val statuses: List<ChildStatus> = childStatuses.ifEmpty {
        children.map { ChildStatus(child = it, overdue = -1) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Hijos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (statuses.isEmpty()) {
            EmptyChildrenCard()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                statuses.forEach { status ->
                    ChildChip(status = status)
                }
            }
        }
    }
}

@Composable
private fun ChildChip(status: ChildStatus, modifier: Modifier = Modifier) {
    val child = status.child
    val displayName = displayName(child.firstName, child.lastName).ifBlank {
        "Hijo sin nombre"
    }
    val initial = safeInitial(child)
    val statusLabel = when {
        status.overdue < 0 -> null
        status.overdue == 0 -> "Al día"
        status.overdue == 1 -> "1 vencida"
        else -> "${status.overdue} vencidas"
    }
    val statusDescription = statusLabel ?: "sin datos de vencidas"

    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Hijo: $displayName. $statusDescription"
        },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.xs,
                end = Spacing.md,
                top = Spacing.xs,
                bottom = Spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.overdue > 0) {
                            overdueContentColor()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChildrenCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription =
                    "No hay hijos registrados. Los hijos aparecerán aquí cuando se agreguen al vínculo."
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No hay hijos registrados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Los hijos aparecerán aquí cuando se agreguen al vínculo.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── sin vínculo ──────────────────────────────────────────────────────────

@Composable
private fun NoBondState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "No hay vínculo activo. Vínculo inactivo."
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "No hay vínculo activo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            BondStatusPill(label = "Vínculo inactivo", active = false)
        }
    }
}

// ── helpers ──────────────────────────────────────────────────────────────

private val MONTH_ABBREVIATIONS = listOf(
    "ene",
    "feb",
    "mar",
    "abr",
    "may",
    "jun",
    "jul",
    "ago",
    "sep",
    "oct",
    "nov",
    "dic",
)

private fun displayName(
    firstName: String,
    lastName: String,
): String = listOf(firstName.trim(), lastName.trim())
    .filter { it.isNotBlank() }
    .joinToString(" ")

private fun safeInitial(child: ChildProfile): String =
    child.firstName.trim().firstOrNull()?.toString()
        ?: child.lastName.trim().firstOrNull()?.toString()
        ?: "?"

private fun findCurrentMemberIndex(
    members: List<ComplianceMember>,
    userName: String,
): Int {
    val normalizedUserName = userName.trim()
    if (normalizedUserName.isBlank()) return -1

    return members.indexOfFirst { member ->
        val firstName = member.firstName.trim()
        val fullName = displayName(member.firstName, member.lastName)
        firstName.equals(normalizedUserName, ignoreCase = true) ||
            fullName.equals(normalizedUserName, ignoreCase = true)
    }
}

private fun normalizedPercentage(value: Double): Float =
    value
        .takeIf { it.isFinite() }
        ?.coerceIn(0.0, 100.0)
        ?.toFloat()
        ?: 0f

private fun formatPercentage(value: Double): String {
    val safeValue = value
        .takeIf { it.isFinite() }
        ?.coerceIn(0.0, 100.0)
        ?: 0.0
    return "${kotlin.math.round(safeValue * 10) / 10.0}%"
}
