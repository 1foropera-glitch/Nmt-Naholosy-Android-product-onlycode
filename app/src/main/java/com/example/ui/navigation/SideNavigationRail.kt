package com.example.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.Screen

@Composable
fun SideNavigationRail(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxHeight().testTag("side_nav_rail")
    ) {
        val navItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        NavigationRailItem(
            selected = currentScreen == Screen.Dashboard,
            onClick = { onNavigate(Screen.Dashboard) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Головна") },
            label = { Text("Головна", fontSize = 11.sp) },
            colors = navItemColors
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Налаштування") },
            label = { Text("Налаштування", fontSize = 11.sp) },
            colors = navItemColors
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = currentScreen == Screen.Dictionary,
            onClick = { onNavigate(Screen.Dictionary) },
            icon = { Icon(Icons.Default.Book, contentDescription = "Словник") },
            label = { Text("Словник", fontSize = 11.sp) },
            colors = navItemColors
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = currentScreen == Screen.ProgressStats || currentScreen == Screen.DifficultWords,
            onClick = { onNavigate(Screen.ProgressStats) },
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Прогрес") },
            label = { Text("Прогрес", fontSize = 11.sp) },
            colors = navItemColors
        )
    }
}
