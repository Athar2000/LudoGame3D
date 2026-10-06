package com.athar.ludo3d.presentation.screens.leaderboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.athar.ludo3d.domain.model.LeaderboardEntry
import com.athar.ludo3d.domain.model.LeaderboardScope
import com.athar.ludo3d.presentation.theme.RewardOrange

// Added by Athar
// Section 16.
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val tabs = LeaderboardScope.entries.toList()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Leaderboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        TabRow(selectedTabIndex = tabs.indexOf(state.scope)) {
            tabs.forEach { scope ->
                Tab(
                    selected = state.scope == scope,
                    onClick = { viewModel.selectScope(scope) },
                    text = { Text(if (scope == LeaderboardScope.GLOBAL) "Global" else "Friends") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.scope == LeaderboardScope.FRIENDS) "Friends aren't available yet" else "No players yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn {
                itemsIndexed(state.entries) { index, entry -> LeaderboardRow(rank = index + 1, entry = entry) }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(
                if (rank <= 3) RewardOrange else MaterialTheme.colorScheme.surfaceVariant
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelLarge,
                color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold)
            Text(
                "Level ${entry.level} · ${entry.gamesPlayed} games · ${entry.winRatePercent}% win rate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${entry.wins} wins", fontWeight = FontWeight.Bold)
    }
}
