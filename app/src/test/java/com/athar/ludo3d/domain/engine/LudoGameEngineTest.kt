package com.athar.ludo3d.domain.engine

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Added by Athar
class LudoGameEngineTest {

    private fun twoPlayerState(status: GameStatus = GameStatus.IN_PROGRESS) = GameState(
        roomId = "test-room",
        players = listOf(
            Player(userId = "p1", name = "Athar", color = PlayerColor.RED),
            Player(userId = "p2", name = "Ali", color = PlayerColor.GREEN)
        ),
        status = status
    )

    private fun fixedDiceEngine(value: Int, state: GameState = twoPlayerState()) =
        LudoGameEngine(state, random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(from: Int, until: Int): Int = value
        })

    @Test
    fun `dice generation stays within 1 to 6`() {
        val engine = LudoGameEngine(twoPlayerState(), random = Random(42))
        repeat(200) {
            val fresh = LudoGameEngine(twoPlayerState(), random = Random(it))
            val value = fresh.rollDice()
            assertTrue(value in 1..6)
        }
        assertTrue(engine.rollDice() in 1..6)
    }

    @Test
    fun `cannot leave base without a six`() {
        val engine = fixedDiceEngine(4)
        engine.rollDice()
        val moves = engine.getValidMoves()
        assertTrue("no token should be movable with all tokens in base and dice=4", moves.isEmpty())
    }

    @Test
    fun `six allows leaving base`() {
        val engine = fixedDiceEngine(6)
        engine.rollDice()
        val moves = engine.getValidMoves()
        assertEquals(4, moves.size) // all 4 tokens can leave base
        assertEquals(1, moves.first().toPosition)
    }

    @Test
    fun `moving a token updates its position`() {
        val engine = fixedDiceEngine(6)
        engine.rollDice()
        val result = engine.moveToken("p1", 0)
        assertEquals(1, result.toPosition)
        assertEquals(1, engine.state.players[0].tokens[0].position)
    }

    @Test
    fun `six grants an extra turn - same player stays current`() {
        val engine = fixedDiceEngine(6)
        engine.rollDice()
        engine.moveToken("p1", 0)
        assertEquals(0, engine.state.currentPlayerIndex) // still p1's turn
        assertEquals(null, engine.state.diceValue)
    }

    @Test
    fun `non-six move without capture passes turn to next player`() {
        // Set up p1's token already on the path so a non-6 roll is legal.
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(tokens = listOf(Token(0, position = 5), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(p1, s.players[1]))
        }
        val engine = fixedDiceEngine(3, state)
        engine.rollDice()
        engine.moveToken("p1", 0)
        assertEquals(1, engine.state.currentPlayerIndex) // turn passed to p2
    }

    @Test
    fun `three consecutive sixes forfeits the turn`() {
        var state = twoPlayerState()
        val engine = LudoGameEngine(state, random = Random(0))

        // Manually drive three sixes using a controllable engine per roll.
        fun rollSix(currentState: GameState): GameState {
            val e = LudoGameEngine(currentState, random = object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
                override fun nextInt(from: Int, until: Int): Int = 6
            })
            e.rollDice()
            e.moveToken("p1", 0) // first available token; base tokens release on 6
            return e.state
        }

        var s = state
        s = rollSix(s) // 1st six: token0 leaves base, extra turn, consecutiveSixes=1
        assertEquals(0, s.currentPlayerIndex)
        s = rollSix(s) // 2nd six: token1 leaves base, extra turn, consecutiveSixes=2
        assertEquals(0, s.currentPlayerIndex)
        s = rollSix(s) // 3rd six: forfeits, turn passes to p2
        assertEquals(1, s.currentPlayerIndex)
        assertEquals(0, s.consecutiveSixes)
    }

    @Test
    fun `landing on opponent captures it back to base`() {
        // p1 (RED, offset 0) token at local pos 10 -> global square 9
        // p2 (GREEN, offset 13) token positioned so its global square is also 9:
        // global = (13 + localPos - 1) % 52 = 9  =>  localPos = -3 mod 52 -> use localPos = 49
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(tokens = listOf(Token(0, position = 9), Token(1), Token(2), Token(3)))
            val p2 = s.players[1].copy(tokens = listOf(Token(0, position = 49), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(p1, p2))
        }
        val engine = fixedDiceEngine(1, state) // p1 moves 9 -> 10, global square 9 (not safe)
        engine.rollDice()
        val result = engine.moveToken("p1", 0)
        assertEquals(1, result.capturedTokens.size)
        assertEquals("p2", result.capturedTokens.first().playerId)
        assertEquals(Token.BASE, engine.state.players[1].tokens[0].position)
    }

    @Test
    fun `safe square blocks capture`() {
        // Global square 8 is a star safe square.
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(tokens = listOf(Token(0, position = 8), Token(1), Token(2), Token(3)))
            // p2 GREEN offset 13: localPos such that global = 8 => (13+l-1)%52=8 => l = 48
            val p2 = s.players[1].copy(tokens = listOf(Token(0, position = 48), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(p1, p2))
        }
        val engine = fixedDiceEngine(1, state)
        engine.rollDice()
        val result = engine.moveToken("p1", 0)
        assertTrue(result.capturedTokens.isEmpty())
    }

    @Test
    fun `token cannot overshoot final home`() {
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(tokens = listOf(Token(0, position = 56), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(p1, s.players[1]))
        }
        val engine = fixedDiceEngine(5, state) // 56 + 5 = 61 > 58, illegal
        engine.rollDice()
        val moves = engine.getValidMoves()
        assertTrue(moves.none { it.tokenId == 0 })
    }

    @Test
    fun `reaching exactly 58 finishes the token`() {
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(tokens = listOf(Token(0, position = 52), Token(1), Token(2), Token(3)))
            s.copy(players = listOf(p1, s.players[1]))
        }
        val engine = fixedDiceEngine(6, state)
        engine.rollDice()
        val result = engine.moveToken("p1", 0)
        assertEquals(Token.FINISHED, result.toPosition)
        assertTrue(engine.state.players[0].tokens[0].isFinished)
    }

    @Test
    fun `winner detected when all four tokens finished`() {
        val state = twoPlayerState().let { s ->
            val p1 = s.players[0].copy(
                tokens = listOf(Token(0, 52), Token(1, Token.FINISHED), Token(2, Token.FINISHED), Token(3, Token.FINISHED))
            )
            s.copy(players = listOf(p1, s.players[1]))
        }
        val engine = fixedDiceEngine(6, state)
        engine.rollDice()
        val result = engine.moveToken("p1", 0)
        assertEquals("p1", result.winnerId)
        assertTrue(engine.hasPlayerWon("p1"))
        assertEquals(GameStatus.FINISHED, engine.state.status)
    }

    @Test
    fun `moving out of turn is rejected`() {
        val engine = fixedDiceEngine(6)
        engine.rollDice()
        try {
            engine.moveToken("p2", 0)
            assertTrue("expected exception for out-of-turn move", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("turn"))
        }
    }

    @Test
    fun `isSafeCell reports classic star and start squares`() {
        val engine = fixedDiceEngine(1)
        assertTrue(engine.isSafeCell(0))
        assertTrue(engine.isSafeCell(8))
        assertTrue(engine.isSafeCell(13))
        assertFalse(engine.isSafeCell(5))
    }
}
