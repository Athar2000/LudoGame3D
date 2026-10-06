package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token

// Added by Athar
// Phase 9. Works out WHICH sounds/effects a change between two GameStates
// should trigger. Pure Kotlin, so local play, AI play and online play all use
// exactly the same rule: online clients never receive "a capture happened"
// events from Firebase, they just see the new state arrive and diff it against
// the previous one.
enum class SoundEvent { DICE_ROLL, TOKEN_MOVE, CAPTURE, TOKEN_HOME, YOUR_TURN, WIN }

/** A token that was sent back to base; position is where it WAS, for placing the particle burst. */
data class CaptureInfo(val color: PlayerColor, val tokenId: Int, val previousPosition: Int)

data class GameEffects(
    val sounds: List<SoundEvent> = emptyList(),
    val captures: List<CaptureInfo> = emptyList(),
    val winnerId: String? = null
) {
    val isEmpty: Boolean get() = sounds.isEmpty() && captures.isEmpty() && winnerId == null
}

object GameEffectDetector {

    /**
     * @param previous last state this device saw; null on first emission (no effects, so joining or
     *                 reconnecting mid-game doesn't replay old sounds)
     * @param localPlayerId this device's user id for YOUR_TURN; null in pass-and-play
     */
    fun detect(previous: GameState?, current: GameState, localPlayerId: String?): GameEffects {
        if (previous == null || previous == current) return GameEffects()
        if (previous.roomId != current.roomId) return GameEffects()

        val sounds = mutableListOf<SoundEvent>()
        val captures = mutableListOf<CaptureInfo>()

        // Dice: a value newly appeared for this turn.
        if (current.diceValue != null && current.diceValue != previous.diceValue) sounds += SoundEvent.DICE_ROLL

        var moved = false
        var reachedHome = false
        for (currentPlayer in current.players) {
            val before = previous.players.firstOrNull { it.userId == currentPlayer.userId } ?: continue
            for (token in currentPlayer.tokens) {
                val old = before.tokens.firstOrNull { it.id == token.id } ?: continue
                if (old.position == token.position) continue
                when {
                    token.position == Token.BASE && old.position in 1..51 ->
                        captures += CaptureInfo(currentPlayer.color, token.id, old.position)
                    token.position == Token.FINISHED -> { reachedHome = true; moved = true }
                    else -> moved = true
                }
            }
        }

        when {
            captures.isNotEmpty() -> sounds += SoundEvent.CAPTURE
            reachedHome -> sounds += SoundEvent.TOKEN_HOME
            moved -> sounds += SoundEvent.TOKEN_MOVE
        }

        val turnChanged = current.currentPlayerIndex != previous.currentPlayerIndex
        if (turnChanged && localPlayerId != null &&
            current.status == GameStatus.IN_PROGRESS &&
            current.currentPlayer.userId == localPlayerId
        ) sounds += SoundEvent.YOUR_TURN

        var winner: String? = null
        if (current.status == GameStatus.FINISHED && previous.status != GameStatus.FINISHED) {
            sounds += SoundEvent.WIN
            winner = current.winnerId
        }
        return GameEffects(sounds, captures, winner)
    }
}
