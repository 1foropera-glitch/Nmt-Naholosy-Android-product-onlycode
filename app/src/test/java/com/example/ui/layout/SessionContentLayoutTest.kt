package com.example.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.Word
import com.example.ui.screens.SessionAnswerChoices
import com.example.ui.screens.SessionContentLayout
import com.example.ui.screens.SessionQuestionCard
import com.example.ui.theme.MyApplicationTheme
import com.example.util.StressUtils
import com.example.viewmodel.ActiveSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h900dp", application = android.app.Application::class)
class SessionContentLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val word = Word(stressed = "середИна", plain = "середина", normalized = "середина",
        stressPositions = "5", letter = "С", note = null, source = "test")

    private fun render(height: Int, fontScale: Float = 1f, onNext: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MyApplicationTheme {
                    Box(Modifier.requiredSize(400.dp, height.dp)) {
                        SessionContentLayout(
                            header = { Box(Modifier.fillMaxWidth().height(62.dp).testTag("header")) },
                            question = {
                                Column(Modifier.testTag("question")) {
                                    SessionQuestionCard(word)
                                    SessionAnswerChoices(word, ActiveSession("practice", listOf(word)), true, false) {}
                                }
                            },
                            footer = { Button(onNext, Modifier.testTag("footer")) { Text("Далі") } }
                        )
                    }
                }
            }
        }
    }

    @Test fun questionIsCenteredBetweenHeaderAndFooterWhenThereIsRoom() {
        render(height = 850)
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val question = compose.onNodeWithTag("question").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot
        assertEquals((header.bottom + footer.top) / 2, question.center.y, 1f)
        assertTrue("Question must use the free space instead of sticking to the header", question.top - header.bottom > 24f)
        compose.onNodeWithTag("footer").assertIsDisplayed()
    }

    @Test fun allChoicesAndNextRemainReachableInShortWindowWithLargeFont() {
        var nextClicks = 0
        render(height = 240, fontScale = 2f, onNext = { nextClicks++ })
        StressUtils.getAnswerVariants(word.plain, word.stressed).forEach { choice ->
            val node = compose.onNodeWithTag("choice_$choice").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertEquals(node.layoutInfo.coordinates.size.height.toFloat(), node.boundsInRoot.height, 1f)
        }
        compose.onNodeWithTag("footer").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, nextClicks)
    }
}
