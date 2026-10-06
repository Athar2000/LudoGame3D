package com.athar.ludo3d.presentation.screens.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athar.ludo3d.domain.engine.DAILY_REWARDS
import com.athar.ludo3d.presentation.theme.RewardOrange
import com.athar.ludo3d.presentation.theme.SuccessGreen

// Added by Athar
// Section 19: 7-day escalating reward grid. The "next day" highlighted here
// is a DISPLAY estimate from the stored streak day; if the player missed
// more than the grace window the server resets them to day 1 on claim, and
// the result message shows the day actually granted. This screen has no
// authority over whether a claim succeeds — claim() just asks the server.
@Composable
fun DailyRewardScreen(viewModel: DailyRewardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val nextDay = if (state.currentStreakDay == 0 || state.currentStreakDay >= 7) 1 else state.currentStreakDay + 1

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Daily Reward", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Come back every day for a bigger reward.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(DAILY_REWARDS) { reward ->
                val isToday = state.canClaimToday && reward.day == nextDay
                val isClaimed = reward.day <= state.currentStreakDay && !isToday
                DayCard(
                    day = reward.day,
                    coins = reward.coins,
                    isPremium = reward.isPremium,
                    isToday = isToday,
                    isClaimed = isClaimed
                )
            }
        }

        state.lastResultMessage?.let {
            Text(
                it,
                color = SuccessGreen,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Button(
            onClick = viewModel::claim,
            enabled = state.canClaimToday && !state.isClaiming,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    state.isClaiming -> "Claiming..."
                    state.canClaimToday -> "Claim Day $nextDay"
                    else -> "Come back tomorrow"
                }
            )
        }
    }
}

@Composable
private fun DayCard(day: Int, coins: Int, isPremium: Boolean, isToday: Boolean, isClaimed: Boolean) {
    Card(
        modifier = Modifier.aspectRatio(0.85f),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isToday -> RewardOrange.copy(alpha = 0.25f)
                isClaimed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Day $day", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(if (isPremium) "⭐" else "🪙", style = MaterialTheme.typography.titleLarge)
            Text("$coins", style = MaterialTheme.typography.bodySmall, color = RewardOrange, fontWeight = FontWeight.Bold)
            if (isClaimed) {
                Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}
