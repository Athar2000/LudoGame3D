package com.athar.ludo3d.presentation.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athar.ludo3d.domain.engine.AIDifficulty
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.presentation.sound.SoundViewModel
import com.athar.ludo3d.presentation.sound.UiSound

// Added by Athar
// Local pass-and-play (Phase 2) and Vs Computer (Phase 7): the GameState
// lives entirely in-memory (GameViewModel wraps a local LudoGameEngine
// instance directly), so there's no turn timer and "canRoll" just means
// "it's not the AI's turn". See OnlineGameScreen.kt for the Firebase-backed
// counterpart that shares this exact same GameScreenContent UI. Sound is
// owned here (not inside GameScreenContent, see that file's comment) via
// its own hiltViewModel() — this is a normal top-level screen composable, so
// that's the correct place for a Hilt dependency to live.
@Composable
fun GameScreen(
    players: List<Player>,
    onLeaveGame: () -> Unit,
    aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM
) {
    val viewModel: GameViewModel = viewModel(factory = GameViewModelFactory(players, aiDifficulty))
    val state by viewModel.uiState.collectAsState()
    val gameState = state.gameState ?: return

    val soundViewModel: SoundViewModel = hiltViewModel()
    val soundOn by soundViewModel.soundEnabled.collectAsState()

    GameScreenContent(
        gameState = gameState,
        statusMessage = state.statusMessage,
        isRolling = state.isRolling,
        highlightedTokens = viewModel.highlightedTokens,
        onTokenTap = viewModel::selectToken,
        canRoll = !gameState.currentPlayer.isAI,
        rollButtonLabel = if (gameState.currentPlayer.isAI) "${gameState.currentPlayer.name} is playing..." else "ROLL",
        onRoll = viewModel::rollDice,
        onLeaveGame = onLeaveGame,
        turnSecondsRemaining = null, // no turn timer in local/AI play
        soundOn = soundOn,
        onToggleSound = soundViewModel::toggle,
        onPlaySounds = { events -> events.forEach { soundViewModel.play(it) } },
        onRollButtonTap = { soundViewModel.play(UiSound.BUTTON_TAP) }
    )
}
