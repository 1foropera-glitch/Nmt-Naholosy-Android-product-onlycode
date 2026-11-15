package com.example.ui.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.data.Word
import com.example.ui.screens.SessionAnswerChoices
import com.example.ui.theme.MyApplicationTheme
import com.example.util.StressUtils
import com.example.viewmodel.ActiveSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h240dp-land", application = android.app.Application::class)
class SessionChoicesLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allChoicesRemainReachableWithLargeTextInSmallLandscapeWindow() {
        val word = Word(stressed = "електромерЕжа", plain = "електромережа", normalized = "електромережа",
            stressPositions = "9", letter = "Е", note = null, source = "test")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SessionAnswerChoices(word, ActiveSession("practice", listOf(word)), true, false) {}
                    }
                }
            }
        }
        StressUtils.getAnswerVariants(word.plain, word.stressed).forEach { choice ->
            compose.onNodeWithTag("choice_$choice").performScrollTo().assertIsDisplayed()
        }
    }
}
