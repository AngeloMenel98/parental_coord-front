package com.parental.shared.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : BottomNavItem("home", "Inicio", Icons.Default.Home)
    data object Actividades : BottomNavItem("activities", "Actividades", Icons.Default.Event)
    data object Gastos : BottomNavItem("expenses", "Gastos", Icons.Default.AccountBalanceWallet)
    data object Terceros : BottomNavItem("third_parties", "Terceros", Icons.Default.People)
}

@Composable
fun BottomNavBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Actividades,
        BottomNavItem.Gastos,
        BottomNavItem.Terceros,
    )

    // El ítem seleccionado se apoya sobre `primaryContainer` (la píldora). En claro el
    // texto va en `primary` (8.2:1 sobre #9CF1F6); en oscuro `primary` sobre la píldora
    // violeta da 4.39:1 y no alcanza AA para 12sp, así que ahí manda `onPrimaryContainer`
    // (8.6:1). Mismo patrón que StatusTag: el componente resuelve el modo.
    val selectedContent: Color = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedContent,
                    selectedTextColor = selectedContent,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
