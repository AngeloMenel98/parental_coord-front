package com.parental.shared.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parental.shared.core.ui.components.AppTopBar
import com.parental.shared.core.ui.components.ContentListSkeleton
import com.parental.shared.core.ui.components.EmptyContentState
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.util.formatRelativeTime
import com.parental.shared.feature.notifications.domain.model.NotificationItem

/**
 * Centro de notificaciones (slice 4): lista paginada de **no leídas**, eliminar una
 * y "borrar todas".
 *
 * Decisión de producto (2026-10-31): "eliminar" NO borra filas — el backend no
 * tiene delete y el doc de la wave fija que eliminar = marcar leída. Como el
 * listado se pide con `unreadOnly=true`, lo marcado desaparece de la vista y no
 * vuelve al refrescar. Consecuencia asumida: las leídas no se pueden volver a ver
 * (no hay endpoint de "marcar como no leída"), así que NO hay "Deshacer": un undo
 * sólo local que se pierde al refrescar sería una promesa falsa.
 *
 * Sin WebSocket ni push: la pantalla se refresca al abrirse, a mano (botón de la
 * barra) y cuando el poller del badge trae un conteo nuevo. El copy va en
 * castellano porque TODA la UI de esta app lo está (no hay recursos i18n).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    /**
     * S12 · G-1/G-2 — la barra global lleva la salida en TODAS las vistas. Esta pantalla
     * es una sub-vista de Home, así que la recibe desde `App.kt` igual que las demás.
     */
    onLogout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // N-9: "borrar todas" es irreversible para el usuario, así que pide confirmación.
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("¿Borrar todas las notificaciones?") },
            text = {
                Text("Se van a marcar como leídas y no van a volver a aparecer en esta lista.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.markAllRead()
                    },
                ) {
                    Text("Borrar todas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            // S12 · La barra global, con la flecha de vuelta como `navigationIcon`. Sin
            // campana: esta vista YA es la lista de notificaciones, así que una campana
            // acá abriría la pantalla en la que el usuario está. El resto de las
            // acciones ("Marcar todas", refrescar) sigue siendo de esta pantalla.
            AppTopBar(
                title = "Notificaciones",
                onLogout = onLogout,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                actions = {
                    // G-2 · "íconos solamente". Bajo la bandeja de no leídas, "marcar
                    // todas" y "borrar todas" son la misma operación, así que el ícono
                    // honesto es el tacho, no el doble tilde.
                    IconButton(
                        onClick = { showDeleteAllDialog = true },
                        enabled = !uiState.isMarkingAll && uiState.items.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Borrar todas las notificaciones",
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
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
            val error = uiState.error
            when {
                uiState.isLoading -> ContentListSkeleton("Cargando notificaciones")

                error != null && uiState.items.isEmpty() -> EmptyContentState(
                    title = "No pudimos cargar las notificaciones",
                    message = error,
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = "Reintentar",
                    onAction = { viewModel.refresh() },
                )

                uiState.items.isEmpty() -> EmptyContentState(
                    title = "No tenés notificaciones nuevas",
                    message = "Cuando haya novedades en tu coordinación van a aparecer acá. " +
                        "Las que ya leíste no se vuelven a mostrar.",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        NotificationRow(
                            item = item,
                            isMarking = item.id in uiState.readingIds,
                            onDelete = { viewModel.markRead(item.id) },
                        )
                    }

                    if (uiState.canLoadMore) {
                        item(key = "load-more") {
                            LoadMoreRow(
                                isLoading = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una fila de la bandeja. Toda fila que llega acá está SIN leer (`unreadOnly=true`),
 * así que no hay estilo "leída": el toque en la fila, o el tacho del extremo, la
 * elimina de la vista.
 */
@Composable
private fun NotificationRow(
    item: NotificationItem,
    isMarking: Boolean,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .semantics { stateDescription = "Sin leer" }
            .clickable(enabled = !isMarking, onClick = onDelete),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(Spacing.md)) {
            UnreadDot(isUnread = true)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = formatRelativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            IconButton(onClick = onDelete, enabled = !isMarking) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar notificación",
                )
            }
        }
    }
}

@Composable
private fun UnreadDot(isUnread: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (isUnread) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
    )
}
@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Button(onClick = onLoadMore) {
                Text("Cargar más")
            }
        }
    }
}
