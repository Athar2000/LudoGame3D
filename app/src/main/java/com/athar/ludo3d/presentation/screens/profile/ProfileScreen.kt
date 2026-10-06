package com.athar.ludo3d.presentation.screens.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athar.ludo3d.presentation.theme.PrimaryBlue
import com.athar.ludo3d.presentation.theme.RewardOrange

// Added by Athar
// Section 18: avatar, name, level, XP, games, wins, losses, win rate,
// achievements, favorite color. Achievements here are computed client-side
// from the profile's own stats (wins/gamesPlayed) rather than a separate
// stored achievements list — "First Win" and "Veteran" are simple derived
// facts, not something that needs its own database writes and the cheating
// surface that would come with a client being able to write "unlocked".
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()

    val current = profile
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(current.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(current.name, style = MaterialTheme.typography.headlineSmall)
                Text("Level ${current.level} · ${current.xp} XP", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        StatsGrid(
            wins = current.wins,
            losses = current.losses,
            gamesPlayed = current.gamesPlayed,
            winRate = current.winRatePercent,
            coins = current.coins
        )

        Spacer(Modifier.height(20.dp))
        Text("Achievements", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        AchievementRow("First Win", "Win your first game", unlocked = current.wins >= 1)
        AchievementRow("Veteran", "Play 10 games", unlocked = current.gamesPlayed >= 10)
        AchievementRow("Champion", "Win 10 games", unlocked = current.wins >= 10)

        current.favoriteColor?.let {
            Spacer(Modifier.height(20.dp))
            Text("Favorite color: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}")
        }
    }
}

@Composable
private fun StatsGrid(wins: Int, losses: Int, gamesPlayed: Int, winRate: Int, coins: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Wins", "$wins", Modifier.weight(1f))
        StatCard("Losses", "$losses", Modifier.weight(1f))
        StatCard("Win Rate", "$winRate%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Games Played", "$gamesPlayed", Modifier.weight(1f))
        StatCard("Coins", "🪙 $coins", Modifier.weight(1f), accent = RewardOrange)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementRow(title: String, description: String, unlocked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(if (unlocked) RewardOrange else MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
