package com.athar.ludo3d.presentation.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Added by Athar (Phase 10)
//
// Real instrumented Compose UI tests, not scaffolding — they exercise the
// actual GameScreenContent composable. This class was made deliberately
// Hilt-free in this same phase specifically so it could be tested with a
// plain createComposeRule(): see GameScreenContent.kt's comment on why sound
// is passed in rather than fetched via hiltViewModel() inside the composable.
//
// STATUS IN THIS REPO: written and reviewed against the real composable's
// signature, NOT executed — that needs a connected device/emulator or
// `./gradlew connectedAndroidTest`, neither available in the sandbox this
// was built in (no Android SDK/emulator image here at all, a harder
// limitation than the Firebase emulator's blocked download). Run it in
// Android Studio before trusting it.
@RunWith(AndroidJUnit4::class)
class GameScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun twoPlayerState(hasRolled: Boolean = false, diceValue: Int? = null) = GameState(
        roomId = "test",
        status = GameStatus.IN_PROGRESS,
        hasRolledThisTurn = hasRolled,
        diceValue = diceValue,
        players = listOf(
            Player(userId = "p1", name = "Athar", color = PlayerColor.RED),
            Player(userId = "p2", name = "Ali", color = PlayerColor.GREEN)
        )
    )

    @Test
    fun rollButton_isEnabled_whenCanRollAndNotYetRolled() {
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState(),
                statusMessage = "Your turn",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = true,
                rollButtonLabel = "ROLL",
                onRoll = {},
                onLeaveGame = {}
            )
        }
        composeRule.onNodeWithText("ROLL").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun rollButton_isDisabled_whenNotThisPlayersTurn() {
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState(),
                statusMessage = "Waiting for Ali...",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = false, // e.g. online, not this device's turn
                rollButtonLabel = "Waiting for Ali...",
                onRoll = {},
                onLeaveGame = {}
            )
        }
        composeRule.onNodeWithText("Waiting for Ali...").assertIsNotEnabled()
    }

    @Test
    fun rollButton_isDisabled_afterAlreadyRolledThisTurn() {
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState(hasRolled = true, diceValue = 4),
                statusMessage = "Choose a token",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = true, // it IS this player's turn, but they already rolled
                rollButtonLabel = "ROLL",
                onRoll = {},
                onLeaveGame = {}
            )
        }
        composeRule.onNodeWithText("ROLL").assertIsNotEnabled()
    }

    @Test
    fun tappingRoll_invokesCallbacksExactlyOnce() {
        var rollCount = 0
        var tapSoundCount = 0
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState(),
                statusMessage = "Your turn",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = true,
                rollButtonLabel = "ROLL",
                onRoll = { rollCount++ },
                onLeaveGame = {},
                onRollButtonTap = { tapSoundCount++ }
            )
        }
        composeRule.onNodeWithText("ROLL").performClick()
        assert(rollCount == 1) { "expected onRoll called once, was $rollCount" }
        assert(tapSoundCount == 1) { "expected onRollButtonTap called once, was $tapSoundCount" }
    }

    @Test
    fun winState_showsBackToHomeInsteadOfRoll() {
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState().copy(status = GameStatus.FINISHED, winnerId = "p1"),
                statusMessage = "Athar wins!",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = false,
                rollButtonLabel = "ROLL",
                onRoll = {},
                onLeaveGame = {}
            )
        }
        composeRule.onNodeWithText("Back to Home").assertIsDisplayed()
        composeRule.onNodeWithText("Athar wins!").assertIsDisplayed()
    }

    @Test
    fun turnTimer_showsSecondsWhenProvided() {
        composeRule.setContent {
            GameScreenContent(
                gameState = twoPlayerState(),
                statusMessage = "Your turn",
                isRolling = false,
                highlightedTokens = emptySet(),
                onTokenTap = { _, _ -> },
                canRoll = true,
                rollButtonLabel = "ROLL",
                onRoll = {},
                onLeaveGame = {},
                turnSecondsRemaining = 12
            )
        }
        composeRule.onNodeWithText("⏱ 12s").assertIsDisplayed()
    }
}
