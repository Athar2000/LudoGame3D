package com.athar.ludo3d.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.data.firebase.GoogleSignInHelper
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val authenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: GoogleSignInHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun toggleMode() = _uiState.update { it.copy(isSignUpMode = !it.isSignUpMode, errorMessage = null) }

    fun submitEmailAuth() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email and a 6+ character password") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (state.isSignUpMode) {
                val name = state.displayName.ifBlank { state.email.substringBefore("@") }
                authRepository.signUpWithEmail(state.email, state.password, name)
            } else {
                authRepository.signInWithEmail(state.email, state.password)
            }
            applyResult(result)
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            applyResult(authRepository.signInAsGuest())
        }
    }

    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runCatching {
                val idToken = googleSignInHelper.requestGoogleIdToken(context)
                authRepository.signInWithGoogleIdToken(idToken)
            }.getOrElse { e -> AuthResult.Failure(e.message ?: "Google sign-in failed") }
            applyResult(result)
        }
    }

    private fun applyResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, authenticated = true) }
            is AuthResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
        }
    }
}
