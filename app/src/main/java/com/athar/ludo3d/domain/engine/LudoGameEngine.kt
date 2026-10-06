package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.CapturedToken
import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.MoveResult
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.Token
import com.athar.ludo3d.domain.model.ValidMove
import kotlin.random.Random

/**
 * Added by Athar
 *
 * LudoGameEngine is the single source of truth for standard Ludo rules.
 * It has ZERO dependency on Android, Firebase, or Compose — it is pure Kotlin
 * operating on immutable data classes, so it can:
 *   1. Run identically in a Firebase Cloud Function (Node/JS port) as the
 *      server-authoritative validator (section 6), and
 *   2. Be unit tested completely offline (section 23), and
 *   3. Drive local single-player / AI-vs-human games (section 2) without any
 *      network dependency.
 *
 * IMPORTANT: this class itself does not trust "the client". In production,
 * this exact logic must be re-run server-side (Cloud Function) before a move
 * is committed to Realtime Database — see /firebase/README in this project
 * for the validation pipeline. Locally (single player / AI / practice) this
 * engine's output IS authoritative, since there is no other client to cheat.
 */
class LudoGameEngine(
    initialState: GameState,
    private val random: Random = Random.Default // injectable for deterministic tests
) {
    var state: GameState = initialState
        private set

    companion object {
        // Global squares (0..51) that are safe: cannot be captured on.
        // = each color's start square + the 4 classic star squares.
        val SAFE_SQUARES: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        const val TRACK_LENGTH = 51   // squares 1..51 on the shared ring (local numbering)
        const val HOME_COLUMN_START = 52
        const val HOME_COLUMN_END = 57
        const val MAX_CONSECUTIVE_SIXES = 3
    }

    /** rollDice() — section 8. Produces 1..6. Rejects rolling twice in one turn. */
    fun rollDice(): Int {
        check(state.status == GameStatus.IN_PROGRESS) { "Game is not in progress" }
        check(!state.hasRolledThisTurn) { "Dice already rolled this turn" }
        val value = random.nextInt(1, 7)
        state = state.copy(diceValue = value, hasRolledThisTurn = true)
        return value
    }

    /**
     * getValidMoves() — section 8.
     * Returns every legal move for the CURRENT player given the CURRENT dice value.
     * Empty list means "no valid move", which the caller must resolve via nextTurn().
     */
    fun getValidMoves(): List<ValidMove> {
        val dice = state.diceValue ?: return emptyList()
        val player = state.currentPlayer
        val moves = mutableListOf<ValidMove>()

        for (token in player.tokens) {
            if (token.isFinished) continue

            if (token.isInBase) {
                // Need exactly a 6 to release a token from base.
                if (dice == 6) {
                    moves += ValidMove(
                        tokenId = token.id,
                        fromPosition = token.position,
                        toPosition = 1,
                        capturesOpponent = wouldCapture(player, 1)
                    )
                }
                continue
            }

            val destination = token.position + dice
            if (destination > Token.FINISHED - 1 + 1) continue // cannot overshoot home
            if (destination > HOME_COLUMN_END + 1) continue

            val actualDestination = if (destination == HOME_COLUMN_END + 1) Token.FINISHED else destination
            if (actualDestination > Token.FINISHED) continue

            moves += ValidMove(
                tokenId = token.id,
                fromPosition = token.position,
                toPosition = actualDestination,
                capturesOpponent = wouldCapture(player, actualDestination)
            )
        }
        return moves
    }

    /**
     * moveToken() — section 8.
     * Applies a move that MUST be present in getValidMoves() (server / engine
     * validates this — the caller cannot invent an arbitrary destination).
     */
    fun moveToken(playerId: String, tokenId: Int): MoveResult {
        val dice = state.diceValue ?: error("No dice value to apply")
        val playerIndex = state.players.indexOfFirst { it.userId == playerId }
        require(playerIndex == state.currentPlayerIndex) { "Not this player's turn" }

        val validMove = getValidMoves().firstOrNull { it.tokenId == tokenId }
            ?: error("Illegal move: token $tokenId cannot move with dice=$dice")

        val player = state.players[playerIndex]
        val updatedToken = Token(id = tokenId, position = validMove.toPosition)

        // Capture resolution — only meaningful on the shared path, and never on a safe square.
        val captured = mutableListOf<CapturedToken>()
        var newPlayers = state.players.toMutableList()

        if (validMove.toPosition in 1..TRACK_LENGTH) {
            val globalSquare = toGlobalSquare(player, validMove.toPosition)
            if (globalSquare !in SAFE_SQUARES) {
                newPlayers = newPlayers.map { opponent ->
                    if (opponent.userId == playerId) return@map opponent
                    val opponentTokens = opponent.tokens.map { t ->
                        if (t.isOnSharedPath && toGlobalSquare(opponent, t.position) == globalSquare) {
                            captured += CapturedToken(opponent.userId, t.id)
                            Token(id = t.id, position = Token.BASE) // sent home
                        } else t
                    }
                    opponent.copy(tokens = opponentTokens)
                }.toMutableList()
            }
        }

        val movingPlayer = newPlayers[playerIndex]
        newPlayers[playerIndex] = movingPlayer.copy(
            tokens = movingPlayer.tokens.map { if (it.id == tokenId) updatedToken else it }
        )

        val winner = newPlayers[playerIndex].takeIf { it.hasWon }
        val newSixStreak = if (dice == 6) state.consecutiveSixes + 1 else 0
        val forfeitForTripleSix = newSixStreak >= MAX_CONSECUTIVE_SIXES
        val extraTurn = (dice == 6 || captured.isNotEmpty() || validMove.toPosition == Token.FINISHED) &&
            !forfeitForTripleSix

        state = state.copy(
            players = newPlayers,
            winnerId = winner?.userId ?: state.winnerId,
            status = if (winner != null) GameStatus.FINISHED else state.status,
            hasRolledThisTurn = true,
            consecutiveSixes = newSixStreak
        )

        if (winner == null) {
            if (!extraTurn) {
                advanceTurn()
            } else {
                // Extra turn: same player rolls again.
                state = state.copy(diceValue = null, hasRolledThisTurn = false)
            }
        }

        return MoveResult(
            playerId = playerId,
            tokenId = tokenId,
            fromPosition = validMove.fromPosition,
            toPosition = validMove.toPosition,
            capturedTokens = captured,
            grantsExtraTurn = extraTurn && winner == null,
            winnerId = winner?.userId
        )
    }

    /** isSafeCell() — section 8. Global square index (0..51). */
    fun isSafeCell(globalSquare: Int): Boolean = globalSquare in SAFE_SQUARES

    /** captureOpponent() helper, exposed for the AI engine's risk evaluation. */
    fun wouldCapture(mover: Player, destinationLocalPos: Int): Boolean {
        if (destinationLocalPos !in 1..TRACK_LENGTH) return false
        val globalSquare = toGlobalSquare(mover, destinationLocalPos)
        if (globalSquare in SAFE_SQUARES) return false
        return state.players.any { opponent ->
            opponent.userId != mover.userId &&
                opponent.tokens.any { it.isOnSharedPath && toGlobalSquare(opponent, it.position) == globalSquare }
        }
    }

    /** hasPlayerWon() — section 8. */
    fun hasPlayerWon(playerId: String): Boolean =
        state.players.firstOrNull { it.userId == playerId }?.hasWon == true

    /**
     * nextTurn() — section 8.
     * Called when: (a) player has no valid moves, (b) player rolled 3 sixes in a
     * row (forfeits turn per section 8's "extra turn on 6 where applicable"), or
     * (c) a move was completed without earning an extra turn.
     */
    fun nextTurn() {
        if (state.status == GameStatus.FINISHED) return
        advanceTurn()
    }

    /** Call after rollDice() when getValidMoves() is empty, to auto-skip the turn. */
    fun skipTurnIfNoValidMoves(): Boolean {
        if (state.diceValue == null) return false
        if (getValidMoves().isNotEmpty()) return false
        if (state.diceValue == 6 && state.consecutiveSixes < MAX_CONSECUTIVE_SIXES - 1) {
            // Rolled a 6 with no token able to move at all (rare, all tokens finished/blocked):
            // still consumes the extra turn since there's nothing to move.
        }
        advanceTurn()
        return true
    }

    // ---- internal helpers ----

    private fun toGlobalSquare(player: Player, localPos: Int): Int =
        (player.color.startOffset + localPos - 1).mod(52)

    private fun advanceTurn() {
        var nextIndex = state.currentPlayerIndex
        do {
            nextIndex = (nextIndex + 1) % state.players.size
        } while (state.players[nextIndex].hasWon && nextIndex != state.currentPlayerIndex)

        state = state.copy(
            currentPlayerIndex = nextIndex,
            diceValue = null,
            hasRolledThisTurn = false,
            consecutiveSixes = 0,
            turnStartedAtMillis = 0L
        )
    }
}
