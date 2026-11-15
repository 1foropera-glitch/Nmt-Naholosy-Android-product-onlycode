package com.example.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

data class AuthAccount(
    val uid: String,
    val displayName: String?,
    val email: String?
)

sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    data object SigningIn : AuthUiState
    data class SignedIn(val account: AuthAccount) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

interface AuthRepository : AutoCloseable {
    val authState: kotlinx.coroutines.flow.StateFlow<AuthUiState>

    fun beginGoogleSignIn()
    fun cancelGoogleSignIn()
    fun reportGoogleSignInError(message: String)
    suspend fun signInWithGoogleIdToken(idToken: String)
    fun signOut()
}

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    private val mutableAuthState = kotlinx.coroutines.flow.MutableStateFlow(
        firebaseAuth.currentUser.toAuthUiState()
    )
    override val authState: kotlinx.coroutines.flow.StateFlow<AuthUiState> =
        mutableAuthState

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        mutableAuthState.value = auth.currentUser.toAuthUiState()
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    override fun beginGoogleSignIn() {
        if (firebaseAuth.currentUser == null) {
            mutableAuthState.value = AuthUiState.SigningIn
        }
    }

    override fun cancelGoogleSignIn() {
        mutableAuthState.value = firebaseAuth.currentUser.toAuthUiState()
    }

    override fun reportGoogleSignInError(message: String) {
        if (firebaseAuth.currentUser == null) {
            mutableAuthState.value = AuthUiState.Error(message)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        mutableAuthState.value = AuthUiState.SigningIn
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).awaitResult()
            mutableAuthState.value = firebaseAuth.currentUser.toAuthUiState()
        } catch (exception: CancellationException) {
            mutableAuthState.value = firebaseAuth.currentUser.toAuthUiState()
            throw exception
        } catch (exception: Exception) {
            mutableAuthState.value = AuthUiState.Error(exception.toUserMessage())
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
        mutableAuthState.value = AuthUiState.SignedOut
    }

    override fun close() {
        firebaseAuth.removeAuthStateListener(authStateListener)
    }
}

private fun FirebaseUser?.toAuthUiState(): AuthUiState = if (this == null) {
    AuthUiState.SignedOut
} else {
    AuthUiState.SignedIn(
        AuthAccount(
            uid = uid,
            displayName = displayName,
            email = email
        )
    )
}

private fun Exception.toUserMessage(): String = when ((this as? FirebaseAuthException)?.errorCode) {
    "ERROR_NETWORK_REQUEST_FAILED" ->
        "Немає з’єднання з мережею. Перевірте інтернет і спробуйте ще раз."
    "ERROR_USER_DISABLED" ->
        "Цей акаунт вимкнено. Зверніться до підтримки застосунку."
    "ERROR_INVALID_CREDENTIAL" ->
        "Google не підтвердив вхід. Спробуйте вибрати акаунт ще раз."
    else -> "Не вдалося увійти через Google. Спробуйте ще раз."
}

private suspend fun Task<AuthResult>.awaitResult(): AuthResult =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener

            when {
                task.isSuccessful -> {
                    val result = task.result
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("Firebase Auth returned no result")
                        )
                    }
                }
                task.isCanceled -> continuation.cancel()
                else -> continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase Auth failed without an exception")
                )
            }
        }
    }
