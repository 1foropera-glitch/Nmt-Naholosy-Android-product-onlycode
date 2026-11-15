package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.viewmodel.Screen

@Composable
fun BottomNavigationBar(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("bottom_nav_bar")
        ) {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NavigationBarItem(
                selected = currentScreen == Screen.Dashboard,
                onClick = { onNavigate(Screen.Dashboard) },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Головна") },
                label = { Text("Головна") },
                colors = navItemColors
            )
            NavigationBarItem(
                selected = currentScreen == Screen.Settings,
                onClick = { onNavigate(Screen.Settings) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Налаштування") },
                label = { Text("Налашт.") },
                colors = navItemColors
            )
            NavigationBarItem(
                selected = currentScreen == Screen.Dictionary,
                onClick = { onNavigate(Screen.Dictionary) },
                icon = { Icon(Icons.Default.Book, contentDescription = "Словник") },
                label = { Text("Словник") },
                colors = navItemColors
            )
            NavigationBarItem(
                selected = currentScreen == Screen.ProgressStats || currentScreen == Screen.DifficultWords,
                onClick = { onNavigate(Screen.ProgressStats) },
                icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Прогрес") },
                label = { Text("Прогрес") },
                colors = navItemColors
            )
        }
    }
}
