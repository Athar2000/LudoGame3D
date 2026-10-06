package com.athar.ludo3d.presentation.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.model.LeaderboardEntry
import com.athar.ludo3d.domain.model.LeaderboardScope
import com.athar.ludo3d.domain.repository.LeaderboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class LeaderboardUiState(
    val scope: LeaderboardScope = LeaderboardScope.GLOBAL,
    val entries: List<LeaderboardEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectScope(scope: LeaderboardScope) {
        if (scope == _uiState.value.scope) return
        _uiState.update { it.copy(scope = scope) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = when (_uiState.value.scope) {
                LeaderboardScope.GLOBAL -> leaderboardRepository.getGlobalLeaderboard()
                // No friends system exists yet (see LeaderboardRepository's doc comment) —
                // an empty friend list correctly renders as "no friends yet" rather than
                // silently falling back to the global list, which would misrepresent it.
                LeaderboardScope.FRIENDS -> leaderboardRepository.getFriendsLeaderboard(emptyList())
            }
            result
                .onSuccess { entries -> _uiState.update { it.copy(entries = entries, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
        }
    }
}
