package com.athar.ludo3d.presentation.screens.localsetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.presentation.screens.game.board.playerColorToUi

// Added by Athar
// Section 2: "Local practice mode" — same-device pass-and-play using the real
// LudoGameEngine, no network at all. 2-player games use RED/YELLOW (opposite
// corners) so the board reads correctly; 3/4-player games fill colors in
// order starting from RED.
private val colorsForCount: Map<Int, List<PlayerColor>> = mapOf(
    2 to listOf(PlayerColor.RED, PlayerColor.YELLOW),
    3 to listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW),
    4 to listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
)

@Composable
fun LocalGameSetupScreen(onStart: (List<Player>) -> Unit) {
    var playerCount by remember { mutableIntStateOf(4) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Local Multiplayer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pass the phone between players. No internet required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Text("Number of players", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(2, 3, 4).forEach { count ->
                val selected = playerCount == count
                Card(
                    onClick = { playerCount = count },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "$count",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Colors", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        colorsForCount.getValue(playerCount).forEachIndexed { index, color ->
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(playerColorToUi(color))
                )
                Spacer(Modifier.width(12.dp))
                Text("Player ${index + 1}")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                val colors = colorsForCount.getValue(playerCount)
                val players = colors.mapIndexed { index, color ->
                    Player(
                        userId = "local-p${index + 1}",
                        name = "Player ${index + 1}",
                        color = color
                    )
                }
                onStart(players)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Game")
        }
    }
}
