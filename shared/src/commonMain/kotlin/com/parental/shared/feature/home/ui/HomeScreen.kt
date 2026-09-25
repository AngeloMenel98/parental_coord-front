package com.parental.shared.feature.home.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceMember
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeSummary
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit = {},
    onNavigateBottom: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error
    val summary = uiState.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Coordinación Parental",
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("Cerrar sesión")
                    }
                },
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
                    complianceLoading = uiState.complianceLoading,
                    complianceError = uiState.complianceError,
                    onRetryCompliance = { viewModel.loadHomeData() },
                )
            }
        }
    }
}

@Composable
private fun HomeLoadingState(modifier: Modifier = Modifier) {
    CenteredHomeState(modifier = modifier) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Cargando el inicio"
                },
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cargando el inicio",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
                    .padding(24.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
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
                    .padding(24.dp),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No hay datos para mostrar en este momento.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HomeContent(
    summary: HomeSummary,
    userName: String,
    complianceLoading: Boolean = false,
    complianceError: String? = null,
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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                GreetingSection(userName = userName)
            }

            if (summary.bondId == null) {
                item {
                    NoBondState()
                }
            } else {
                item {
                    BondInfoCard(summary = summary)
                }
                item {
                    when {
                        complianceLoading -> ComplianceCardLoading()
                        complianceError != null -> ComplianceCardError(
                            onRetry = onRetryCompliance,
                        )
                        summary.compliance != null -> ComplianceCard(
                            compliance = summary.compliance,
                            userName = userName,
                        )
                        else -> ComplianceUnavailableCard()
                    }
                }
            }

            item {
                SectionTitle("Familiares")
            }

            if (summary.children.isEmpty()) {
                item {
                    EmptyChildrenCard()
                }
            } else {
                items(summary.children) { child ->
                    ChildCard(child = child)
                }
            }
        }
    }
}

@Composable
private fun GreetingSection(
    userName: String,
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
    }
    val monthNames = listOf(
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
    val monthName = monthNames[localDate.monthNumber - 1]
    val displayName = userName.trim()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$dayOfWeek, ${localDate.dayOfMonth} $monthName ${localDate.year}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (displayName.isBlank()) "Hola" else "Hola, $displayName",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

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
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "No hay vínculo activo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BondStatusPill(label = "Vínculo inactivo", active = false)
        }
    }
}

@Composable
private fun BondInfoCard(
    summary: HomeSummary,
    modifier: Modifier = Modifier,
) {
    val isActive = summary.compliance?.bondActive != false
    val statusLabel = if (isActive) "Vínculo activo" else "Vínculo inactivo"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            summary.bondTitle
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

            summary.bondType
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { type ->
                    Text(
                        text = type.replaceFirstChar { character -> character.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

            Spacer(modifier = Modifier.height(12.dp))
            BondStatusPill(label = statusLabel, active = isActive)
        }
    }
}

@Composable
private fun BondStatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
            stateDescription = label
        },
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ComplianceCard(
    compliance: ComplianceResponse,
    userName: String,
    modifier: Modifier = Modifier,
) {
    val currentMemberIndex = findCurrentMemberIndex(
        members = compliance.members,
        userName = userName,
    )
    val currentMember = compliance.members.getOrNull(currentMemberIndex)
    val remainingMembers = if (currentMemberIndex >= 0) {
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
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Cumplimiento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = compliance.period.trim().ifBlank { "Este mes" },
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (compliance.members.isEmpty()) {
                Text(
                    text = "No hay datos de cumplimiento",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                currentMember?.let { member ->
                    CurrentMemberSummary(member = member)
                    if (remainingMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Restantes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                remainingMembers.forEach { member ->
                    ComplianceMemberRow(member = member)
                    if (member != remainingMembers.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            BondStatusPill(
                label = if (compliance.bondActive) "Vínculo activo" else "Vínculo inactivo",
                active = compliance.bondActive,
            )
        }
    }
}

@Composable
private fun CurrentMemberSummary(
    member: ComplianceMember,
    modifier: Modifier = Modifier,
) {
    val percentage = formatPercentage(member.percentage)
    val memberName = displayName(member.firstName, member.lastName).ifBlank {
        "Miembro sin nombre"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = memberName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = percentage,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${member.completed} de ${member.total} tareas completadas",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ComplianceProgress(
            member = member,
            label = "Cumplimiento de $memberName",
        )
    }
}

@Composable
private fun ComplianceMemberRow(
    member: ComplianceMember,
    modifier: Modifier = Modifier,
) {
    val memberName = displayName(member.firstName, member.lastName).ifBlank {
        "Miembro sin nombre"
    }
    val percentage = formatPercentage(member.percentage)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = memberName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = percentage,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        ComplianceProgress(
            member = member,
            label = "Cumplimiento de $memberName",
        )
    }
}

@Composable
private fun ComplianceProgress(
    member: ComplianceMember,
    label: String,
    modifier: Modifier = Modifier,
) {
    val progress = normalizedPercentage(member.percentage)
    val percentage = formatPercentage(member.percentage)

    LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.small)
            .semantics {
                contentDescription = "$label: $percentage"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress,
                    range = 0f..100f,
                )
            },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cargando cumplimiento",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ComplianceCardError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComplianceErrorCard(
        message = "No se pudo cargar el cumplimiento",
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun ComplianceErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                modifier = Modifier.clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = "Error de cumplimiento"
                    contentDescription = message
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun ComplianceUnavailableCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Cumplimiento no disponible"
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cumplimiento no disponible",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChildCard(
    child: ChildProfile,
    modifier: Modifier = Modifier,
) {
    val displayName = displayName(child.firstName, child.lastName).ifBlank {
        "Familiar sin nombre"
    }
    val initial = safeInitial(child)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Familiar: $displayName. Inicial: $initial"
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
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
                    "No hay familiares registrados. Los familiares aparecerán aquí cuando se agreguen al vínculo."
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No hay familiares registrados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Los familiares aparecerán aquí cuando se agreguen al vínculo.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

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
