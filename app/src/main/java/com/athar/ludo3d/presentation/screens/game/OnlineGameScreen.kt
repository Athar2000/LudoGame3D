package com.athar.ludo3d.presentation.screens.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.athar.ludo3d.presentation.screens.chat.ChatPanel
import com.athar.ludo3d.presentation.sound.SoundViewModel
import com.athar.ludo3d.presentation.sound.UiSound

// Added by Athar
// Section 5: every phone in the room collects the SAME Firebase gameState
// listener (via OnlineGameViewModel -> GameSyncRepository.observeGameState),
// so when Player A rolls the dice or moves a token, Players B/C/D's screens
// update from that listener firing — not from any direct phone-to-phone
// connection, and not from anything this screen computes locally.
@Composable
fun OnlineGameScreen(
    onLeaveGame: () -> Unit,
    viewModel: OnlineGameViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val turnSeconds by viewModel.turnSecondsRemaining.collectAsState()
    val gameState = state.gameState
    var chatVisible by remember { mutableStateOf(false) }

    if (gameState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isMyTurn = gameState.currentPlayer.userId == viewModel.currentUserId
    val soundViewModel: SoundViewModel = hiltViewModel()
    val soundOn by soundViewModel.soundEnabled.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        GameScreenContent(
            gameState = gameState,
            statusMessage = state.statusMessage,
            isRolling = state.isRolling,
            highlightedTokens = viewModel.highlightedTokens,
            onTokenTap = viewModel::selectToken,
            canRoll = isMyTurn,
            rollButtonLabel = if (isMyTurn) "ROLL" else "Waiting for ${gameState.currentPlayer.name}...",
            onRoll = viewModel::rollDice,
            onLeaveGame = {
                viewModel.leaveGame()
                onLeaveGame()
            },
            turnSecondsRemaining = turnSeconds,
            onChatToggle = { chatVisible = !chatVisible },
            localPlayerId = viewModel.currentUserId,
            soundOn = soundOn,
            onToggleSound = soundViewModel::toggle,
            onPlaySounds = { events -> events.forEach { soundViewModel.play(it) } },
            onRollButtonTap = { soundViewModel.play(UiSound.BUTTON_TAP) }
        )

        AnimatedVisibility(
            visible = chatVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            ChatPanel(modifier = Modifier.fillMaxWidth())
        }
    }
}
