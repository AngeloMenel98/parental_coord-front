package com.parental.shared.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * S12 · G-5 — el tope del contador es **"9+"**, no "99+".
 *
 * Vive como función pura porque el tope es la regla y el `Text` es el pintado: una regla
 * que sólo existe dentro de un composable no se puede afirmar sin infraestructura de UI,
 * que este repo no tiene. `null` = no hay burbuja (contar cero "sin leer" no es noticia, y
 * un negativo menos todavía).
 */
internal const val UNREAD_BADGE_CAP: Int = 9

internal fun unreadBadgeLabel(count: Int): String? = when {
    count <= 0 -> null
    count > UNREAD_BADGE_CAP -> "$UNREAD_BADGE_CAP+"
    else -> count.toString()
}

/**
 * Campana con contador (HOME-08). Desde [S12] vive en `core`: la usan la barra global de
 * TODAS las vistas, no sólo Home, así que no puede depender de `feature/notifications`.
 *
 * Sin `BadgedBox` a propósito: es un `Box` con la burbuja en la esquina, así el número no
 * depende de la métrica interna del componente Material. El `contentDescription` del ícono
 * lleva el conteo —un lector de pantalla no ve la burbuja— y la burbuja se retira del árbol
 * accesible (`clearAndSetSemantics`) para no anunciar "9+" después de "12 sin leer".
 *
 * G-4: objetivo táctil de [AppTopBarGeometry.touchTarget] como PISO. El `IconButton` de
 * Material ya mide 48dp; esto impide que un tema lo encoja por debajo del mínimo.
 */
@Composable
fun UnreadBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(
            minWidth = AppTopBarGeometry.touchTarget,
            minHeight = AppTopBarGeometry.touchTarget,
        ),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = if (unreadCount > 0) {
                    "Notificaciones, $unreadCount sin leer"
                } else {
                    "Notificaciones"
                },
            )
            val label = unreadBadgeLabel(unreadCount)
            if (label != null) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onError,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
