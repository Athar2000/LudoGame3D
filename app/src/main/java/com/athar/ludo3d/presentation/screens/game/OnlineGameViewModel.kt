package com.athar.ludo3d.presentation.screens.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.engine.LudoGameEngine
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.GameSyncRepository
import com.athar.ludo3d.domain.repository.TURN_DURATION_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
// The online counterpart to GameViewModel (Phase 2). Where GameViewModel
// holds a LudoGameEngine directly and mutates it in memory, this class NEVER
// mutates game state itself — every roll/move is a suspend call to
// GameSyncRepository, which re-reads Firebase, runs the engine, and writes
// the result; THIS class only reflects whatever observeGameState() emits.
// That is the actual meaning of "server-authoritative for online play": the
// source of truth is Firebase, not this view model's local memory.
@HiltViewModel
class OnlineGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameSyncRepository: GameSyncRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])
    val currentUserId: String = authRepository.currentUser()?.uid ?: ""

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _turnSecondsRemaining = MutableStateFlow<Int?>(null)
    val turnSecondsRemaining: StateFlow<Int?> = _turnSecondsRemaining.asStateFlow()

    private var timerJob: Job? = null
    private var lastTurnStamp: Long = -1L
    private var hasAttemptedAutoSkipThisTurn = false

    val highlightedTokens: Set<Pair<String, Int>>
        get() {
            val state = _uiState.value.gameState ?: return emptySet()
            if (state.currentPlayer.userId != currentUserId) return emptySet()
            if (!state.hasRolledThisTurn) return emptySet()
            // Recompute locally purely for UI highlighting — the actual move
            // legality is re-checked authoritatively by submitMove() itself.
            val moves = LudoGameEngine(state).getValidMoves()
            return moves.map { state.currentPlayer.userId to it.tokenId }.toSet()
        }

    init {
        gameSyncRepository.observeGameState(roomId)
            .onEach { state ->
                _uiState.update {
                    it.copy(
                        gameState = state,
                        statusMessage = state?.let(::describe) ?: "Connecting..."
                    )
                }
                if (state != null) restartTimerIfTurnChanged(state.turnStartedAtMillis, state.status)
            }
            .catch { e -> _uiState.update { it.copy(statusMessage = "Connection lost: ${e.message ?: "unknown error"}") } }
            .launchIn(viewModelScope)
    }

    fun rollDice() {
        val state = _uiState.value.gameState ?: return
        if (state.currentPlayer.userId != currentUserId || state.hasRolledThisTurn) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRolling = true) }
            gameSyncRepository.rollDice(roomId)
                .onFailure { e -> _uiState.update { it.copy(statusMessage = e.message ?: "Could not roll") } }
            _uiState.update { it.copy(isRolling = false) }
        }
    }

    fun selectToken(playerId: String, tokenId: Int) {
        if (playerId != currentUserId) return
        viewModelScope.launch {
            gameSyncRepository.submitMove(roomId, tokenId)
                .onFailure { e -> _uiState.update { it.copy(statusMessage = e.message ?: "Could not move") } }
        }
    }

    fun leaveGame() {
        viewModelScope.launch { gameSyncRepository.leaveGame(roomId) }
    }

    private fun describe(state: com.athar.ludo3d.domain.model.GameState): String = when {
        state.status == GameStatus.FINISHED ->
            "${state.players.first { it.userId == state.winnerId }.name} wins!"
        state.currentPlayer.userId == currentUserId && !state.hasRolledThisTurn -> "Your turn — roll the dice"
        state.currentPlayer.userId == currentUserId -> "Choose a token to move"
        else -> "${state.currentPlayer.name}'s turn"
    }

    /**
     * Restarts the visible countdown whenever Firebase reports a new
     * turnStartedAt (i.e. the turn actually changed), and — once it hits
     * zero — calls forceSkipExpiredTurn() ONCE per turn. Every connected
     * phone runs this same logic, so whichever one's timer fires first
     * resolves it; the repository call is safe to lose a race on since a
     * turn that already advanced makes a second attempt a no-op read.
     */
    private fun restartTimerIfTurnChanged(turnStartedAtMillis: Long, status: GameStatus) {
        if (turnStartedAtMillis == lastTurnStamp) return
        lastTurnStamp = turnStartedAtMillis
        hasAttemptedAutoSkipThisTurn = false
        timerJob?.cancel()

        if (status != GameStatus.IN_PROGRESS || turnStartedAtMillis <= 0L) {
            _turnSecondsRemaining.value = null
            return
        }

        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - turnStartedAtMillis
                val remaining = ((TURN_DURATION_MS - elapsed) / 1000).toInt()
                _turnSecondsRemaining.value = remaining.coerceAtLeast(0)

                if (remaining <= 0) {
                    if (!hasAttemptedAutoSkipThisTurn) {
                        hasAttemptedAutoSkipThisTurn = true
                        gameSyncRepository.forceSkipExpiredTurn(roomId)
                    }
                    return@launch
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
