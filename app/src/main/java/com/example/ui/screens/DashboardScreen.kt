package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.BrandingHeroBanner
import com.example.ui.components.QuickAccessItem
import com.example.ui.layout.AppWindowWidthSizeClass
import com.example.ui.layout.adaptiveGridColumnCount
import com.example.ui.theme.AppTheme
import com.example.util.wordsLabel
import com.example.viewmodel.ActiveSession
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.Screen

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    widthSizeClass: AppWindowWidthSizeClass = AppWindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier,
    onEnableNotifications: () -> Unit = {},
    onDismissNotifications: () -> Unit = {},
    canPostNotifications: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onResumeSession: () -> Unit = viewModel::resumeSession,
    onStartReview: () -> Unit = viewModel::startReview
) {
    val dueReviewsCount by viewModel.dueReviewsCount.collectAsStateWithLifecycle()
    val stats by viewModel.srsStats.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val difficultWords by viewModel.difficultWords.collectAsStateWithLifecycle()
    val reminderPrompt by viewModel.reminderPrompt.collectAsStateWithLifecycle()
    val pendingReset by viewModel.pendingReset.collectAsStateWithLifecycle()
    val hasLearningWords = (1..7).sumOf { stats["box_$it"] ?: 0 } > 0

    LazyVerticalGrid(
        columns = GridCells.Fixed(adaptiveGridColumnCount(widthSizeClass)),
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { BrandingHeroBanner() }
        if (pendingReset) {
            item(span = { GridItemSpan(maxLineSpan) }) { PendingResetCard(onRetry = { viewModel.retryReset() }) }
        }

        if (reminderPrompt.shouldShow(canPostNotifications)) {
            item {
                NotificationPrimerCard(
                    blocked = reminderPrompt.needsPermissionRecovery,
                    onEnable = if (reminderPrompt.needsPermissionRecovery) onOpenNotificationSettings else onEnableNotifications,
                    onDismiss = onDismissNotifications
                )
            }
        }

        item {
            TodayPlanCard(
                activeSession = activeSession,
                dueCount = dueReviewsCount,
                hasLearningWords = hasLearningWords,
                onResume = onResumeSession,
                onStartReview = onStartReview,
                onStartPractice = { viewModel.navigateTo(Screen.PracticeSetup) }
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(
                    iconRes = R.drawable.emoji_graduation,
                    title = "Режими навчання"
                )
                LearningModeCard(
                    onClick = { viewModel.navigateTo(Screen.PracticeSetup) }
                )
            }
        }

        item(
            span = {
                if (widthSizeClass == AppWindowWidthSizeClass.Expanded) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(
                    iconRes = R.drawable.emoji_lightning,
                    title = "Швидкий доступ"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickAccessItem(
                        title = "Усі слова",
                        description = "Повний перелік НМТ",
                        icon = Icons.Default.MenuBook,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f).testTag("quick_dictionary_button"),
                        onClick = { viewModel.navigateTo(Screen.Dictionary) }
                    )
                    QuickAccessItem(
                        title = "Складні слова",
                        description = if (difficultWords.isEmpty()) "Поки немає" else wordsLabel(difficultWords.size),
                        icon = Icons.Default.Psychology,
                        color = AppTheme.colors.warning,
                        modifier = Modifier.weight(1f).testTag("quick_difficult_words_button"),
                        onClick = { viewModel.navigateTo(Screen.DifficultWords) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPlanCard(
    activeSession: ActiveSession?,
    dueCount: Int,
    hasLearningWords: Boolean,
    onResume: () -> Unit,
    onStartReview: () -> Unit,
    onStartPractice: () -> Unit
) {
    val title: String
    val description: String
    val buttonText: String
    val onClick: () -> Unit

    when {
        activeSession != null -> {
            title = "Незавершене тренування"
            val answered = (activeSession.currentIndex + if (activeSession.selectedAnswer != null) 1 else 0)
                .coerceAtMost(activeSession.words.size)
            description = "$answered із ${activeSession.words.size} слів"
            buttonText = "Продовжити"
            onClick = onResume
        }
        dueCount > 0 -> {
            title = "План на сьогодні"
            description = "До повторення готово: ${wordsLabel(dueCount)}"
            buttonText = "Повторити"
            onClick = onStartReview
        }
        hasLearningWords -> {
            title = "На сьогодні все виконано"
            description = "Планові слова повторено. Можна перейти до нових."
            buttonText = "Вивчати нові"
            onClick = onStartPractice
        }
        else -> {
            title = "Почни сьогодні"
            description = "Коротке тренування займе приблизно 4 хвилини."
            buttonText = "Почати"
            onClick = onStartPractice
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(
                            if (activeSession != null) R.drawable.emoji_target else R.drawable.emoji_brain
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dashboard_primary_action")
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LearningModeCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("learning_mode_practice")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Вивчення нових слів", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Обери коротку сесію та додай нові наголоси до повторення.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Почати вивчення")
        }
    }
}

@Composable
private fun NotificationPrimerCard(blocked: Boolean, onEnable: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(if (blocked) "Нагадування потребують дозволу" else "Дозволити нагадування?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (blocked) "Android блокує сповіщення. Дозвольте їх у налаштуваннях, щоб отримувати нагадування про повторення."
                else "Ми повідомимо лише тоді, коли слова будуть готові до повторення.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEnable) { Text(if (blocked) "Налаштувати" else "Увімкнути") }
                OutlinedButton(onClick = onDismiss) { Text("Не зараз") }
            }
        }
    }
}

@Composable
private fun SectionTitle(iconRes: Int, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
