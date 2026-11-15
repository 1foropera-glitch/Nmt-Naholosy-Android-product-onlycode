package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Word
import com.example.ui.screens.SessionAnswerChoices
import com.example.ui.theme.MyApplicationTheme
import com.example.util.StressUtils
import com.example.viewmodel.ActiveSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionChoicesDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyAnswerIsReachableAndClickableInSmallWindowWithLargeText() {
        val word = Word(stressed = "електромерЕжа", plain = "електромережа", normalized = "електромережа",
            stressPositions = "9", letter = "Е", note = null, source = "test")
        var selected: String? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 200.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            SessionAnswerChoices(word, ActiveSession("practice", listOf(word)), true, false) { selected = it }
                        }
                    }
                }
            }
        }
        StressUtils.getAnswerVariants(word.plain, word.stressed).forEach { choice ->
            compose.onNodeWithTag("choice_$choice").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(choice, selected) }
        }
    }
}
