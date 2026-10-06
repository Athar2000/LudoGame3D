package com.athar.ludo3d.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.model.UserProfile
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Added by Athar
// Section 18. Purely a read view over UserProfileRepository's live stream —
// there's no local mutable state to manage here at all, which is a natural
// consequence of coins/level/xp/wins/gamesPlayed all being Cloud-Function-
// owned (Phase 6/8): this screen has literally nothing it could compute
// wrong, since it isn't computing anything.
@HiltViewModel
class ProfileViewModel @Inject constructor(
    userProfileRepository: UserProfileRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = userProfileRepository
        .observeProfile(authRepository.currentUser()?.uid ?: "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
