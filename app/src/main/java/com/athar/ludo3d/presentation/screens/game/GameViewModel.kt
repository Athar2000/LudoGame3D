package com.athar.ludo3d.presentation.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.engine.AIDifficulty
import com.athar.ludo3d.domain.engine.LudoAIEngine
import com.athar.ludo3d.domain.engine.LudoGameEngine
import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.MoveResult
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.ValidMove
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Added by Athar
// Drives a LOCAL game (pass-and-play / practice, section 2) directly off the
// real LudoGameEngine — no Firebase involved, so this screen works fully
// offline. The exact same engine instance is what Phase 6's Cloud Function
// will run server-side for online games; this view model is what online
// play's "apply the server's broadcast MoveResult to local UI state" will
// eventually reuse too, since both cases boil down to "engine state changed,
// re-render."
//
// When any Player in the initial list has isAI = true, this view model also
// drives that player's turns automatically via LudoAIEngine (section 11) —
// rolling, waiting briefly so the move is visible, then picking and applying
// a move — with no network or LLM call on the hot path.
data class GameUiState(
    val gameState: GameState? = null,
    val validMoves: List<ValidMove> = emptyList(),
    val lastMoveResult: MoveResult? = null, // drives capture/win animations & sound cues
    val isRolling: Boolean = false,
    val statusMessage: String = ""
)

class GameViewModel(
    players: List<Player>,
    private val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    private val engine: LudoGameEngine = LudoGameEngine(
        GameState(roomId = "local", players = players, status = GameStatus.IN_PROGRESS)
    )
) : ViewModel() {

    private val aiEngine = LudoAIEngine(aiDifficulty)

    private val _uiState = MutableStateFlow(GameUiState(gameState = engine.state))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        publishState()
        maybeTakeAiTurn()
    }

    /** Highlighted (tappable) tokens for the board renderer — empty until dice is rolled. */
    val highlightedTokens: Set<Pair<String, Int>>
        get() {
            val current = engine.state.currentPlayer
            return _uiState.value.validMoves.map { current.userId to it.tokenId }.toSet()
        }

    fun rollDice() {
        val state = engine.state
        if (state.status != GameStatus.IN_PROGRESS || state.hasRolledThisTurn) return
        if (state.currentPlayer.isAI) return // AI turns are driven by maybeTakeAiTurn(), never by UI taps

        viewModelScope.launch {
            _uiState.update { it.copy(isRolling = true, statusMessage = "Rolling...") }
            delay(500) // lets the dice-roll animation play before the result lands (section 9)

            engine.rollDice()
            val moves = engine.getValidMoves()

            if (moves.isEmpty()) {
                // No legal move for this roll: auto-skip after a short pause so the
                // player can see what was rolled (section 8: "no valid move handling").
                publishState(message = "Rolled ${engine.state.diceValue} — no valid move")
                delay(700)
                engine.skipTurnIfNoValidMoves()
                _uiState.update { it.copy(isRolling = false) }
                publishState(message = "No valid move — turn passed")
                maybeTakeAiTurn()
            } else {
                _uiState.update { it.copy(isRolling = false, validMoves = moves) }
                publishState(message = "Choose a token to move")
            }
        }
    }

    fun selectToken(playerId: String, tokenId: Int) {
        val state = engine.state
        if (state.currentPlayer.userId != playerId) return
        if (state.currentPlayer.isAI) return // AI never receives UI taps
        if (_uiState.value.validMoves.none { it.tokenId == tokenId }) return

        applyMove(playerId, tokenId)
        maybeTakeAiTurn()
    }

    private fun applyMove(playerId: String, tokenId: Int) {
        val result = engine.moveToken(playerId, tokenId)
        _uiState.update { it.copy(validMoves = emptyList(), lastMoveResult = result) }

        val message = when {
            result.winnerId != null -> "${engine.state.players.first { it.userId == result.winnerId }.name} wins!"
            result.capturedTokens.isNotEmpty() -> "Captured! Extra turn"
            result.grantsExtraTurn -> "Rolled a 6 — go again"
            else -> "${engine.state.currentPlayer.name}'s turn"
        }
        publishState(message = message)
    }

    /**
     * Drives every consecutive AI turn (including AI extra-turns on six) with a small
     * delay between each step so a human watching can actually follow what happened,
     * rather than the whole AI sequence resolving instantly and unreadably.
     */
    private fun maybeTakeAiTurn() {
        val state = engine.state
        if (state.status != GameStatus.IN_PROGRESS) return
        if (!state.currentPlayer.isAI) return

        viewModelScope.launch {
            delay(600)
            _uiState.update { it.copy(isRolling = true, statusMessage = "${engine.state.currentPlayer.name} is rolling...") }
            delay(500)

            engine.rollDice()
            val moves = engine.getValidMoves()
            _uiState.update { it.copy(isRolling = false) }

            if (moves.isEmpty()) {
                publishState(message = "${engine.state.currentPlayer.name} rolled ${engine.state.diceValue} — no valid move")
                delay(500)
                engine.skipTurnIfNoValidMoves()
                publishState(message = "No valid move for ${state.currentPlayer.name} — turn passed")
            } else {
                delay(400)
                val chosen = aiEngine.chooseBestMove(engine.state, moves)
                applyMove(engine.state.currentPlayer.userId, chosen.tokenId)
            }

            maybeTakeAiTurn() // recurse: handles extra turns and the next AI player in sequence
        }
    }

    private fun publishState(message: String = _uiState.value.statusMessage) {
        _uiState.update { it.copy(gameState = engine.state, statusMessage = message) }
    }
}
