package com.example.ui.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `width boundaries map to expected size classes`() {
        assertEquals(
            AppWindowWidthSizeClass.Compact,
            AppWindowWidthSizeClass.fromWidth(599)
        )
        assertEquals(
            AppWindowWidthSizeClass.Medium,
            AppWindowWidthSizeClass.fromWidth(600)
        )
        assertEquals(
            AppWindowWidthSizeClass.Medium,
            AppWindowWidthSizeClass.fromWidth(839)
        )
        assertEquals(
            AppWindowWidthSizeClass.Expanded,
            AppWindowWidthSizeClass.fromWidth(840)
        )
    }

    @Test
    fun `only expanded windows use two grid columns`() {
        assertEquals(1, adaptiveGridColumnCount(AppWindowWidthSizeClass.Compact))
        assertEquals(1, adaptiveGridColumnCount(AppWindowWidthSizeClass.Medium))
        assertEquals(2, adaptiveGridColumnCount(AppWindowWidthSizeClass.Expanded))
    }

    @Test
    fun `medium and expanded windows use navigation rail`() {
        assertFalse(usesNavigationRail(AppWindowWidthSizeClass.Compact))
        assertTrue(usesNavigationRail(AppWindowWidthSizeClass.Medium))
        assertTrue(usesNavigationRail(AppWindowWidthSizeClass.Expanded))
    }

    @Test
    fun `screen gutters grow with available width`() {
        assertEquals(16.dp, adaptiveHorizontalPadding(AppWindowWidthSizeClass.Compact))
        assertEquals(24.dp, adaptiveHorizontalPadding(AppWindowWidthSizeClass.Medium))
        assertEquals(32.dp, adaptiveHorizontalPadding(AppWindowWidthSizeClass.Expanded))
    }
}
