package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.billing.BillingUiState

@Composable
internal fun PremiumCard(isPremium: Boolean, state: BillingUiState, onBuyClick: () -> Unit, onRestoreClick: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isPremium) "Преміум активовано" else "Преміум", style = MaterialTheme.typography.titleMedium)
            Text(if (isPremium) "Реклама вимкнена. Дякуємо за підтримку!" else "Прибрати рекламу назавжди.")
            if (state.pending) Text("Платіж очікує завершення в Google Play.")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it) }
            if (!isPremium) Button(onClick = onBuyClick,
                enabled = state.ready && !state.busy && !state.pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("buy_premium_button")) {
                Text(when {
                    state.busy -> "Перевіряємо покупку…"
                    state.price != null -> "Прибрати рекламу — ${state.price}"
                    else -> "Покупка недоступна"
                })
            }
            OutlinedButton(onClick = onRestoreClick, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("restore_premium_button")) {
                Text(if (state.busy) "Перевіряємо…" else "Відновити покупку")
            }
            Text("Для покупки та відновлення використовуйте той самий Google-акаунт.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
