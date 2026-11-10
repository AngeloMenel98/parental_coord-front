package com.parental.shared.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * S12 · G-4 — objetivo táctil mínimo de los íconos de la barra: **44×44 dp** (WCAG 2.5.5).
 *
 * Es un PISO, no un recorte: el `IconButton` de Material ya mide 48dp y este `sizeIn` sólo
 * impide que un tema lo encoja por debajo del mínimo. No se usa `AgendaGeometry`: ese token
 * pertenece al agenda y está documentado como "no consumir desde el chrome".
 */
object AppTopBarGeometry {
    val touchTarget: Dp = 44.dp
}

/**
 * S12 · G-3 — el estado de la confirmación de salida.
 *
 * Vive fuera del composable para que la regla se pueda probar sin infraestructura de UI:
 * **pedir NO cierra la sesión, confirmar SÍ (una sola vez), descartar NO**. Un `var` local
 * dentro de `AppTopBar` habría dejado G-3 sin test en un repo que no tiene `createComposeRule`.
 */
internal class LogoutConfirmationState {
    var isVisible by mutableStateOf(false)
        private set

    /** Tocar el ícono: sólo abre el diálogo. */
    fun request() {
        isVisible = true
    }

    /** "Cancelar" o toque fuera: cierra el diálogo sin tocar la sesión. */
    fun dismiss() {
        isVisible = false
    }

    /** "Cerrar sesión": baja el diálogo y recién ahí termina la sesión. */
    fun confirm(onLogout: () -> Unit) {
        isVisible = false
        onLogout()
    }
}

internal const val LOGOUT_LABEL = "Cerrar sesión"
internal const val LOGOUT_DIALOG_TITLE = "¿Cerrar sesión?"
internal const val LOGOUT_DIALOG_BODY =
    "Vas a salir de tu cuenta. Para volver a ver las actividades tendrás que entrar de nuevo."
internal const val LOGOUT_DIALOG_CANCEL = "Cancelar"

/**
 * S12 · G-1/G-2/G-3/G-4 — la ÚNICA barra superior de la app.
 *
 * Antes cada pantalla armaba su propio `TopAppBar` y la salida existía sólo en Home (como
 * botón de texto) y en Admin. Ahora hay un solo composable: título, los íconos globales
 * —campana con contador y salir con confirmación— y un slot para las acciones propias de
 * cada pantalla (calendario, vista, alta, "marcar todas"…), que NO desaparecen.
 *
 * `onOpenNotifications` en `null` esconde la campana en la vista que YA es la lista de
 * notificaciones (una campana ahí abriría la pantalla en la que el usuario ya está) y en
 * Admin, que no tiene vínculo ni endpoint de notificaciones: un control que no lleva a
 * ningún lado es peor que no tenerlo. La barra sigue presente en todas las vistas y la
 * salida también, que es lo que G-1/G-2 piden.
 *
 * Los colores son parámetro porque Actividades usa `surface` con hairline (artboard T3) y
 * el resto usa `primary`: un default fijo habría sido una regresión visual en una de las dos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
    ),
) {
    val logout = remember { LogoutConfirmationState() }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                // Un título largo no puede empujar los íconos fuera de la barra.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon ?: {},
        actions = {
            actions()
            if (onOpenNotifications != null) {
                UnreadBell(unreadCount = unreadCount, onClick = onOpenNotifications)
            }
            IconButton(
                onClick = { logout.request() },
                modifier = Modifier.sizeIn(
                    minWidth = AppTopBarGeometry.touchTarget,
                    minHeight = AppTopBarGeometry.touchTarget,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = LOGOUT_LABEL,
                )
            }
        },
        colors = colors,
    )

    if (logout.isVisible) {
        AlertDialog(
            onDismissRequest = { logout.dismiss() },
            title = { Text(LOGOUT_DIALOG_TITLE) },
            text = { Text(LOGOUT_DIALOG_BODY) },
            confirmButton = {
                TextButton(onClick = { logout.confirm(onLogout) }) {
                    Text(LOGOUT_LABEL)
                }
            },
            dismissButton = {
                TextButton(onClick = { logout.dismiss() }) {
                    Text(LOGOUT_DIALOG_CANCEL)
                }
            },
        )
    }
}
