package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing

/*
 * Cromo que no es una fila — `calendar-design-redesign`: T4 (paso 3) lo mudó, T5-3
 * (`R3-T5-3`) colapsó sus TRES estados verbatim en `EmptyState`. DD-9: separarlos
 * fue lo que dejó a T4 cumplir su promesa de delta cero; por eso el colapso es una
 * tarea aparte y no una limpieza de paso.
 *
 * ADOPCIÓN DE TOKENS — sólo nombres, ningún valor cambia; cada valor sigue siendo de su
 * tarea. Este archivo queda SIN HOLGURA contra su techo de 220: lo que quepa en otro
 * sitio, va en otro sitio.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TimelineControls(
    mode: TimelineMode,
    onModeChange: (TimelineMode) -> Unit,
    selectedTab: ActivityTab,
    onSelectTab: (ActivityTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = Spacing.maxContentWidth)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Vista",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TimelineMode.entries.forEachIndexed { index, timelineMode ->
                SegmentedButton(
                    selected = mode == timelineMode,
                    onClick = { onModeChange(timelineMode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TimelineMode.entries.size,
                    ),
                    modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
                ) {
                    Text(timelineMode.label)
                }
            }
        }

        Text(
            text = "Filtrar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ActivityTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label) },
                    modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy),
                )
            }
        }
    }
}

/**
 * Los TRES estados vacíos/error del calendario, en un solo composable — `R3-T5-3`.
 * Reemplaza a `TimelineEmpty` (×2 call-sites), `ListErrorState` y `EmptyListState`.
 * ⚠ **CERO COPY NUEVO** salvo el título de error que prescribe el registro: los tres
 * vacíos pasan su texto tal cual como `title` y `message` va `""`; partir "…para
 * este filtro" en título + cuerpo sería reescribir copy.
 * `fillMaxHeight()` y NO `fillMaxSize()`: los dos vacíos viven en items de
 * `LazyColumn` (altura no acotada), donde `fillMaxSize()` no haría nada; en pantalla
 * completa reproduce el `Box(contentAlignment = Center)` previo.
 * **UN nodo de semántica, acción FUERA**: el `mergeDescendants` cubre ilustración +
 * título + cuerpo y el botón es hermano — fusionarlo rompería el foco (R7-T2-7).
 */
@Composable
internal fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(vertical = Spacing.padded),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ilustración DECORATIVA — sustituye al emoji 📅, que era un fallo duro
            // (sin `contentDescription` en una `Column` sin `mergeDescendants`,
            // TalkBack anunciaba un emoji suelto). ⚠ GLIFO PROVISIONAL, paralelo a
            // OI-4: `prd-v4` no nombra forma; `EventBusy` es "calendario sin eventos".
            Icon(
                imageVector = Icons.Filled.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(MaterialTheme.typography.headlineLarge.fontSize.value.dp)
                    .clearAndSetSemantics { },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // S1-T5-3a: sin esto salía pegado a la izquierda aun centrando.
                textAlign = TextAlign.Center,
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = AgendaGeometry.touchTargetLegacy)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun ListSkeleton() {
    // T5-6 (i): el rótulo vive en el CONTENEDOR — un nodo, y `clearAndSetSemantics`
    // limpia los cuatro hijos. Antes las cuatro tarjetas lo decían: cuatro veces.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .clearAndSetSemantics { contentDescription = "Cargando actividades" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = Spacing.maxContentWidth)
                    .align(Alignment.CenterHorizontally)
                    .clearAndSetSemantics { },
                shape = ParentalShapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(AgendaGeometry.skeletonDateBlock)
                            .height(AgendaGeometry.skeletonDateHeight)
                            .clip(ParentalShapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    // T5-6 (iv): sin línea PROPIA — la de la lista cargada, así el
                    // contraste no cae a 1.67:1 en el instante de la carga.
                    AgendaTimelineLine()
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(Spacing.lg)
                                .clip(ParentalShapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(Spacing.md)
                                .clip(ParentalShapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}
