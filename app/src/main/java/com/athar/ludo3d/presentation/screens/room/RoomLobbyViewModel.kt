package com.athar.ludo3d.presentation.screens.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.model.Room
import com.athar.ludo3d.domain.model.RoomStatus
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.GameSyncRepository
import com.athar.ludo3d.domain.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
// Section 4's lobby: "Player 1 - Athar READY / Player 2 - Ali READY ...".
// Entirely driven by Firebase's live listener (RoomRepository.observeRoom) —
// there is no local "players joined" state kept anywhere else, so what every
// phone in the room sees is always exactly what's in Realtime Database.
data class RoomLobbyUiState(
    val room: Room? = null,
    val currentUserId: String = "",
    val errorMessage: String? = null,
    val isStarting: Boolean = false,
    val hasLeft: Boolean = false
) {
    val isHost: Boolean get() = room?.hostId == currentUserId
    val gameHasStarted: Boolean get() = room?.status == RoomStatus.IN_PROGRESS
}

@HiltViewModel
class RoomLobbyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepository: RoomRepository,
    private val gameSyncRepository: GameSyncRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _uiState = MutableStateFlow(
        RoomLobbyUiState(currentUserId = authRepository.currentUser()?.uid ?: "")
    )
    val uiState: StateFlow<RoomLobbyUiState> = _uiState.asStateFlow()

    init {
        roomRepository.observeRoom(roomId)
            .onEach { room -> _uiState.update { it.copy(room = room) } }
            .catch { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Connection lost") } }
            .launchIn(viewModelScope)
    }

    fun toggleReady() {
        val current = _uiState.value.room?.players?.firstOrNull { it.userId == _uiState.value.currentUserId }
        val nowReady = !(current?.ready ?: false)
        viewModelScope.launch { roomRepository.setReady(roomId, nowReady) }
    }

    fun startGame() {
        val room = _uiState.value.room ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, errorMessage = null) }

            // Order matters: flip the room to IN_PROGRESS first (this re-validates
            // host/ready/count server-side-equivalent, per FirebaseRoomRepository),
            // THEN initialize gameState — so no other phone can observe
            // status == IN_PROGRESS with gameState still missing.
            roomRepository.startGame(room.roomId)
                .onSuccess {
                    gameSyncRepository.startGame(room)
                        .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Could not start the game") } }
                }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Could not start the game") } }

            _uiState.update { it.copy(isStarting = false) }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            runCatching { roomRepository.leaveRoom(roomId) }
            _uiState.update { it.copy(hasLeft = true) }
        }
    }
}
