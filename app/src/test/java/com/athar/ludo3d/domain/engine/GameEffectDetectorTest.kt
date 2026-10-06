package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Added by Athar
// Mirrors the 18-check standalone runner that was compiled and run against the real classes.
class GameEffectDetectorTest {
    private fun state(
        p1: List<Token> = List(4) { Token(it) }, p2: List<Token> = List(4) { Token(it) },
        idx: Int = 0, dice: Int? = null, status: GameStatus = GameStatus.IN_PROGRESS, winner: String? = null
    ) = GameState(
        roomId = "r", status = status, currentPlayerIndex = idx, diceValue = dice, winnerId = winner,
        players = listOf(Player("a", "A", PlayerColor.RED, tokens = p1), Player("b", "B", PlayerColor.GREEN, tokens = p2))
    )

    @Test fun `no effects on first emission or identical state`() {
        assertTrue(GameEffectDetector.detect(null, state(), "a").isEmpty)
        assertTrue(GameEffectDetector.detect(state(), state(), "a").isEmpty)
    }

    @Test fun `new dice value triggers dice roll`() {
        assertEquals(listOf(SoundEvent.DICE_ROLL), GameEffectDetector.detect(state(), state(dice = 4), "a").sounds)
    }

    @Test fun `capture wins over plain move and reports the victim`() {
        val before = state(p1 = listOf(Token(0, 9), Token(1), Token(2), Token(3)), p2 = listOf(Token(0, 10), Token(1), Token(2), Token(3)), dice = 1)
        val after = state(p1 = listOf(Token(0, 10), Token(1), Token(2), Token(3)))
        val e = GameEffectDetector.detect(before, after, "a")
        assertTrue(SoundEvent.CAPTURE in e.sounds && SoundEvent.TOKEN_MOVE !in e.sounds)
        assertEquals(listOf(CaptureInfo(PlayerColor.GREEN, 0, 10)), e.captures)
    }

    @Test fun `your turn only fires for the local player`() {
        val toB = state(idx = 1)
        assertTrue(SoundEvent.YOUR_TURN in GameEffectDetector.detect(state(), toB, "b").sounds)
        assertTrue(SoundEvent.YOUR_TURN !in GameEffectDetector.detect(state(), toB, "a").sounds)
        assertTrue(SoundEvent.YOUR_TURN !in GameEffectDetector.detect(state(), toB, null).sounds)
    }

    @Test fun `win fires once`() {
        val fin = state(status = GameStatus.FINISHED, winner = "a")
        assertEquals("a", GameEffectDetector.detect(state(), fin, "a").winnerId)
        assertEquals(null, GameEffectDetector.detect(fin, fin.copy(diceValue = 2), "a").winnerId)
    }

    @Test fun `particles are deterministic and expire`() {
        val burst = ParticleSystem.burst(0.5f, 0.5f, 24, 4, Random(1))
        assertEquals(burst, ParticleSystem.burst(0.5f, 0.5f, 24, 4, Random(1)))
        var ps = burst
        repeat(200) { ps = ParticleSystem.step(ps, 0.016f) }
        assertTrue(ps.isEmpty())
    }
}
