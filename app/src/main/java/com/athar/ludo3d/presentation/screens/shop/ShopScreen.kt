package com.athar.ludo3d.presentation.screens.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.athar.ludo3d.domain.model.ShopCategory
import com.athar.ludo3d.domain.model.ShopItem
import com.athar.ludo3d.presentation.theme.RewardOrange
import com.athar.ludo3d.presentation.theme.SuccessGreen

// Added by Athar
// Section 17: dice skins, pawn skins, board themes, avatars, effects — all
// cosmetic, no real-money purchases anywhere in this screen (per the spec's
// explicit "do NOT implement real-money gambling or betting"). Purchases go
// through RewardsRepository.purchaseItem(), which is a Cloud Function call —
// the price shown here is for display only, purchaseItem() re-checks the
// real price server-side against functions/src/shopCatalog.ts.
@Composable
fun ShopScreen(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val grouped = state.catalog.groupBy { it.category }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shop", style = MaterialTheme.typography.headlineMedium)
            Text(
                "🪙 ${state.coins}",
                style = MaterialTheme.typography.titleLarge,
                color = RewardOrange,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            grouped.forEach { (category, items) ->
                item {
                    Text(
                        category.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
                items(items) { item ->
                    ShopItemRow(
                        item = item,
                        owned = item.id in state.ownedItemIds,
                        canAfford = state.coins >= item.priceCoins,
                        isPurchasing = state.purchasingItemId == item.id,
                        onBuy = { viewModel.purchase(item) }
                    )
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ShopItemRow(item: ShopItem, owned: Boolean, canAfford: Boolean, isPurchasing: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.name, fontWeight = FontWeight.SemiBold)
            when {
                owned -> Text("Owned", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                else -> Button(onClick = onBuy, enabled = canAfford && !isPurchasing) {
                    Text(if (isPurchasing) "..." else "🪙 ${item.priceCoins}")
                }
            }
        }
    }
}

private fun ShopCategory.displayName(): String = when (this) {
    ShopCategory.DICE_SKIN -> "Dice Skins"
    ShopCategory.PAWN_SKIN -> "Pawn Skins"
    ShopCategory.BOARD_THEME -> "Board Themes"
    ShopCategory.AVATAR -> "Avatars"
    ShopCategory.EFFECT -> "Effects"
}
