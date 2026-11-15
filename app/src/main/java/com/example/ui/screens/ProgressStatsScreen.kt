package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ReviewStages
import com.example.data.WordState
import com.example.ui.components.ProgressCircle
import com.example.ui.layout.AppWindowWidthSizeClass
import com.example.ui.layout.adaptiveGridColumnCount
import com.example.ui.theme.AppTheme
import com.example.util.formatReviewCountdown
import com.example.util.stagePassedCount
import com.example.util.timeProgress
import com.example.util.wordsLabel
import com.example.util.wordsNeedAttentionLabel
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.delay

private data class ProgressSlide(val title: String, val count: Int, val isOverall: Boolean)

@Composable
fun ProgressStatsScreen(
    viewModel: DashboardViewModel,
    widthSizeClass: AppWindowWidthSizeClass = AppWindowWidthSizeClass.Compact
) {
    val allStates by viewModel.allWordStates.collectAsStateWithLifecycle()
    val totalWords by viewModel.totalWordsCount.collectAsStateWithLifecycle()
    val difficultWords by viewModel.difficultWords.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    var selectedSlide by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    val learnedCount = allStates.count { it.status == "learned" }
    val slides = listOf(ProgressSlide("Загальне засвоєння", learnedCount, true)) +
        ReviewStages.map { stage ->
            ProgressSlide(stage.title, stagePassedCount(allStates, stage.box), false)
        }
    val slide = slides[selectedSlide]
    val slideDetailText = if (slide.isOverall) {
        "Вивчено: ${slide.count} із $totalWords слів"
    } else {
        "Пройдено через етап: ${slide.count} із $totalWords слів"
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(adaptiveGridColumnCount(widthSizeClass)),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.emoji_chart),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Прогрес запам'ятовування",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(slide.title, style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { selectedSlide-- },
                            enabled = selectedSlide > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Попередня діаграма"
                            )
                        }
                        ProgressCircle(
                            modifier = Modifier.weight(1f),
                            count = slide.count,
                            totalCount = totalWords,
                            reduceMotion = reduceMotion
                        )
                        IconButton(
                            onClick = { selectedSlide++ },
                            enabled = selectedSlide < slides.lastIndex,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Наступна діаграма"
                            )
                        }
                    }
                    Text(
                        text = slideDetailText,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${selectedSlide + 1} із ${slides.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = AppTheme.colors.warning,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Складні слова", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (difficultWords.isEmpty()) "Поки немає складних слів"
                            else wordsNeedAttentionLabel(difficultWords.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(onClick = { viewModel.navigateTo(Screen.DifficultWords) }) {
                        Text("Переглянути")
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Етапи повторення", style = MaterialTheme.typography.titleMedium)
        }

        ReviewStages.forEach { stage ->
            item(key = "stage_${stage.box}") {
                val states = allStates.filter { it.status == "learning" && it.box == stage.box }
                StageCard(stage.title, states, now)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.destructive),
                modifier = Modifier.fillMaxWidth().testTag("clear_progress_button")
            ) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Скинути весь прогрес навчання")
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Скинути прогрес?") },
            text = { Text("Цю дію неможливо буде скасувати. Всі вивчені слова повернуться у початковий стан.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllProgress()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.destructive,
                        contentColor = AppTheme.colors.onDestructive
                    )
                ) { Text("Скинути") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) { Text("Скасувати") }
            }
        )
    }
}

@Composable
private fun StageCard(title: String, states: List<WordState>, now: Long) {
    val nearest = states.filter { it.dueAt != null }.minByOrNull { it.dueAt ?: Long.MAX_VALUE }
    val isDue = nearest?.dueAt?.let { it <= now } == true
    val countdown = if (states.isEmpty()) {
        "На цьому етапі поки немає слів"
    } else {
        formatReviewCountdown(nearest?.dueAt, now)
    }
    val progress = timeProgress(nearest, now)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    wordsLabel(states.size),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                countdown,
                color = if (isDue) AppTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics { contentDescription = "$title: $countdown" },
                color = if (isDue) AppTheme.colors.warning else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
