package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import com.example.util.StressUtils

import com.example.data.Word
import com.example.viewmodel.ActiveSession

@Composable
fun SessionAnswerChoices(word: Word, session: ActiveSession, reduceMotion: Boolean, busy: Boolean, onAnswer: (String) -> Unit) {
    val semanticColors = AppTheme.colors
    // Vowel stress buttons (Choices)
    val choices = remember(word) {
        StressUtils.getAnswerVariants(word.plain, word.stressed)
    }

    Column(
        modifier = Modifier.fillMaxWidth(0.88f),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        choices.forEach { choice ->
            val isSelected = session.selectedAnswer == choice
            val isCorrectAnswer = choice == word.stressed

            // Determine buttons color states based on correctness feedback
            val targetContainerColor = when {
                session.selectedAnswer == null -> MaterialTheme.colorScheme.surface
                isCorrectAnswer -> semanticColors.success
                isSelected -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }

            val targetContentColor = when {
                session.selectedAnswer == null -> MaterialTheme.colorScheme.onSurface
                isCorrectAnswer -> semanticColors.onSuccess
                isSelected -> MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }

            // Animated colors for smooth feedback transition
            val animatedContainerColor by animateColorAsState(
                targetValue = targetContainerColor,
                animationSpec = tween(durationMillis = 250),
                label = "choice_container_color"
            )

            val animatedContentColor by animateColorAsState(
                targetValue = targetContentColor,
                animationSpec = tween(durationMillis = 250),
                label = "choice_content_color"
            )

            // Button scale animation based on selection/correctness
            val targetScale = when {
                session.selectedAnswer == null -> 1f
                isSelected -> if (isCorrectAnswer) 1.05f else 0.95f
                isCorrectAnswer -> 1.02f
                else -> 0.92f
            }

            val buttonScale by animateFloatAsState(
                targetValue = if (reduceMotion) 1f else targetScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "choice_button_scale"
            )

            // Primary border by default; semantic success/error after answering.
            val targetBorderColor = when {
                session.selectedAnswer == null -> MaterialTheme.colorScheme.primary
                isCorrectAnswer -> semanticColors.success
                isSelected -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            val animatedBorderColor by animateColorAsState(
                targetValue = targetBorderColor,
                animationSpec = tween(durationMillis = 250),
                label = "choice_border_color"
            )

            Button(
                onClick = { onAnswer(choice) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = animatedContainerColor,
                    contentColor = animatedContentColor
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, animatedBorderColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .semantics { contentDescription = StressUtils.accessibleStress(word.plain, choice) }
                    .testTag("choice_$choice")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (session.selectedAnswer != null) {
                        if (isCorrectAnswer) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Правильно",
                                tint = semanticColors.onSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Неправильно",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    Text(
                        text = choice,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
