package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App-specific semantic colors not covered by Material's core roles. */
@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val destructive: Color,
    val onDestructive: Color,
    val destructiveContainer: Color,
    val onDestructiveContainer: Color
)

internal val LightSemanticColors = SemanticColors(
    success = Color(0xFF047857),
    onSuccess = Color.White,
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF064E3B),
    warning = AppLightColorScheme.tertiary,
    onWarning = AppLightColorScheme.onTertiary,
    warningContainer = AppLightColorScheme.tertiaryContainer,
    onWarningContainer = AppLightColorScheme.onTertiaryContainer,
    destructive = Color(0xFFB91C1C),
    onDestructive = Color.White,
    destructiveContainer = Color(0xFFFEE2E2),
    onDestructiveContainer = Color(0xFF7F1D1D)
)

internal val DarkSemanticColors = SemanticColors(
    success = Color(0xFF34D399),
    onSuccess = Color(0xFF0F172A),
    successContainer = Color(0xFF064E3B),
    onSuccessContainer = Color(0xFFD1FAE5),
    warning = AppDarkColorScheme.tertiary,
    onWarning = AppDarkColorScheme.onTertiary,
    warningContainer = AppDarkColorScheme.tertiaryContainer,
    onWarningContainer = AppDarkColorScheme.onTertiaryContainer,
    destructive = Color(0xFFF87171),
    onDestructive = Color(0xFF0F172A),
    destructiveContainer = Color(0xFF7F1D1D),
    onDestructiveContainer = Color(0xFFFEE2E2)
)

internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object AppTheme {
    val colors: SemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColors.current
}
