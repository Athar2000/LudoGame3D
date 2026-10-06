package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token
import com.athar.ludo3d.domain.model.ValidMove
import kotlin.random.Random

/**
 * Added by Athar
 *
 * LudoAIEngine — section 11. Chooses a move for a computer-controlled player
 * given the CURRENT LudoGameEngine state and the list of legal moves it
 * already returned from getValidMoves(). This is pure local computation —
 * no network calls, no LLM — so it resolves in well under a millisecond and
 * can run on every dice roll without any latency or API cost.
 *
 * Section 12's optional AI Assistant (tips/commentary) is a SEPARATE,
 * clearly-optional feature that MAY call an external generative model for
 * flavor text; it must never be on this class's critical path, since the
 * spec requires the core AI to work "locally and instantly" even with no
 * network connection at all.
 */
enum class AIDifficulty { EASY, MEDIUM, HARD }

class LudoAIEngine(
    private val difficulty: AIDifficulty,
    private val random: Random = Random.Default
) {

    /** chooseBestMove() — section 11. Never empty-input-safe: caller must ensure moves is non-empty. */
    fun chooseBestMove(state: GameState, moves: List<ValidMove>): ValidMove {
        require(moves.isNotEmpty()) { "chooseBestMove called with no legal moves" }
        if (moves.size == 1) return moves.first()

        return when (difficulty) {
            AIDifficulty.EASY -> moves[random.nextInt(moves.size)]
            AIDifficulty.MEDIUM -> moves.maxBy { calculateMoveScore(state, it, includeRisk = false) }
            AIDifficulty.HARD -> moves.maxBy { calculateMoveScore(state, it, includeRisk = true) }
        }
    }

    /** calculateMoveScore() — section 11. Higher is better. Combines capture value, progress, safety, and risk. */
    fun calculateMoveScore(state: GameState, move: ValidMove, includeRisk: Boolean = true): Double {
        val mover = state.currentPlayer
        var score = 0.0

        // Releasing a token from base is good — more active tokens means more options later.
        if (move.fromPosition == Token.BASE) score += 8.0

        // Progress made this move (small constant weight so it doesn't dominate captures/safety).
        score += (move.toPosition - move.fromPosition).coerceAtLeast(0) * 0.5

        // Finishing a token outright is close to a guaranteed-good outcome.
        if (move.toPosition == Token.FINISHED) score += 60.0

        // Entering the private home column (can no longer be captured) is a strong safety gain.
        if (move.toPosition in 52..57 && move.fromPosition !in 52..57) score += 20.0

        score += evaluateCapture(state, mover, move)

        if (move.toPosition in 1..LudoGameEngine.TRACK_LENGTH) {
            val globalSquare = globalSquareOf(mover.color, move.toPosition)
            if (LudoGameEngine.SAFE_SQUARES.contains(globalSquare)) {
                score += 12.0 // landing on a safe/star square is good defensively
            } else if (includeRisk) {
                score -= evaluateRisk(state, mover, globalSquare) * 15.0
            }
        }

        return score
    }

    /**
     * evaluateCapture() — section 11. Value of capturing an opponent on this move:
     * proportional to how far that opponent's token had already traveled (sending a
     * token that's almost home back to base hurts them far more than an early one).
     */
    fun evaluateCapture(state: GameState, mover: Player, move: ValidMove): Double {
        if (!move.capturesOpponent || move.toPosition !in 1..LudoGameEngine.TRACK_LENGTH) return 0.0
        val globalSquare = globalSquareOf(mover.color, move.toPosition)

        var value = 0.0
        state.players.forEach { opponent ->
            if (opponent.userId == mover.userId) return@forEach
            opponent.tokens.forEach { token ->
                if (token.isOnSharedPath && globalSquareOf(opponent.color, token.position) == globalSquare) {
                    value += 25.0 + token.position * 1.2 // further along = more valuable capture
                }
            }
        }
        return value
    }

    /**
     * evaluateRisk() — section 11. How exposed a token would be at [globalSquare]:
     * counts opponent tokens on the shared path that could reach this exact square
     * with a single die roll of 1..6 next turn (a real, if simplified, threat model —
     * it ignores multi-turn combinations, which HARD difficulty intentionally keeps
     * cheap so move selection stays instant).
     */
    fun evaluateRisk(state: GameState, mover: Player, globalSquare: Int): Double {
        if (LudoGameEngine.SAFE_SQUARES.contains(globalSquare)) return 0.0

        var threats = 0
        state.players.forEach { opponent ->
            if (opponent.userId == mover.userId) return@forEach
            opponent.tokens.forEach tokenLoop@{ token ->
                if (!token.isOnSharedPath) return@tokenLoop
                val opponentGlobal = globalSquareOf(opponent.color, token.position)
                val distance = (globalSquare - opponentGlobal).mod(52)
                if (distance in 1..6) threats++
            }
        }
        return threats.toDouble()
    }

    private fun globalSquareOf(color: PlayerColor, localPos: Int): Int =
        (color.startOffset + localPos - 1).mod(52)
}
