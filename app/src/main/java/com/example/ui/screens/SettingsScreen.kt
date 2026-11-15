package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.R
import com.example.data.ThemeMode
import com.example.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, activity: ComponentActivity) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val reviewRemindersEnabled by viewModel.reviewRemindersEnabled.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingReset by viewModel.pendingReset.collectAsStateWithLifecycle()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val diagnosticsEnabled by viewModel.diagnosticsEnabled.collectAsStateWithLifecycle()
    val consent by viewModel.adConsentState.collectAsStateWithLifecycle()
    val deletionState by viewModel.accountDeletionState.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingAccountDeletion.collectAsStateWithLifecycle()
    val mainActivity = activity as? MainActivity
    val canPostReviewReminders = mainActivity?.canPostReviewReminders == true
    val reviewRemindersBlocked = reviewRemindersEnabled && !canPostReviewReminders

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.emoji_gear),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(28.dp)
                )
                Text(
                    text = "Налаштування",
            color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            AccountCard(
                authState = authState,
                syncState = syncState,
                onSignIn = { (activity as? MainActivity)?.startGoogleSignIn() },
                onSignOut = { (activity as? MainActivity)?.signOutFromGoogle() },
                onSync = viewModel::syncProgressNow
            )
        }

        if (pendingReset) {
            item { PendingResetCard(onRetry = viewModel::syncProgressNow) }
        }
        item { AccountDeletionCard(authState, pendingDeletion != null, deletionState,
            onConfirm = { mainActivity?.confirmAccountDeletion() }, onCheck = viewModel::checkAccountDeletion) }
        if (pendingCount > 0) {
            item { Text("Очікують відправлення: $pendingCount слів", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // ── Premium Card ──────────────────────────────────────────────
        item {
            PremiumCard(
                isPremium = isPremium,
                state = billingState,
                onBuyClick = {
                    val billingManager = (activity as? com.example.MainActivity)?.billingManager
                    billingManager?.launchPurchase(activity)
                },
                onRestoreClick = viewModel::restorePurchases
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Приватність і підтримка", style = MaterialTheme.typography.titleMedium)
                    SettingsSwitchRow(
                        title = "Допомагати покращувати застосунок",
                        description = "Дозволити надсилання подій використання та звітів про збої. Навчання працює і без цього.",
                        checked = diagnosticsEnabled,
                        onCheckedChange = viewModel::setDiagnosticsEnabled,
                        testTag = "diagnostics_switch"
                    )
                    OutlinedButton(onClick = {
                        viewModel.message.value = com.example.privacy.openPublicPage(activity, com.example.BuildConfig.PRIVACY_POLICY_URL)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Політика конфіденційності") }
                    if (consent.privacyOptionsRequired) {
                        OutlinedButton(onClick = { mainActivity?.showAdPrivacyOptions() }, enabled = !consent.busy,
                            modifier = Modifier.fillMaxWidth()) { Text("Налаштування рекламної приватності") }
                    }
                    consent.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { mainActivity?.retryAdConsent() }, enabled = !consent.busy,
                            modifier = Modifier.fillMaxWidth()) { Text("Перевірити рекламні налаштування") }
                    }
                    OutlinedButton(onClick = {
                        viewModel.message.value = com.example.privacy.contactSupport(activity, com.example.BuildConfig.SUPPORT_EMAIL)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Зв’язатися з підтримкою") }
                    OutlinedButton(onClick = {
                        viewModel.message.value = com.example.privacy.openPublicPage(activity, com.example.BuildConfig.ACCOUNT_DELETION_URL)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Запросити видалення акаунта й даних") }
                    Text("Відкриється сторінка запиту видалення. Вихід з акаунта або скидання прогресу не видаляють акаунт.",
                        style = MaterialTheme.typography.bodySmall)
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Вигляд",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Оберіть тему, у якій зручніше займатися.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ThemeModeSelector(
                        selectedMode = themeMode,
                        onSelected = viewModel::setThemeMode
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Комфорт",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Застосунок використовує системний розмір тексту. Збільшити його можна в налаштуваннях Android.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSwitchRow(
                        title = "Звуки відповідей",
                        description = "Сигнали правильної та неправильної відповіді.",
                        checked = soundEnabled,
                        onCheckedChange = viewModel::setSoundEnabled,
                        testTag = "sound_feedback_switch"
                    )
                    SettingsSwitchRow(
                        title = "Вібрація",
                        description = "Тактильний відгук після вибору відповіді.",
                        checked = hapticsEnabled,
                        onCheckedChange = viewModel::setHapticsEnabled,
                        testTag = "haptics_feedback_switch"
                    )
                    SettingsSwitchRow(
                        title = "Зменшити анімації",
                        description = "Переходи й реакції відображатимуться без зайвого руху.",
                        checked = reduceMotion,
                        onCheckedChange = viewModel::setReduceMotion,
                        testTag = "reduce_motion_switch"
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Нагадування",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitchRow(
                        title = "Повторення слів",
                        description = "Повідомляти, коли слова будуть готові до повторення.",
                        checked = reviewRemindersEnabled && canPostReviewReminders,
                        onCheckedChange = { enabled ->
                            mainActivity?.setReviewRemindersEnabled(enabled)
                        },
                        testTag = "review_reminders_switch"
                    )
                    SettingsSwitchRow(
                        title = "Тихі години: 00:00–07:00",
                        description = "Не надсилати нагадування вночі за місцевим часом пристрою.",
                        checked = quietHoursEnabled,
                        onCheckedChange = viewModel::setQuietHoursEnabled,
                        testTag = "quiet_hours_switch"
                    )
                    if (reviewRemindersBlocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.review_reminders_blocked),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { mainActivity?.openReviewReminderSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.review_reminders_open_settings))
                        }
                    }
                    if (reviewRemindersEnabled && canPostReviewReminders) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.review_reminders_background_help),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { mainActivity?.openReviewReminderBackgroundSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.review_reminders_open_background_settings))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun ThemeModeSelector(
    selectedMode: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {
    val modes = listOf(
        ThemeMode.SYSTEM to "Система",
        ThemeMode.LIGHT to "Світла",
        ThemeMode.DARK to "Темна"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = mode == selectedMode
            if (isSelected) {
                Button(
                    onClick = { onSelected(mode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("theme_${mode.name.lowercase()}")
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(mode) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("theme_${mode.name.lowercase()}")
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

