package com.example.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWindowWidthSizeClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidth(widthDp: Int): AppWindowWidthSizeClass = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            else -> Expanded
        }
    }
}

fun adaptiveGridColumnCount(widthSizeClass: AppWindowWidthSizeClass): Int =
    if (widthSizeClass == AppWindowWidthSizeClass.Expanded) 2 else 1

fun usesNavigationRail(widthSizeClass: AppWindowWidthSizeClass): Boolean =
    widthSizeClass != AppWindowWidthSizeClass.Compact

fun adaptiveHorizontalPadding(
    widthSizeClass: AppWindowWidthSizeClass,
    compactHorizontalPadding: Dp = 16.dp
): Dp = when (widthSizeClass) {
    AppWindowWidthSizeClass.Compact -> compactHorizontalPadding
    AppWindowWidthSizeClass.Medium -> 24.dp
    AppWindowWidthSizeClass.Expanded -> 32.dp
}

object AdaptiveContentWidth {
    val Focused = 760.dp
    val Form = 960.dp
    val Wide = 1200.dp
}

@Composable
fun rememberAppWindowWidthSizeClass(): AppWindowWidthSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { AppWindowWidthSizeClass.fromWidth(widthDp) }
}

@Composable
fun AdaptiveScreenContainer(
    widthSizeClass: AppWindowWidthSizeClass,
    maxContentWidth: Dp,
    modifier: Modifier = Modifier,
    compactHorizontalPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val horizontalPadding = adaptiveHorizontalPadding(
        widthSizeClass = widthSizeClass,
        compactHorizontalPadding = compactHorizontalPadding
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            content = content
        )
    }
}
