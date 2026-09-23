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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coordinación Parental", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colorScheme.onPrimary)
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
        Box(modifier = Modifier.padding(padding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::loadHomeData) {
                            Text("Reintentar")
                        }
                    }
                }
            } else {
                uiState.summary?.let { summary ->
                    HomeContent(
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Greeting ───────────────────────────────────────────────
        item {
            GreetingSection(userName = userName)
        }

        // ── Bond info ──────────────────────────────────────────────
        summary.bondTitle?.let { title ->
            item {
                BondInfoCard(
                    title = title,
                    type = summary.bondType,
                    memberCount = summary.memberCount,
                )
            }
        }

        // ── Compliance card ────────────────────────────────────────
        summary.bondId?.let { bondId ->
            item {
                when {
                    complianceLoading -> ComplianceCardLoading()
                    complianceError != null -> ComplianceCardError(onRetry = onRetryCompliance)
                    summary.compliance != null -> ComplianceCard(
                        compliance = summary.compliance,
                        userName = userName,
                    )
                    else -> ComplianceCardLoading()
                }
            }
        }

        // ── Children cards ─────────────────────────────────────────
        if (summary.children.isNotEmpty()) {
            item {
                SectionTitle("Familiares (${summary.children.size})")
            }
            items(summary.children) { child ->
                ChildCard(child)
            }
        } else {
            item {
                EmptyChildrenCard()
            }
        }
    }
}

// ── Compliance card ──────────────────────────────────────────────

@Composable
fun ComplianceCard(
    compliance: ComplianceResponse,
    userName: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: title + period
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MI CUMPLIMIENTO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                )
                Text(
                    text = compliance.period.ifBlank { "Este mes" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current user's large percentage
            val currentUserMember = compliance.members.find { member ->
                member.firstName.trim().equals(userName.trim(), ignoreCase = true)
            }

            currentUserMember?.let { member ->
                Text(
                    text = "${(kotlin.math.round(member.percentage * 10) / 10)}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${member.completed} de ${member.total} tareas completadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Other members
            val otherMembers = compliance.members.filter { member ->
                !(member.firstName.trim().equals(userName.trim(), ignoreCase = true))
            }

            if (otherMembers.isNotEmpty()) {
                otherMembers.forEach { member ->
                    ComplianceMemberRow(member = member)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else if (currentUserMember == null && compliance.members.isNotEmpty()) {
                compliance.members.forEach { member ->
                    ComplianceMemberRow(member = member)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Bond status pill
            if (compliance.bondActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "Vínculo activo",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplianceMemberRow(
    member: ComplianceMember,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${member.firstName.trim()} ${member.lastName.trim()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (member.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${(kotlin.math.round(member.percentage * 10) / 10)}%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.End,
        )
    }
}

// ── Compliance loading ───────────────────────────────────────────

@Composable
fun ComplianceCardLoading(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

// ── Compliance error ─────────────────────────────────────────────

@Composable
fun ComplianceCardError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No se pudo cargar el cumplimiento",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

// ── Greeting ───────────────────────────────────────────────────────

@Composable
private fun GreetingSection(userName: String) {
    val now = Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dayOfWeek = when (localDate.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Lunes"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Martes"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Miércoles"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Jueves"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Viernes"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sábado"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Domingo"
        else -> {}
    }
    val monthNames = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )
    val monthName = monthNames[localDate.monthNumber - 1]

    Column {
        Text(
            text = "$dayOfWeek, ${localDate.dayOfMonth} $monthName ${localDate.year}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hola, $userName \uD83D\uDC4B",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Bond info card ─────────────────────────────────────────────────

@Composable
private fun BondInfoCard(
    title: String,
    type: String?,
    memberCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                type?.let {
                    Text(
                        text = it.replaceFirstChar { c -> c.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "$memberCount miembros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Section title ──────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ── Child card ─────────────────────────────────────────────────────

@Composable
private fun ChildCard(child: ChildProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = child.firstName.first().toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "${child.firstName} ${child.lastName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (child.dateOfBirth.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Nacimiento: ${child.dateOfBirth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Empty children card ────────────────────────────────────────────

@Composable
private fun EmptyChildrenCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay familiares registrados",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Los familiares aparecerán aquí cuando se agreguen al vínculo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
