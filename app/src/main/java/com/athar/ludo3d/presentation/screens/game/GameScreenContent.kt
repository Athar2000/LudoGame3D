package com.athar.ludo3d.presentation.screens.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.athar.ludo3d.domain.engine.CaptureInfo
import com.athar.ludo3d.domain.engine.GameEffectDetector
import com.athar.ludo3d.domain.engine.SoundEvent
import com.athar.ludo3d.presentation.screens.game.board.ParticleOverlay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.presentation.screens.game.board.GameBoardCanvas
import com.athar.ludo3d.presentation.screens.game.board.playerColorToUi

// Added by Athar
// The actual board/dice/player-row UI (section 10), extracted so both the
// local/AI GameScreen (Phase 2/7) and the online OnlineGameScreen (Phase 5)
// render pixel-identical gameplay — the only thing that differs between them
// is WHERE the GameState comes from (an in-memory engine vs. a Firebase
// listener) and WHO is allowed to tap Roll right now (canRoll).
@Composable
fun GameScreenContent(
    gameState: GameState,
    statusMessage: String,
    isRolling: Boolean,
    highlightedTokens: Set<Pair<String, Int>>,
    onTokenTap: (playerId: String, tokenId: Int) -> Unit,
    canRoll: Boolean,
    rollButtonLabel: String,
    onRoll: () -> Unit,
    onLeaveGame: () -> Unit,
    turnSecondsRemaining: Int? = null,
    onChatToggle: (() -> Unit)? = null, // null in local/AI play, which has no chat (no one else to talk to)
    localPlayerId: String? = null, // this device's user id online (enables YOUR_TURN sound); null in pass-and-play
    // Sound is hoisted up rather than fetched via hiltViewModel() inside this
    // composable on purpose: GameScreenContent stays a plain, stateless
    // function of its parameters (see the class doc above), which is what
    // makes it directly unit-testable with createComposeRule() and no Hilt
    // test harness — see GameScreenContentTest. GameScreen/OnlineGameScreen
    // each own a SoundViewModel and pass these three through.
    soundOn: Boolean = true,
    onToggleSound: () -> Unit = {},
    onPlaySounds: (List<SoundEvent>) -> Unit = {},
    onRollButtonTap: () -> Unit = {} // distinct UI click sound, separate from the game-state-driven SoundEvents above
) {

    // Phase 9: diff each incoming GameState against the previous one to decide which
    // sounds/particles to fire. Same code path for local, AI and online games.
    var previousState by remember { mutableStateOf<GameState?>(null) }
    var pendingCaptures by remember { mutableStateOf(emptyList<CaptureInfo>()) }
    var celebrate by remember { mutableStateOf(false) }
    var effectVersion by remember { mutableStateOf(0) }
    LaunchedEffect(gameState) {
        val effects = GameEffectDetector.detect(previousState, gameState, localPlayerId)
        previousState = gameState
        if (!effects.isEmpty) {
            onPlaySounds(effects.sounds)
            pendingCaptures = effects.captures
            celebrate = effects.winnerId != null
            if (effects.captures.isNotEmpty() || celebrate) effectVersion++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- Top bar: leave + sound toggle ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLeaveGame) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Leave game")
            }
            if (turnSecondsRemaining != null && gameState.status == GameStatus.IN_PROGRESS) {
                Text(
                    "⏱ ${turnSecondsRemaining}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (turnSecondsRemaining <= 5) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleSound) {
                Icon(
                    if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Toggle sound"
                )
            }
            if (onChatToggle != null) {
                IconButton(onClick = onChatToggle) {
                    Icon(Icons.Filled.Chat, contentDescription = "Chat")
                }
            }
        }

        // --- Player row: name, color dot, tokens-still-home count, turn highlight ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            gameState.players.forEach { player ->
                PlayerBadge(player = player, isCurrentTurn = player.userId == gameState.currentPlayer.userId)
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
            GameBoardCanvas(
                players = gameState.players,
                highlightedTokenIds = highlightedTokens,
                onTokenTap = onTokenTap
            )
            // Overlay is a plain Canvas with no pointer handling, so taps still reach the board underneath.
            ParticleOverlay(
                captures = pendingCaptures,
                winnerCelebration = celebrate,
                effectVersion = effectVersion,
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiceView(value = gameState.diceValue, isRolling = isRolling)
        }

        Spacer(Modifier.height(16.dp))

        if (gameState.status == GameStatus.FINISHED) {
            Button(onClick = onLeaveGame, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Home")
            }
        } else {
            Button(
                onClick = {
                    onRollButtonTap()
                    onRoll()
                },
                enabled = canRoll && !gameState.hasRolledThisTurn && !isRolling,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRolling) "Rolling..." else rollButtonLabel)
            }
        }
    }
}

@Composable
private fun PlayerBadge(player: Player, isCurrentTurn: Boolean) {
    val homeCount = player.tokens.count { it.isInBase }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (isCurrentTurn) 44.dp else 38.dp)
                .clip(CircleShape)
                .background(playerColorToUi(player.color)),
            contentAlignment = Alignment.Center
        ) {
            Text(player.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(player.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("$homeCount at home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isCurrentTurn) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun DiceView(value: Int?, isRolling: Boolean) {
    val transition = rememberInfiniteTransition(label = "dice")
    val rollAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing)),
        label = "roll-angle"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .rotate(if (isRolling) rollAngle else 0f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isRolling) "" else (value?.toString() ?: "🎲"),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
