package com.athar.ludo3d.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// Added by Athar
// Phase 8: name/level/coins now come from the live users/{uid} profile
// (UserProfileRepository), replacing the Phase 1 placeholder defaults. Until
// the profile node exists (created server-side by initializeUserProfile right
// after sign-up), the header shows the Firebase Auth display name and zeros.
data class HomeUiState(
    val playerName: String = "Player",
    val level: Int = 1,
    val coins: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val authUser = authRepository.currentUser()

    private val _uiState = MutableStateFlow(HomeUiState(playerName = authUser?.displayName ?: "Player"))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        authUser?.uid?.let { uid ->
            userProfileRepository.observeProfile(uid)
                .onEach { profile ->
                    if (profile != null) {
                        _uiState.update { it.copy(playerName = profile.name, level = profile.level, coins = profile.coins) }
                    }
                }
                .launchIn(viewModelScope)
        }
    }
}
