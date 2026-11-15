package com.example.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.Word
import com.example.ui.screens.DictionaryContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DictionaryLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wordCanBeFullyShownWhenHeaderExceedsWindowHeight() {
        val word = Word(id = 1, stressed = "асиметрІя", plain = "асиметрія", normalized = "асиметрія",
            stressPositions = "7", letter = "А", note = null, source = "test")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(320.dp, 200.dp)) {
                        DictionaryContent("", null, listOf(word), emptyList(), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("dictionary_grid").performScrollToNode(hasTestTag("dictionary_word_1"))
        val node = compose.onNodeWithTag("dictionary_word_1").assertIsDisplayed().fetchSemanticsNode()
        assertEquals(node.layoutInfo.coordinates.size.height.toFloat(), node.boundsInRoot.height, 1f)
    }
}
