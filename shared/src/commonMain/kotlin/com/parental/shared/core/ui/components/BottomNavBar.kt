package com.parental.shared.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            )
        }
    }
}
