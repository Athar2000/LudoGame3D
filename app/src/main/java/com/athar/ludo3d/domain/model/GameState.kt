package com.athar.ludo3d.domain.model

// Added by Athar
enum class GameStatus { WAITING, IN_PROGRESS, PAUSED, FINISHED }

/** A single legal move offered to the current player after a dice roll. */
data class ValidMove(
    val tokenId: Int,
    val fromPosition: Int,
    val toPosition: Int,
    val capturesOpponent: Boolean
)

/** Result of applying a move to the engine: what the UI/network layer needs to broadcast. */
data class MoveResult(
    val playerId: String,
    val tokenId: Int,
    val fromPosition: Int,
    val toPosition: Int,
    val capturedTokens: List<CapturedToken>,
    val grantsExtraTurn: Boolean,
    val winnerId: String?
)

data class CapturedToken(val playerId: String, val tokenId: Int)

/**
 * The full authoritative state of one Ludo match.
 * This is the object that gets mirrored 1:1 into Firebase Realtime Database
 * under rooms/{roomId}/gameState once Phase 5 wires up sync — see README.
 */
data class GameState(
    val roomId: String,
    val players: List<Player>,
    val currentPlayerIndex: Int = 0,
    val diceValue: Int? = null,
    val consecutiveSixes: Int = 0,
    val hasRolledThisTurn: Boolean = false,
    val status: GameStatus = GameStatus.WAITING,
    val winnerId: String? = null,
    val turnStartedAtMillis: Long = 0L
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
}
