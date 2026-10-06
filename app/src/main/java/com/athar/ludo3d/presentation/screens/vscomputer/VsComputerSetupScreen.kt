package com.athar.ludo3d.presentation.screens.vscomputer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.ludo3d.domain.engine.AIDifficulty
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor

// Added by Athar
// Section 11: "Vs Computer" with Easy/Medium/Hard difficulty. The human is
// always RED; AI opponents fill the remaining colors (same ordering as local
// pass-and-play) and are flagged isAI = true so GameViewModel drives their
// turns via LudoAIEngine automatically.
private val colorsForTotal: Map<Int, List<PlayerColor>> = mapOf(
    2 to listOf(PlayerColor.RED, PlayerColor.YELLOW),
    3 to listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW),
    4 to listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
)

@Composable
fun VsComputerSetupScreen(onStart: (List<Player>, AIDifficulty) -> Unit) {
    var totalPlayers by remember { mutableIntStateOf(2) } // you + 1 AI by default
    var difficulty by remember { mutableStateOf(AIDifficulty.MEDIUM) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Vs Computer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Play against a local AI opponent — works fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Text("Opponents", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(2, 3, 4).forEach { count ->
                SelectableChip(
                    label = "${count - 1} AI",
                    selected = totalPlayers == count,
                    onClick = { totalPlayers = count }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Difficulty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AIDifficulty.entries.forEach { level ->
                SelectableChip(
                    label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = difficulty == level,
                    onClick = { difficulty = level }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                val colors = colorsForTotal.getValue(totalPlayers)
                val players = colors.mapIndexed { index, color ->
                    if (index == 0) {
                        Player(userId = "you", name = "You", color = color, isAI = false)
                    } else {
                        Player(userId = "ai-$index", name = "Bot ${index}", color = color, isAI = true)
                    }
                }
                onStart(players, difficulty)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Game")
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}
