package com.example.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Failure(val message: String) : GoogleSignInResult
}

class GoogleSignInCoordinator(context: Context) {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    suspend fun requestGoogleIdToken(
        activity: ComponentActivity,
        serverClientId: String
    ): GoogleSignInResult {
        return try {
            val credential = withTimeoutOrNull(SIGN_IN_TIMEOUT_MILLIS) {
                getCredential(
                    activity = activity,
                    serverClientId = serverClientId
                )
            } ?: return GoogleSignInResult.Failure(
                "Вікно входу Google не відповіло. Спробуйте ще раз."
            )

            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(googleCredential.idToken)
            } else {
                GoogleSignInResult.Failure(
                    "Google повернув непідтримуваний тип входу. Спробуйте ще раз."
                )
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Failure(
                "На пристрої немає доступного Google-акаунта. Додайте його в налаштуваннях Android."
            )
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failure(
                "Не вдалося відкрити вхід Google. Перевірте Google Play services і спробуйте ще раз."
            )
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failure(
                "Не вдалося прочитати відповідь Google. Спробуйте ще раз."
            )
        } catch (_: IllegalArgumentException) {
            GoogleSignInResult.Failure(
                "Не вдалося прочитати відповідь Google. Спробуйте ще раз."
            )
        }
    }

    suspend fun clearCredentialState() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private suspend fun getCredential(
        activity: ComponentActivity,
        serverClientId: String
    ) = credentialManager.getCredential(
        context = activity,
        request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(
                    serverClientId = serverClientId
                )
                    .build()
            )
            .build()
    ).credential

    private companion object {
        const val SIGN_IN_TIMEOUT_MILLIS = 60_000L
    }
}
