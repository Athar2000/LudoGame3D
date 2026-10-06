package com.athar.ludo3d.presentation.screens.localsetup

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Added by Athar (Phase 10)
// Same "Hilt-free, so plainly testable" reasoning as GameScreenContentTest.
// STATUS: written and reviewed against the real composable, not executed —
// see that file's header for why (no Android SDK/emulator in this sandbox).
@RunWith(AndroidJUnit4::class)
class LocalGameSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultStart_produces4PlayersInRedGreenYellowBlueOrder() {
        var startedWith: List<Player>? = null
        composeRule.setContent {
            LocalGameSetupScreen(onStart = { players -> startedWith = players })
        }
        composeRule.onNodeWithText("Start Game").performClick()

        val players = startedWith ?: error("onStart was never called")
        assertEquals(4, players.size)
        assertEquals(
            listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE),
            players.map { it.color }
        )
        assertEquals(listOf("local-p1", "local-p2", "local-p3", "local-p4"), players.map { it.userId })
    }

    @Test
    fun selecting2Players_usesOppositeRedAndYellow() {
        var startedWith: List<Player>? = null
        composeRule.setContent {
            LocalGameSetupScreen(onStart = { players -> startedWith = players })
        }
        // The count selector card shows "2"; tap it before starting.
        composeRule.onNodeWithText("2").performClick()
        composeRule.onNodeWithText("Start Game").performClick()

        val players = startedWith ?: error("onStart was never called")
        assertEquals(2, players.size)
        assertEquals(listOf(PlayerColor.RED, PlayerColor.YELLOW), players.map { it.color })
    }

    @Test
    fun selecting3Players_showsThreeColorRows() {
        composeRule.setContent {
            LocalGameSetupScreen(onStart = {})
        }
        composeRule.onNodeWithText("3").performClick()
        composeRule.onNodeWithText("Player 1").assertExists()
        composeRule.onNodeWithText("Player 2").assertExists()
        composeRule.onNodeWithText("Player 3").assertExists()
    }
}
