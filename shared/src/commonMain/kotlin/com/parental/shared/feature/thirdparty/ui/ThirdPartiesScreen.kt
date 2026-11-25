package com.parental.shared.feature.thirdparty.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.BottomNavBar
import com.parental.shared.core.ui.components.BottomNavItem
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.MotionTokens
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.feature.activities.ui.currentDateIn
import com.parental.shared.feature.thirdparty.domain.model.ThirdPartyRelationshipOption
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt

/**
 * Terceros del progenitor — artboard `design/terceros-ui/04-vigencia-a-la-vista.html`.
 *
 * Es la pantalla que **da** el acceso, no la que lo recibe (`ThirdPartyHomeScreen`): lista
 * los grants que este progenitor autorizó, los crea en un asistente de dos pasos, los edita
 * y los revoca.
 *
 * Decisiones visuales que vienen del artboard y no se negocian acá:
 * - **Tarjetas**, con el resumen de vigencia ("Hasta el 12 dic 2026 · quedan 26 días") y su
 *   barra de período. La fecha y los días se dicen en palabras: el color nunca es la única
 *   señal del design system.
 * - El resumen de permisos **no** va en las tarjetas: vive en el paso 2 del asistente
 *   (confirmación antes de otorgar) y, completo, detrás del icono ⓘ del topbar.
 * - **Editar y Revocar viven en un rail que se descubre deslizando la tarjeta a la
 *   izquierda**, y ese gesto es un atajo, no la única vía: cada tarjeta ofrece las mismas
 *   dos acciones en un menú visible (⋮) y como `CustomAccessibilityAction` para lectores de
 *   pantalla. Un control alcanzable sólo por gesto no es accesible.
 * - Nada de "Renovar": el PATCH del back responde 409 sobre un grant que no está `active`
 *   (no reactiva), así que un acceso vencido o revocado no ofrece acciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartiesScreen(
    viewModel: ThirdPartiesViewModel,
    onLogout: () -> Unit = {},
    onNavigateBottom: (String) -> Unit = {},
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    val wizard = state.wizard
    if (wizard != null) {
        // El asistente es pantalla completa y sin barra de navegación: es un flujo con
        // principio y fin, y un tab a mitad del alta sólo puede perder lo que se escribió.
        ThirdPartiesWizard(
            wizard = wizard,
            isSaving = state.isSaving,
            onBack = {
                if (wizard.step == ThirdPartyWizardStep.QUIEN) {
                    viewModel.closeWizard()
                } else {
                    viewModel.back()
                }
            },
            onClose = viewModel::closeWizard,
            onEmail = viewModel::setEmail,
            onFirstName = viewModel::setFirstName,
            onLastName = viewModel::setLastName,
            onPhone = viewModel::setPhone,
            onDocument = viewModel::setDocument,
            onRelationship = viewModel::setRelationship,
            onDuration = viewModel::setDuration,
            onAccessEnd = viewModel::setAccessEnd,
            onNote = viewModel::setNote,
            onNext = viewModel::next,
            onStepBack = viewModel::back,
            onSubmit = viewModel::submit,
            onOpenPolicy = viewModel::openPolicySheet,
        )
    } else {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = "Terceros",
                    onLogout = onLogout,
                    unreadCount = unreadCount,
                    onOpenNotifications = onOpenNotifications,
                    actions = {
                        IconButton(onClick = viewModel::openPolicySheet) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Qué puede hacer un tercero",
                            )
                        }
                        IconButton(onClick = viewModel::openCreate) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Otorgar un acceso",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                BottomNavBar(
                    selectedRoute = BottomNavItem.Terceros.route,
                    onNavigate = onNavigateBottom,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading -> ContentListSkeleton(
                        contentDescription = "Cargando los accesos",
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.loadError != null && state.items.isEmpty() -> ThirdPartiesErrorState(
                        message = state.loadError.orEmpty(),
                        onRetry = viewModel::load,
                    )

                    state.isEmpty -> EmptyContentState(
                        title = "Todavía no otorgaste accesos",
                        message = "Cuando le des acceso temporal a alguien, lo vas a ver acá con su fecha de vencimiento.",
                        modifier = Modifier.fillMaxSize(),
                        actionLabel = "Otorgar un acceso",
                        onAction = viewModel::openCreate,
                    )

                    else -> ThirdPartiesContent(
                        state = state,
                        onEdit = viewModel::openEdit,
                        onRevoke = viewModel::askRevoke,
                        onCreate = viewModel::openCreate,
                    )
                }
            }
        }
    }

    if (state.showPolicySheet) {
        ThirdPartiesPolicySheet(onDismiss = viewModel::dismissPolicySheet)
    }

    val edit = state.edit
    if (edit != null) {
        ThirdPartyEditSheet(
            edit = edit,
            isSaving = state.isSaving,
            onRelationship = viewModel::setEditRelationship,
            onFirstName = viewModel::setEditFirstName,
            onLastName = viewModel::setEditLastName,
            onPhone = viewModel::setEditPhone,
            onDocument = viewModel::setEditDocument,
            onAccessEnd = viewModel::setEditAccessEnd,
            onNote = viewModel::setEditNote,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::closeEdit,
        )
    }

    val pendingRevoke = state.pendingRevoke
    if (pendingRevoke != null) {
        RevokeAccessDialog(
            item = pendingRevoke,
            isSaving = state.isSaving,
            onConfirm = viewModel::revoke,
            onDismiss = viewModel::dismissRevoke,
        )
    }
}

/**
 * La lista: encabezado, tarjetas agrupadas por vigencia real y el alta al final.
 *
 * Los dos grupos son "Con acceso" y "Sin acceso vigente" porque la diferencia importa para
 * lo único que se puede hacer acá: sobre un acceso vencido no hay nada que editar ni
 * revocar (el back responde 409), así que la tarjeta lo dice y no ofrece acciones.
 */
@Composable
private fun ThirdPartiesContent(
    state: ThirdPartiesUiState,
    onEdit: (ThirdPartyAccessUi) -> Unit,
    onRevoke: (ThirdPartyAccessUi) -> Unit,
    onCreate: () -> Unit,
) {
    // Una sola tarjeta abierta a la vez (mismo criterio que la lista de gastos): dos rails
    // abiertos dejarían dos juegos de acciones compitiendo por el mismo lugar.
    var openId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Accesos que otorgaste",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Sólo vos ves y administrás los que autorizaste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.active.isNotEmpty()) {
            item(key = "label-active") { GroupLabel("Con acceso") }
            items(state.active.size, key = { "active-${state.active[it].id}" }) { index ->
                val item = state.active[index]
                ThirdPartyGrantRow(
                    item = item,
                    open = openId == item.id,
                    onOpenChange = { open -> openId = if (open) item.id else null },
                    onEdit = { onEdit(item) },
                    onRevoke = { onRevoke(item) },
                )
            }
        }

        if (state.inactive.isNotEmpty()) {
            item(key = "label-inactive") { GroupLabel("Sin acceso vigente") }
            items(state.inactive.size, key = { "inactive-${state.inactive[it].id}" }) { index ->
                val item = state.inactive[index]
                ThirdPartyGrantCard(item = item, openActions = null, showActionsMenu = false)
            }
        }

        item(key = "create") {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = ParentalShapes.extraLarge,
            ) {
                Text("Otorgar un acceso")
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/**
 * Una tarjeta con acceso vigente: es la tarjeta del artboard envuelta en el rail de
 * acciones.
 *
 * El rail NO es la única vía (ver el KDoc de la pantalla): `ThirdPartyGrantCard` recibe
 * `openActions` para pintar el menú ⋮ con las mismas dos acciones, y la tarjeta expone las
 * dos como acciones de accesibilidad.
 */
@Composable
private fun ThirdPartyGrantRow(
    item: ThirdPartyAccessUi,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit,
) {
    ThirdPartyActionRail(
        open = open,
        onOpenChange = onOpenChange,
        onEdit = onEdit,
        onRevoke = onRevoke,
        name = item.name,
    ) {
        ThirdPartyGrantCard(
            item = item,
            openActions = onEdit to onRevoke,
            showActionsMenu = true,
        )
    }
}

/**
 * El rail de acciones (Editar / Revocar) que aparece al deslizar la tarjeta a la izquierda.
 *
 * Mismas decisiones que el rail de gastos del proyecto, sin reutilizar ese composable: el
 * de actividades está prohibido en este paquete por el contrato de la vista del terciario
 * (`ThirdPartyRestrictionsContractTest`), y ese contrato es más importante que ahorrar
 * cuarenta líneas.
 *
 * - **Controlado por el llamador** ([open] + [onOpenChange]) para que la lista cierre la
 *   tarjeta anterior al abrir otra.
 * - **Accesibilidad**: las dos acciones se anuncian como [CustomAccessibilityAction] en el
 *   contenido, así que TalkBack las ofrece sin el gesto; los botones visuales del rail
 *   quedan fuera del árbol de semántica para no duplicar el mismo control dos veces.
 * - **Sin APIs experimentales**: arrastre + animación, estables en la versión del módulo.
 */
@Composable
private fun ThirdPartyActionRail(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit,
    name: String,
    content: @Composable () -> Unit,
) {
    val actionWidth = 88.dp
    val maxOffsetPx = with(LocalDensity.current) { (actionWidth * 2).toPx() }
    var offsetPx by remember { mutableFloatStateOf(if (open) -maxOffsetPx else 0f) }
    val scope = rememberCoroutineScope()
    val duration = MotionTokens.durationStandard

    suspend fun settle(target: Float) {
        animate(
            initialValue = offsetPx,
            targetValue = target,
            animationSpec = tween(durationMillis = duration),
        ) { value, _ -> offsetPx = value }
    }

    LaunchedEffect(open, maxOffsetPx) {
        settle(if (open) -maxOffsetPx else 0f)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize().clearAndSetSemantics { },
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            ) {
                RailButton(
                    label = "Editar",
                    background = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    width = actionWidth,
                    onClick = {
                        onOpenChange(false)
                        onEdit()
                    },
                )
                RailButton(
                    label = "Revocar",
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    width = actionWidth,
                    onClick = {
                        onOpenChange(false)
                        onRevoke()
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(offsetPx.roundToInt(), 0)
                    }
                }
                .pointerInput(open, maxOffsetPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldOpen = offsetPx < -maxOffsetPx / 2f
                            onOpenChange(shouldOpen)
                            scope.launch { settle(if (shouldOpen) -maxOffsetPx else 0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-maxOffsetPx, 0f)
                        },
                    )
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(label = "Editar el acceso de $name") {
                            onEdit()
                            true
                        },
                        CustomAccessibilityAction(label = "Revocar el acceso de $name") {
                            onRevoke()
                            true
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun RailButton(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * La tarjeta del artboard: quién, relación y estado; vigencia con barra; y —sólo en un
 * acceso vigente— el menú de acciones como vía sin gesto.
 *
 * `openActions` en `null` significa "esta tarjeta no ofrece acciones" (grant vencido,
 * revocado o suspendido): el back no permite editarlo ni revocarlo, así que la tarjeta no
 * promete lo que va a fallar.
 */
@Composable
private fun ThirdPartyGrantCard(
    item: ThirdPartyAccessUi,
    openActions: Pair<() -> Unit, () -> Unit>?,
    showActionsMenu: Boolean,
) {
    val dark = isSystemInDarkTheme()
    var menuOpen by remember { mutableStateOf(false) }
    val vigencia = if (item.hasAccess) {
        listOfNotNull(
            item.endDateLabel?.let { "Hasta el $it" },
            item.remainingLabel,
        ).joinToString(" · ")
    } else {
        when (item.status) {
            "expired" -> item.endDateLabel?.let { "Venció el $it" } ?: "Venció"
            "revoked" -> "Revocado"
            "suspended" -> item.endDateLabel?.let { "Suspendido · hasta el $it" } ?: "Suspendido"
            else -> item.endDateLabel?.let { "Hasta el $it" } ?: "Sin fecha de vencimiento"
        }
    }
    val spoken = listOfNotNull(
        item.name,
        item.relationshipLabel,
        item.statusLabel,
        vigencia,
    ).joinToString(", ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            Spacing.hairline,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(initials = item.initials, active = item.hasAccess)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.relationshipLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                AccessStatusChip(label = item.statusLabel, active = item.hasAccess, dark = dark)
            }

            Text(
                text = vigencia,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.hasAccess) {
                AccessProgressBar(
                    fraction = item.progress,
                    low = (item.daysLeft ?: Int.MAX_VALUE) <= LOW_DAYS_LEFT,
                    dark = dark,
                )
            }

            item.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val actions = openActions
            if (actions != null && showActionsMenu) {
                // La vía SIN gesto: un menú visible de 44dp con las mismas dos acciones del
                // rail. Sin esto, editar o revocar dependería de descubrir un swipe.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Acciones del acceso de ${item.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = {
                                menuOpen = false
                                actions.first()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Revocar",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                actions.second()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Umbral de "queda poco": el mismo naranja de `InProgress` del design system. */
private const val LOW_DAYS_LEFT = 7

@Composable
private fun Avatar(initials: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * La etiqueta de estado. El texto (`Activo`, `Vencido`, `Revocado`, `Suspendido`) es la
 * señal; el color sólo la refuerza, porque el design system prohíbe que el estado viaje
 * únicamente en el color.
 */
@Composable
private fun AccessStatusChip(label: String, active: Boolean, dark: Boolean) {
    val triple = StatusTokens.triple(
        if (active) StatusTokens.Status.Ok else StatusTokens.Status.Overdue,
        dark,
    )
    Surface(shape = ParentalShapes.medium, color = triple.dot.copy(alpha = 0.15f)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = triple.dot,
        )
    }
}

@Composable
private fun AccessProgressBar(fraction: Float?, low: Boolean, dark: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (low) {
        StatusTokens.triple(StatusTokens.Status.InProgress, dark).dot
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(ParentalShapes.extraSmall)
            .background(track)
            .clearAndSetSemantics { },
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(fill),
            )
        }
    }
}

@Composable
private fun ThirdPartiesErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
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

// ── Asistente de alta ───────────────────────────────────────────────────

/**
 * El asistente de dos pasos del artboard: quién (email + relación) y hasta cuándo
 * (duración o fecha, relación también visible, y el resumen de permisos como confirmación
 * antes de otorgar).
 *
 * El error del formulario es INLINE y hay uno solo: el del paso en el que se está. El
 * mensaje del servidor ocupa ese mismo lugar, así que la persona lo lee donde está
 * escribiendo y no en un snackbar que se va.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThirdPartiesWizard(
    wizard: ThirdPartyWizardState,
    isSaving: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onEmail: (String) -> Unit,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onDocument: (String) -> Unit,
    onRelationship: (String) -> Unit,
    onDuration: (ThirdPartyDuration) -> Unit,
    onAccessEnd: (String) -> Unit,
    onNote: (String) -> Unit,
    onNext: () -> Unit,
    onStepBack: () -> Unit,
    onSubmit: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { currentDateIn(zone) }
    val resolved = parseCalendarDate(wizard.accessEnd)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otorgar acceso", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (wizard.step == ThirdPartyWizardStep.QUIEN) {
                                "Cerrar el asistente"
                            } else {
                                "Volver al paso anterior"
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    Spacing.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (wizard.step == ThirdPartyWizardStep.QUIEN) {
                        Button(
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = ParentalShapes.extraLarge,
                        ) {
                            Text("Continuar")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStepBack,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = ParentalShapes.extraLarge,
                        ) {
                            Text("Atrás")
                        }
                        Button(
                            onClick = onSubmit,
                            enabled = !isSaving,
                            modifier = Modifier.weight(2f).heightIn(min = 48.dp),
                            shape = ParentalShapes.extraLarge,
                        ) {
                            Text("Otorgar acceso")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            WizardStepper(step = wizard.step)

            if (wizard.step == ThirdPartyWizardStep.QUIEN) {
                OutlinedTextField(
                    value = wizard.email,
                    onValueChange = onEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    placeholder = { Text("abuela@example.com") },
                    singleLine = true,
                    isError = wizard.error != null,
                )
                // S1 (thirdparty-identity): la identidad declarada se captura acá, no en
                // el paso 2, porque el paso HASTA_CUANDO ya carga la vigencia + permisos.
                OutlinedTextField(
                    value = wizard.firstName,
                    onValueChange = onFirstName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    placeholder = { Text("Pablo") },
                    singleLine = true,
                    isError = wizard.error == THIRD_PARTY_FIRST_NAME_MISSING,
                )
                OutlinedTextField(
                    value = wizard.lastName,
                    onValueChange = onLastName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Apellido") },
                    placeholder = { Text("Charles") },
                    singleLine = true,
                    isError = wizard.error == THIRD_PARTY_LAST_NAME_MISSING,
                )
                OutlinedTextField(
                    value = wizard.phone,
                    onValueChange = onPhone,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Teléfono (opcional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = wizard.document,
                    onValueChange = onDocument,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Documento (opcional)") },
                    singleLine = true,
                )
                Text(
                    text = "Si no tiene cuenta, la creamos con una contraseña provisoria y le mandamos los datos por mail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RelationshipChips(selected = wizard.relationship, onSelect = onRelationship)
                wizard.error?.let { FormError(it) }
            } else {
                WizardChosenPerson(
                    email = wizard.email,
                    firstName = wizard.firstName,
                    lastName = wizard.lastName,
                    relationship = wizard.relationship,
                )
                RelationshipChips(selected = wizard.relationship, onSelect = onRelationship)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Acceso hasta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DurationPresets(selected = wizard.duration, onSelect = onDuration)
                    if (wizard.duration == ThirdPartyDuration.CUSTOM) {
                        OutlinedTextField(
                            value = wizard.accessEnd,
                            onValueChange = onAccessEnd,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Fecha (AAAA-MM-DD)") },
                            placeholder = { Text("2026-12-12") },
                            singleLine = true,
                            isError = wizard.error != null,
                        )
                        Text(
                            text = "La fecha no puede ser anterior a hoy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (resolved != null) {
                        val left = remainingLabel(
                            thirdPartyDaysLeft(resolved, today),
                        )
                        Text(
                            text = listOfNotNull(
                                "Hasta el ${fullDateLabel(resolved)}",
                                left,
                            ).joinToString(" · ") + ". Después se corta solo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // El resumen de permisos vive ACÁ y no en las tarjetas (delta de diseño):
                // es la confirmación de lo que se está por otorgar, no un dato de la lista.
                Surface(
                    shape = ParentalShapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "Qué va a poder hacer",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = THIRD_PARTY_PERMISSION_SUMMARY,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                TextButton(
                    onClick = onOpenPolicy,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Qué puede hacer un tercero")
                }

                OutlinedTextField(
                    value = wizard.note,
                    onValueChange = onNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                )

                wizard.error?.let { FormError(it) }

                Text(
                    text = "Al otorgarlo, la persona recibe las credenciales por mail. Al entrar por primera vez le pedimos que cambie la contraseña.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WizardStepper(step: ThirdPartyWizardStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StepBadge(
            number = "1",
            label = "Quién",
            done = step != ThirdPartyWizardStep.QUIEN,
            current = step == ThirdPartyWizardStep.QUIEN,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Spacing.hairline)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .clearAndSetSemantics { },
        )
        StepBadge(
            number = "2",
            label = "Hasta cuándo",
            done = false,
            current = step == ThirdPartyWizardStep.HASTA_CUANDO,
        )
    }
}

@Composable
private fun StepBadge(number: String, label: String, done: Boolean, current: Boolean) {
    val numberBackground = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val numberContent = when {
        current -> MaterialTheme.colorScheme.onPrimary
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.clearAndSetSemantics {
            // El paso se anuncia en palabras: el número y el color no son la única señal.
            contentDescription = if (current) "$label, paso actual" else "$label"
        },
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(numberBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (done) "✓" else number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = numberContent,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun WizardChosenPerson(
    email: String,
    firstName: String,
    lastName: String,
    relationship: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ParentalShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            Spacing.hairline,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // S1: las iniciales salen del NOMBRE declarado; sin identidad, del email.
            val personName = listOfNotNull(
                firstName.trim().takeIf { it.isNotEmpty() },
                lastName.trim().takeIf { it.isNotEmpty() },
            ).joinToString(" ")
            val avatarSeed = personName.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
            Avatar(initials = initialsOf(avatarSeed), active = true)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                // S1: una vez declarada la identidad, es EL NOMBRE lo que se muestra;
                // el email queda como dato secundario de contacto. La variable vive en el
                // scope del Row (la calcula el bloque del Avatar).
                if (personName.isNotEmpty()) {
                    Text(
                        text = personName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ThirdPartyRelationshipOption.fromValue(relationship)?.let { option ->
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Los cuatro chips de relación: Familiar / Cuidador/a / Médico/a / Docente. */
@Composable
private fun RelationshipChips(selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Relación",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ThirdPartyRelationshipOption.entries.forEach { option ->
                val isSelected = ThirdPartyRelationshipOption.fromValue(selected) == option
                Surface(
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = ParentalShapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = if (isSelected) {
                        null
                    } else {
                        androidx.compose.foundation.BorderStroke(
                            Spacing.hairline,
                            MaterialTheme.colorScheme.outline,
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/** Los tres presets de duración: 30 días / 3 meses / elegir fecha. */
@Composable
private fun DurationPresets(selected: ThirdPartyDuration, onSelect: (ThirdPartyDuration) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ThirdPartyDuration.entries.forEach { duration ->
            val isSelected = selected == duration
            Surface(
                onClick = { onSelect(duration) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = ParentalShapes.small,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = if (isSelected) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(
                        Spacing.hairline,
                        MaterialTheme.colorScheme.outline,
                    )
                },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = duration.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics(mergeDescendants = true) { },
    )
}

// ── Edición ─────────────────────────────────────────────────────────────

/**
 * La hoja de edición: relación, vencimiento y nota de un acceso vigente.
 *
 * Sólo se abre para un grant `active` (ver `ThirdPartiesViewModel.openEdit`): el PATCH del
 * back responde 409 en el resto, así que un acceso vencido no ofrece esta hoja en lugar de
 * ofrecer un botón que siempre falla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThirdPartyEditSheet(
    edit: ThirdPartyEditState,
    isSaving: Boolean,
    onRelationship: (String) -> Unit,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onDocument: (String) -> Unit,
    onAccessEnd: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ParentalShapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Editar el acceso de ${edit.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            RelationshipChips(selected = edit.relationship, onSelect = onRelationship)

            // S9 (thirdparty-identity): identidad precargada del grant; NaN = el grant
            // previo a la feature no la tiene y el campo nace vacío para completarla.
            OutlinedTextField(
                value = edit.firstName.orEmpty(),
                onValueChange = onFirstName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre") },
                singleLine = true,
            )
            OutlinedTextField(
                value = edit.lastName.orEmpty(),
                onValueChange = onLastName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Apellido") },
                singleLine = true,
            )
            OutlinedTextField(
                value = edit.phone.orEmpty(),
                onValueChange = onPhone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Teléfono (opcional)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = edit.document.orEmpty(),
                onValueChange = onDocument,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Documento (opcional)") },
                singleLine = true,
            )

            OutlinedTextField(
                value = edit.accessEnd,
                onValueChange = onAccessEnd,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Vence el (AAAA-MM-DD)") },
                singleLine = true,
                isError = edit.error != null,
            )
            OutlinedTextField(
                value = edit.note,
                onValueChange = onNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota (opcional)") },
                singleLine = true,
            )
            edit.error?.let { FormError(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = ParentalShapes.extraLarge,
                ) {
                    Text("Volver")
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = ParentalShapes.extraLarge,
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

// ── Revocación ──────────────────────────────────────────────────────────

/**
 * La confirmación de la revocación: destructiva, en color de error y diciendo las dos cosas
 * que el contrato del back cumple —es inmediata y no se puede deshacer—.
 *
 * Revocar cierra el grant **y** la membresía del vínculo en la misma transacción, así que
 * no existe un "después lo reactivo": volver a darle acceso es un alta nueva.
 */
@Composable
private fun RevokeAccessDialog(
    item: ThirdPartyAccessUi,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Revocar el acceso de ${item.name}?") },
        text = {
            Text(
                "Deja de ver sus actividades al instante: el acceso se corta ahora y no se " +
                    "puede deshacer. Si más adelante lo necesita, vas a tener que otorgarlo de nuevo.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Revocar acceso")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Volver") }
        },
    )
}

// ── Matriz de permisos (hoja de información) ────────────────────────────

/**
 * La matriz `Qué puede y qué no`, detrás del icono ⓘ del topbar.
 *
 * No es un bloque permanente (delta de diseño): es un dato de consulta con el aviso de
 * revocación al pie, así la lista queda para lo que importa —quién tiene acceso y hasta
 * cuándo— y la explicación se lee cuando se busca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThirdPartiesPolicySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dark = isSystemInDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ParentalShapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Qué puede hacer un tercero",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "El acceso es acotado: sólo lo que le asignás, y con fecha de vencimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            THIRD_PARTY_CAPABILITIES.forEach { capability ->
                CapabilityRow(capability = capability, dark = dark)
            }
            // El aviso de revocación es la última línea de la matriz, no un popup aparte:
            // se lee justo donde se decide si conviene dar el acceso.
            Text(
                text = THIRD_PARTY_REVOKE_WARNING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = ParentalShapes.extraLarge,
            ) {
                Text("Entendido")
            }
        }
    }
}

@Composable
private fun CapabilityRow(capability: ThirdPartyCapability, dark: Boolean) {
    val triple = StatusTokens.triple(
        if (capability.allowed) StatusTokens.Status.Ok else StatusTokens.Status.Created,
        dark,
    )
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(triple.dot.copy(alpha = 0.15f))
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (capability.allowed) "✓" else "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = triple.dot,
            )
        }
        Text(
            text = capability.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (capability.allowed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
