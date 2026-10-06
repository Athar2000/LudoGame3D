package com.athar.ludo3d.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.athar.ludo3d.presentation.navigation.Screen
import com.athar.ludo3d.presentation.theme.PrimaryBlue
import com.athar.ludo3d.presentation.theme.RewardOrange
import com.athar.ludo3d.presentation.theme.SpecialPurple
import com.athar.ludo3d.presentation.theme.SuccessGreen

// Added by Athar
private data class MenuItem(val label: String, val route: String, val accent: Color)

private val menuItems = listOf(
    MenuItem("Play Online", Screen.PlayOnline.route, PrimaryBlue),
    MenuItem("Play With Friends", Screen.PrivateRoomEntry.route, SuccessGreen),
    MenuItem("Vs Computer", Screen.VsComputer.route, SpecialPurple),
    MenuItem("Local Multiplayer", Screen.LocalMultiplayer.route, PrimaryBlue),
    MenuItem("Leaderboard", Screen.Leaderboard.route, RewardOrange),
    MenuItem("Daily Reward", Screen.DailyReward.route, RewardOrange),
    MenuItem("Shop", Screen.Shop.route, SpecialPurple),
    MenuItem("Profile", Screen.Profile.route, PrimaryBlue),
    MenuItem("Settings", Screen.Settings.route, SuccessGreen)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        PlayerHeader(
            name = state.playerName,
            level = state.level,
            coins = state.coins
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(menuItems) { item ->
                MenuTile(item = item, onClick = { onNavigate(item.route) })
            }
        }
    }
}

@Composable
private fun PlayerHeader(name: String, level: Int, coins: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text("Level $level", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = RewardOrange.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("🪙 $coins", color = RewardOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MenuTile(item: MenuItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.accent)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            Text(item.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
