package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing

/*
 * Fila de filtros — T3 de `odd/tasks/activities-cards-ui.md` (S5).
 *
 * SUSTITUYE a `TimelineControls` (CalendarChrome.kt), que pintaba DOS rótulos ("Vista" y
 * "Filtrar") y un control segmentado. El arteboard no tiene ninguno de los tres: los
 * filtros son una sola línea de chips con su CONTADOR, y el cambio de vista es un ícono
 * de la barra superior. Los rótulos se fueron con ellos: occupied ≈ 64dp verticales que
 * la lista de tarjetas necesita.
 *
 * El contador sale de la lista YA CARGADA (`tabCounts`), no de una segunda consulta: el
 * chip dice cuántos elementos habría, no cuántos hay en el servidor.
 *
 * La fila scrollea en horizontal (`horizontalScroll`), como la del arteboard: con fuente
 * grande o "Obligaciones" al final, tres chips no caben en 360dp y recortarlos sería
 * peor que desplazarlos.
 */

@Composable
internal fun ActivityFiltersRow(
    selectedTab: ActivityTab,
    onSelectTab: (ActivityTab) -> Unit,
    counts: Map<ActivityTab, Int>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = Spacing.maxContentWidth)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityTab.entries.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = (counts[tab] ?: 0).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
            )
        }
    }
}
