package com.parental.shared.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parental.shared.model.ActivitySummary
import com.parental.shared.model.HomeSummary
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit = {},
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

        // ── Compliance indicator ───────────────────────────────────
        summary.compliance?.let { compliance ->
            item {
                ComplianceCard(compliance)
            }
        }

        // ── Quick counters ─────────────────────────────────────────
        item {
            CountersRow(
                pending = summary.pendingActivities,
                overdue = summary.overdueActivities,
                pendingExpenses = summary.pendingExpenses,
            )
        }

        // ── Upcoming activities ────────────────────────────────────
        if (summary.upcomingActivities.isNotEmpty()) {
            item {
                SectionTitle("Próximos")
            }
            items(summary.upcomingActivities) { activity ->
                ActivityCard(activity)
            }
        }

        // ── Child profile card ─────────────────────────────────────
        summary.child?.let { child ->
            item {
                ChildCard(child)
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
            text = "Hola, $userName 👋",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Compliance card ────────────────────────────────────────────────

@Composable
private fun ComplianceCard(compliance: com.parental.shared.model.ComplianceData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cumplimiento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Parent 1
            ComplianceRow(
                name = compliance.parent1Name,
                percentage = compliance.parent1Percentage,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Parent 2
            ComplianceRow(
                name = compliance.parent2Name,
                percentage = compliance.parent2Percentage,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ComplianceRow(
    name: String,
    percentage: Float,
    color: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}

// ── Counters ───────────────────────────────────────────────────────

@Composable
private fun CountersRow(
    pending: Int,
    overdue: Int,
    pendingExpenses: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CounterCard(
            label = "Pendientes",
            count = pending,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        CounterCard(
            label = "Vencidas",
            count = overdue,
            color = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        CounterCard(
            label = "Gastos",
            count = pendingExpenses,
            color = Color(0xFFFFF3E0),
            textColor = Color(0xFFE65100),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CounterCard(
    label: String,
    count: Int,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
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

// ── Activity card ──────────────────────────────────────────────────

@Composable
private fun ActivityCard(activity: ActivitySummary) {
    val categoryColor = when (activity.category) {
        "SALUD" -> Color(0xFFE53935)
        "EDUCACION" -> Color(0xFF1E88E5)
        "FAMILIAR" -> Color(0xFF8E24AA)
        "SOCIAL" -> Color(0xFF43A047)
        "RECREACION" -> Color(0xFFFB8C00)
        else -> MaterialTheme.colorScheme.outline
    }

    val statusColor = when (activity.status) {
        "OVERDUE" -> Color(0xFFE53935)
        "IN_PROGRESS" -> Color(0xFFFB8C00)
        "COMPLETED" -> Color(0xFF43A047)
        "ASSIGNED" -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.outline
    }

    val typeIcon = if (activity.type == "OBLIGATION") "📋" else "📅"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeIcon,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = activity.category.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.height(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activity.scheduledDate.take(10), // Show date only
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            // Status badge
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = when (activity.status) {
                        "OVERDUE" -> "Vencida"
                        "IN_PROGRESS" -> "En progreso"
                        "COMPLETED" -> "Cumplida"
                        "ASSIGNED" -> "Asignada"
                        else -> activity.status
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Child card ─────────────────────────────────────────────────────

@Composable
private fun ChildCard(child: com.parental.shared.model.ChildProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Familiar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
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
