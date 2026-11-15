package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import com.example.ads.AdManager
import com.example.viewmodel.SessionViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.example.viewmodel.Screen

@Composable
fun LearningSessionScreen(
    viewModel: SessionViewModel,
    isPractice: Boolean,
    isDifficult: Boolean = false,
    activity: ComponentActivity,
    onFinishSession: () -> Unit = viewModel::finishSession
) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val showExitDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
    val semanticColors = AppTheme.colors

    // Resolve AdManager from the host activity
    val adManager = remember(activity) { (activity as? com.example.MainActivity)?.adManager }

    // Ad gate: true = ad has been shown/skipped, ready to show results
    val adGatePassed = rememberSaveable(session?.id) { androidx.compose.runtime.mutableStateOf(false) }

    BackHandler(enabled = session != null && session?.isFinished != true) {
        showExitDialog.value = true
    }

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Warning, contentDescription = "Увага", tint = semanticColors.warning, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Немає слів для повторення!",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Спочатку пройдіть «Вивчення» на головному екрані, щоб почати повторення.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.navigateTo(Screen.Dashboard) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Повернутись на головну")
            }
        }
    } else {
        val currentSession = session!!

        if (currentSession.isFinished) {
            if (isPremium || adGatePassed.value) {
                // Premium user or ad already shown — show results immediately
                SessionFinishedScreen(
                    correct = currentSession.correctCount,
                    total = currentSession.words.size,
                    reduceMotion = reduceMotion,
                    onFinish = onFinishSession
                )
            } else {
                // Free user — show interstitial then results
                // Use a Box placeholder while the ad is loading/showing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {}
                LaunchedEffect(Unit) {
                    if (adManager != null) {
                        adManager.showIfLoaded(activity) {
                            adGatePassed.value = true
                        }
                    } else {
                        adGatePassed.value = true
                    }
                }
            }
        } else {
            val currentWord = currentSession.currentWord!!
            val totalQuestions = currentSession.words.size
            val currentIndex = currentSession.currentIndex
            val feedbackModifier = rememberSessionFeedback(currentSession, reduceMotion, soundEnabled, hapticsEnabled)

            SessionContentLayout(
                header = {
                    // Top header with screen exit & progress
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showExitDialog.value = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Завершити сесію", tint = MaterialTheme.colorScheme.onBackground)
                            }

                            Row(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(
                                        id = if (isPractice) R.drawable.emoji_target else R.drawable.emoji_brain
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp).size(22.dp)
                                )
                                Text(
                                    text = when {
                                        isDifficult -> "Складні слова"
                                        isPractice -> "Вивчення"
                                        else -> "Повторення"
                                    },
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Text(
                                text = "${currentIndex + 1} / $totalQuestions",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal progressive bar indicator
                        val progressValue = (currentIndex.toFloat()) / totalQuestions
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )

                        if (!isPractice && currentSession.isEarlyReview) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(semanticColors.warningContainer)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Раннє повторення: правильні відповіді не змінять розклад",
                                    color = semanticColors.onWarningContainer,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                },
                question = {
                    // Core Giant display Word and choices — with bounce/shake modifier
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(feedbackModifier),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SessionQuestionCard(currentWord)

                        SessionAnswerChoices(currentWord, currentSession, reduceMotion, busy, viewModel::submitAnswer)
                    }

                },
                footer = {
                    // Bottom feedback box & next button panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        if (currentSession.selectedAnswer != null) {
                            val isCorrect = currentSession.isCorrectAnswer == true

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCorrect) semanticColors.successContainer else MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isCorrect) semanticColors.success else MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = "Результат відповіді",
                                        tint = if (isCorrect) semanticColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isCorrect) "Правильно!" else "Помилка!",
                                            color = if (isCorrect) semanticColors.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (!isCorrect) {
                                            Text(
                                                text = "Нормативний наголос: ${currentWord.stressed}",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.nextQuestion() },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("session_next_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Далі", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Наступне слово", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        } else {
                            // Keep spacing stable when no feedback is shown
                            Spacer(modifier = Modifier.height(130.dp))
                        }
                    }
                }
            )
        }
    }

    if (showExitDialog.value) {
        AlertDialog(
            onDismissRequest = { showExitDialog.value = false },
            title = { Text("Що зробити із тренуванням?") },
            text = { Text("Можна зберегти поточне місце й продовжити пізніше або завершити сесію.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog.value = false
                        viewModel.pauseSession()
                    }
                ) { Text("Продовжити пізніше") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showExitDialog.value = false }) {
                        Text("Залишитися")
                    }
                    OutlinedButton(
                        onClick = {
                            showExitDialog.value = false
                            viewModel.discardSession()
                        }
                    ) { Text("Завершити") }
                }
            }
        )
    }
}
