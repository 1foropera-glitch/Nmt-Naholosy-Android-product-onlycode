package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PendingResetCard(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("pending_reset_card")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Скидання очікує підтвердження", style = MaterialTheme.typography.titleMedium)
            Text("Під’єднайтеся до інтернету, щоб завершити скидання на всіх пристроях. До підтвердження нові відповіді не зберігаються, а навчання призупинено. Попередній прогрес поки залишається видимим.")
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Повторити синхронізацію") }
        }
    }
}
