package com.athar.ludo3d.presentation.screens.room

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athar.ludo3d.domain.model.RoomPlayer
import com.athar.ludo3d.presentation.screens.game.board.playerColorToUi
import com.athar.ludo3d.presentation.theme.SuccessGreen

// Added by Athar
// Matches the spec's lobby mockup:
//   ROOM: ATHAR4587
//   Player 1 - Athar     READY
//   Player 2 - Ali       READY
//               START GAME
// "Only the room host can start the game" is enforced both here (button only
// shown/enabled for the host) AND server-side in FirebaseRoomRepository.startGame
// (throws RoomError.NotHost if a non-host UID calls it) — the UI restriction
// alone would not be trustworthy, section 6.
@Composable
fun RoomLobbyScreen(
    onLeave: () -> Unit,
    onGameStarted: (roomId: String) -> Unit,
    viewModel: RoomLobbyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.hasLeft) {
        if (state.hasLeft) onLeave()
    }
    LaunchedEffect(state.gameHasStarted) {
        state.room?.takeIf { state.gameHasStarted }?.let { onGameStarted(it.roomId) }
    }

    val room = state.room

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::leaveRoom) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Leave room")
            }
            Text("Room Lobby", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(48.dp))
        }

        if (room == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.errorMessage != null) {
                    Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ROOM CODE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(room.roomId, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(room.roomId)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy room code")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "${room.players.size} / ${room.maxPlayers} players",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(room.players) { player ->
                PlayerRow(player, isHost = player.userId == room.hostId)
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        val myPlayer = room.players.firstOrNull { it.userId == state.currentUserId }
        if (state.isHost) {
            Button(
                onClick = viewModel::startGame,
                enabled = room.canStart && !state.isStarting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isStarting) "Starting..." else "START GAME")
            }
            if (!room.canStart) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Need at least 2 players, all marked ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedButton(
                onClick = viewModel::toggleReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (myPlayer?.ready == true) "Not Ready" else "Ready")
            }
        }
    }
}

@Composable
private fun PlayerRow(player: RoomPlayer, isHost: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(player.color?.let { playerColorToUi(it) } ?: Color.Gray)
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    player.name + if (isHost) " (Host)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isHost) FontWeight.Bold else FontWeight.Normal
                )
                if (!player.connected) {
                    Text("Disconnected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            if (player.ready) "READY" else "NOT READY",
            color = if (player.ready) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}
