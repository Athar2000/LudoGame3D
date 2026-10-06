package com.athar.ludo3d.presentation.screens.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.RoomError
import com.athar.ludo3d.domain.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class PrivateRoomEntryUiState(
    val joinCode: String = "",
    val maxPlayers: Int = 4,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val navigateToRoomId: String? = null
)

@HiltViewModel
class PrivateRoomEntryViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateRoomEntryUiState())
    val uiState: StateFlow<PrivateRoomEntryUiState> = _uiState.asStateFlow()

    fun onJoinCodeChange(value: String) = _uiState.update { it.copy(joinCode = value, errorMessage = null) }
    fun onMaxPlayersChange(value: Int) = _uiState.update { it.copy(maxPlayers = value) }

    fun createRoom() {
        val hostName = authRepository.currentUser()?.displayName ?: "Host"
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            roomRepository.createRoom(hostName, _uiState.value.maxPlayers)
                .onSuccess { roomId -> _uiState.update { it.copy(isLoading = false, navigateToRoomId = roomId) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.readableMessage()) } }
        }
    }

    fun joinRoom() {
        val code = _uiState.value.joinCode.trim()
        if (code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a room code") }
            return
        }
        val playerName = authRepository.currentUser()?.displayName ?: "Player"
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            roomRepository.joinRoom(code, playerName)
                .onSuccess { _uiState.update { it.copy(isLoading = false, navigateToRoomId = code.uppercase()) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.readableMessage()) } }
        }
    }

    private fun Throwable.readableMessage(): String = when (this) {
        is RoomError -> this.message ?: "Something went wrong"
        else -> message ?: "Something went wrong. Check your connection and try again."
    }
}
