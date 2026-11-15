package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auth.AuthUiState
import com.example.privacy.AccountDeletionState

@Composable
fun AccountDeletionCard(auth: AuthUiState, pending: Boolean, state: AccountDeletionState,
                        onConfirm: () -> Unit, onCheck: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    if (auth is AuthUiState.SignedIn || pending || state.message != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Видалення акаунта", style = MaterialTheme.typography.titleMedium)
                Text("Видалить акаунт застосунку та навчальний прогрес. Залиште застосунок відкритим до завершення; після переривання відкрийте його знову. Ваш Google-акаунт і сама покупка в Google Play залишаються.")
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (pending) OutlinedButton(onClick = onCheck, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Видаляємо…" else "Продовжити видалення")
                }
                if (auth is AuthUiState.SignedIn) OutlinedButton(onClick = { confirm = true },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (pending) "Повторно підтвердити видалення" else "Видалити акаунт і дані")
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Видалити акаунт і прогрес?") },
        text = { Text("Ваш акаунт, навчальний прогрес і сесії буде видалено без можливості відновлення. Premium буде вимкнено на цьому пристрої. Залиште застосунок відкритим до завершення видалення. Тимчасовий технічний запис захищатиме від повторного запису зі старих пристроїв. Чинну покупку можна буде відновити через той самий акаунт Google Play. Видалення не повертає кошти за покупку. Для підтвердження видалення повторно виберіть Google-акаунт, з яким увійшли в застосунок.") },
        confirmButton = { TextButton(onClick = { confirm = false; onConfirm() }) { Text("Підтвердити через Google") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Скасувати") } })
}
