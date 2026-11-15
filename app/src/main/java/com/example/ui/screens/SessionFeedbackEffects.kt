package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.util.SoundFeedbackManager

import com.example.viewmodel.ActiveSession

@Composable
internal fun rememberSessionFeedback(session: ActiveSession, reduceMotion: Boolean,
    soundEnabled: Boolean, hapticsEnabled: Boolean): Modifier {
    val haptic = LocalHapticFeedback.current
    val audioContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val soundManager = remember(audioContext) { SoundFeedbackManager(audioContext) }
    DisposableEffect(soundManager) { onDispose { soundManager.release() } }
    val scaleAnim = remember { Animatable(1f) }
    val shakeAnim = remember { Animatable(0f) }
    val currentIndex = session.currentIndex
    val lastFeedbackEvent = remember(session.mode, session.words) {
        androidx.compose.runtime.mutableStateOf(
            session.selectedAnswer?.let { "$currentIndex:$it" }
        )
    }

    // Trigger haptic, sound, and animation when answer is selected
    LaunchedEffect(currentIndex, session.selectedAnswer) {
        val answer = session.selectedAnswer ?: return@LaunchedEffect
        val feedbackEvent = "$currentIndex:$answer"
        if (lastFeedbackEvent.value == feedbackEvent) return@LaunchedEffect
        lastFeedbackEvent.value = feedbackEvent
        val isCorrect = session.isCorrectAnswer == true

        // Haptic feedback
        if (hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        // Sound feedback
        if (soundEnabled) {
            if (isCorrect) {
                soundManager.playCorrect()
            } else {
                soundManager.playWrong()
            }
        }

        // Animations (respect reduceMotion)
        if (!reduceMotion) {
            if (isCorrect) {
                // Bounce: scale up then back
                scaleAnim.snapTo(1f)
                scaleAnim.animateTo(
                    targetValue = 1.08f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                )
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } else {
                // Shake: horizontal oscillation
                shakeAnim.snapTo(0f)
                shakeAnim.animateTo(
                    targetValue = 12f,
                    animationSpec = tween(durationMillis = 50)
                )
                shakeAnim.animateTo(
                    targetValue = -12f,
                    animationSpec = tween(durationMillis = 50)
                )
                shakeAnim.animateTo(
                    targetValue = 8f,
                    animationSpec = tween(durationMillis = 50)
                )
                shakeAnim.animateTo(
                    targetValue = -8f,
                    animationSpec = tween(durationMillis = 50)
                )
                shakeAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 50)
                )
            }
        }
    }

    // Reset animations when moving to next question
    LaunchedEffect(currentIndex) {
        scaleAnim.snapTo(1f)
        shakeAnim.snapTo(0f)
    }

    return Modifier.graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        translationX = shakeAnim.value
    }
}
