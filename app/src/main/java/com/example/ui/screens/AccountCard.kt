package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AuthUiState
import com.example.sync.ProgressSyncState
import com.example.ui.theme.AppTheme

@Composable
internal fun AccountCard(
    authState: AuthUiState,
    syncState: ProgressSyncState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("account_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Синхронізація прогресу",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (authState) {
                AuthUiState.SignedOut -> SignedOutAccountContent(onSignIn = onSignIn)

                AuthUiState.SigningIn -> {
                    Text(
                        text = "Оберіть Google-акаунт для входу.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("google_sign_in_loading")
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Виконується вхід…")
                    }
                }

                is AuthUiState.SignedIn -> {
                    val account = authState.account
                    val accountName = account.displayName
                        ?.takeIf(String::isNotBlank)
                        ?: account.email?.substringBefore('@')
                        ?: "Google-акаунт"

                    Text(
                        text = accountName,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    account.email?.takeIf(String::isNotBlank)?.let { email ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = email,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SyncStatus(syncState)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSync,
                        enabled = syncState !is ProgressSyncState.Syncing &&
                            syncState !is ProgressSyncState.AccountMismatch,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("progress_sync_button")
                    ) {
                        Text(
                            when (syncState) {
                                is ProgressSyncState.Error -> "Спробувати синхронізацію ще раз"
                                ProgressSyncState.Syncing -> "Синхронізація…"
                                else -> "Синхронізувати зараз"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("google_sign_out_button")
                    ) {
                        Text("Вийти з акаунта")
                    }
                }

                is AuthUiState.Error -> {
                    Text(
                        text = authState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onSignIn,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("google_sign_in_retry_button")
                    ) {
                        Text("Спробувати ще раз", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatus(syncState: ProgressSyncState) {
    val message = when (syncState) {
        ProgressSyncState.SignedOut,
        ProgressSyncState.Idle -> "Акаунт підключено. Прогрес очікує синхронізації."
        ProgressSyncState.Syncing -> "Зберігаємо прогрес у хмарі…"
        is ProgressSyncState.Synced -> when (syncState.wordCount) {
            0 -> "Хмарну синхронізацію підключено. Прогресу поки немає."
            else -> "Локальний прогрес підтверджено хмарою: ${syncState.wordCount} слів."
        }
        is ProgressSyncState.Error -> syncState.message
        ProgressSyncState.AccountMismatch ->
            "Прогрес уже прив’язаний до іншого Google-акаунта. Дані не змішано — вийдіть і використайте попередній акаунт."
    }
    val isError = syncState is ProgressSyncState.Error ||
        syncState is ProgressSyncState.AccountMismatch

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
        }
    ) {
        if (syncState is ProgressSyncState.Syncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = when (syncState) {
                    is ProgressSyncState.Synced -> Icons.Outlined.CheckCircle
                    is ProgressSyncState.Error,
                    ProgressSyncState.AccountMismatch -> Icons.Outlined.ErrorOutline
                    else -> Icons.Outlined.Schedule
                },
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else AppTheme.colors.success,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SignedOutAccountContent(onSignIn: () -> Unit) {
    Text(
        text = "Увійдіть через Google для збереження прогресу між пристроями. Без входу можна навчатися на цьому пристрої.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onSignIn,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("google_sign_in_button")
    ) {
        Text("Увійти через Google", fontWeight = FontWeight.SemiBold)
    }
}

