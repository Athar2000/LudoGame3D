package com.athar.ludo3d.presentation.screens.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.RewardsRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class DailyRewardUiState(
    val currentStreakDay: Int = 0,
    val canClaimToday: Boolean = true,
    val isClaiming: Boolean = false,
    val lastResultMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DailyRewardViewModel @Inject constructor(
    private val rewardsRepository: RewardsRepository,
    userProfileRepository: UserProfileRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyRewardUiState())
    val uiState: StateFlow<DailyRewardUiState> = _uiState.asStateFlow()

    init {
        userProfileRepository.observeProfile(authRepository.currentUser()?.uid ?: "")
            .onEach { profile ->
                if (profile == null) return@onEach
                val hoursSinceClaim = (System.currentTimeMillis() - profile.lastDailyRewardClaimedAtMillis) / 3_600_000.0
                _uiState.update {
                    it.copy(
                        currentStreakDay = profile.lastDailyRewardClaimedDay,
                        canClaimToday = profile.lastDailyRewardClaimedAtMillis == 0L || hoursSinceClaim >= 24.0
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun claim() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClaiming = true, errorMessage = null) }
            rewardsRepository.claimDailyReward()
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isClaiming = false,
                            lastResultMessage = "Day ${result.day}: +${result.coinsAwarded} coins" +
                                if (result.isPremium) " (premium reward!)" else ""
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isClaiming = false, errorMessage = e.message ?: "Could not claim") }
                }
        }
    }
}
