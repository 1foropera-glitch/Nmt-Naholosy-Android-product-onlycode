package com.example.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps local study available when Firebase configuration is deliberately absent. */
class LocalOnlyAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthUiState>(AuthUiState.SignedOut)
    override val authState: StateFlow<AuthUiState> = state
    override fun beginGoogleSignIn() = reportGoogleSignInError("Хмарний вхід недоступний у цій збірці.")
    override fun cancelGoogleSignIn() { state.value = AuthUiState.SignedOut }
    override fun reportGoogleSignInError(message: String) { state.value = AuthUiState.Error(message) }
    override suspend fun signInWithGoogleIdToken(idToken: String) { beginGoogleSignIn() }
    override fun signOut() { state.value = AuthUiState.SignedOut }
    override fun close() = Unit
}
