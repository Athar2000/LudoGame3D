package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Added by Athar
// These fixtures were validated against the compiled engine via a standalone
// runner before being ported here (see project README) — the exact square
// math (global = (color.startOffset + localPos - 1) % 52) is easy to get
// subtly wrong by hand, so every position below was chosen to land on a
// specific, deliberately-checked global square.
class LudoAIEngineTest {

    private fun baseState() = GameState(
        roomId = "ai-test",
        players = listOf(
            Player(userId = "human", name = "You", color = PlayerColor.RED),
            Player(userId = "ai", name = "Bot", color = PlayerColor.GREEN)
        ),
        status = GameStatus.IN_PROGRESS
    )

    @Test
    fun `HARD AI prefers a capturing move over a non-capturing move`() {
        // AI token0: local 1 + dice 2 -> global 15 (not safe). Human token at local 16 -> global 15: capturable.
        // AI token1: local 6 + dice 2 -> global 20 (no capture, not safe).
        val s = baseState().let { s ->
            val ai = s.players[1].copy(tokens = listOf(Token(0, 1), Token(1, 6), Token(2), Token(3)))
            val human = s.players[0].copy(tokens = listOf(Token(0, 16), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(human, ai), currentPlayerIndex = 1, diceValue = 2, hasRolledThisTurn = true)
        }
        val engine = LudoGameEngine(s)
        val moves = engine.getValidMoves()
        assertEquals(2, moves.size)
        assertTrue(moves.first { it.tokenId == 0 }.capturesOpponent)

        val chosen = LudoAIEngine(AIDifficulty.HARD, Random(1)).chooseBestMove(engine.state, moves)
        assertTrue(chosen.capturesOpponent)
        assertEquals(0, chosen.tokenId)
    }

    @Test
    fun `EASY AI varies its choice across random seeds`() {
        val s = baseState().let { s ->
            val ai = s.players[1].copy(tokens = listOf(Token(0, 5), Token(1, 15), Token(2), Token(3)))
            s.copy(players = listOf(s.players[0], ai), currentPlayerIndex = 1, diceValue = 3, hasRolledThisTurn = true)
        }
        val engine = LudoGameEngine(s)
        val moves = engine.getValidMoves()
        assertEquals(2, moves.size)

        val choices = (0 until 40).map {
            LudoAIEngine(AIDifficulty.EASY, Random(it)).chooseBestMove(engine.state, moves).tokenId
        }.toSet()
        assertEquals(2, choices.size)
    }

    @Test
    fun `HARD AI avoids a token exposed to an opponent threat when a safer move exists`() {
        // AI token0: local 10 + dice 2 -> global 24 (not safe). Human token at local 22 -> global 21: 3 squares
        // behind 24, a live threat (reachable with a single 1..6 roll next turn).
        // AI token1: local 30 + dice 2 -> global 44, far from the threat.
        val s = baseState().let { s ->
            val ai = s.players[1].copy(tokens = listOf(Token(0, 10), Token(1, 30), Token(2), Token(3)))
            val human = s.players[0].copy(tokens = listOf(Token(0, 22), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(human, ai), currentPlayerIndex = 1, diceValue = 2, hasRolledThisTurn = true)
        }
        val engine = LudoGameEngine(s)
        val moves = engine.getValidMoves()
        assertEquals(2, moves.size)

        val riskyMove = moves.first { it.tokenId == 0 }
        val riskScore = LudoAIEngine(AIDifficulty.HARD).evaluateRisk(
            engine.state, engine.state.currentPlayer,
            (engine.state.currentPlayer.color.startOffset + riskyMove.toPosition - 1).mod(52)
        )
        assertTrue(riskScore > 0.0)

        val chosen = LudoAIEngine(AIDifficulty.HARD, Random(2)).chooseBestMove(engine.state, moves)
        assertNotEquals(0, chosen.tokenId)
    }

    @Test
    fun `AI always returns a move from the legal set across many random boards`() {
        repeat(30) { seed ->
            val rnd = Random(seed)
            val aiTokens = List(4) { Token(it, if (rnd.nextBoolean()) rnd.nextInt(1, 52) else 0) }
            val s = baseState().let { s ->
                val ai = s.players[1].copy(tokens = aiTokens)
                s.copy(
                    players = listOf(s.players[0], ai),
                    currentPlayerIndex = 1,
                    diceValue = rnd.nextInt(1, 7),
                    hasRolledThisTurn = true
                )
            }
            val engine = LudoGameEngine(s)
            val moves = engine.getValidMoves()
            if (moves.isNotEmpty()) {
                val chosen = LudoAIEngine(AIDifficulty.HARD, rnd).chooseBestMove(engine.state, moves)
                assertTrue(chosen in moves)
            }
        }
    }

    @Test
    fun `chooseBestMove throws on an empty move list`() {
        val engine = LudoGameEngine(baseState())
        try {
            LudoAIEngine(AIDifficulty.MEDIUM).chooseBestMove(engine.state, emptyList())
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
