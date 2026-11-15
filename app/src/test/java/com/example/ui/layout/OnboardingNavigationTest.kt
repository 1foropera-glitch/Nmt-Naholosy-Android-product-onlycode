package com.example.ui.layout

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h850dp", application = android.app.Application::class)
class OnboardingNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nextMovesThroughAllPagesAndFinishWorks() {
        var finished = 0
        compose.setContent { MyApplicationTheme { OnboardingScreen(onFinish = { finished++ }) } }
        compose.onNodeWithText("Далі →").performClick()
        compose.onNodeWithText("Інтервальне повторення").assertIsDisplayed()
        compose.onNodeWithText("Далі →").performClick()
        compose.onNodeWithText("Починай сьогодні!").assertIsDisplayed()
        assertEquals(0, finished)
        compose.onNodeWithText("Почати тренування!").performClick()
        assertEquals(1, finished)
    }

    @Test fun skipFinishesWithoutSwiping() {
        var finished = 0
        compose.setContent { MyApplicationTheme { OnboardingScreen(onFinish = { finished++ }) } }
        compose.onNodeWithText("Пропустити").performClick()
        assertEquals(1, finished)
    }
}
