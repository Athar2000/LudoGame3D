package com.athar.ludo3d.domain.repository

import com.athar.ludo3d.domain.model.ChatMessage
import com.athar.ludo3d.domain.model.ChatMessageType
import com.athar.ludo3d.domain.model.LeaderboardEntry
import com.athar.ludo3d.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

// Added by Athar

interface UserProfileRepository {
    fun observeProfile(uid: String): Flow<UserProfile?>
    suspend fun updateDisplayName(name: String): Result<Unit>
    suspend fun updateAvatar(avatarId: String): Result<Unit>
    suspend fun updateFavoriteColor(color: com.athar.ludo3d.domain.model.PlayerColor): Result<Unit>
}

interface ChatRepository {
    fun observeMessages(roomId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(roomId: String, text: String, type: ChatMessageType = ChatMessageType.TEXT): Result<Unit>
}

interface LeaderboardRepository {
    /** Top [limit] players by wins, globally. */
    suspend fun getGlobalLeaderboard(limit: Int = 50): Result<List<LeaderboardEntry>>

    /** Section 16's friends leaderboard needs a friends system that doesn't exist yet — see implementation note. */
    suspend fun getFriendsLeaderboard(friendUids: List<String>): Result<List<LeaderboardEntry>>
}

sealed class RewardsError(message: String) : Exception(message) {
    data object AlreadyClaimedToday : RewardsError("You've already claimed today's reward.")
    data object ItemNotFound : RewardsError("That item couldn't be found.")
    data object AlreadyOwned : RewardsError("You already own this item.")
    data object InsufficientCoins : RewardsError("Not enough coins for this item.")
}

data class DailyClaimResult(val day: Int, val coinsAwarded: Int, val isPremium: Boolean)

interface RewardsRepository {
    /** Both mutate coins server-side (Cloud Function) — see domain/model/ShopItem.kt and functions/src/rewardsAndShop.ts. */
    suspend fun claimDailyReward(): Result<DailyClaimResult>
    suspend fun purchaseItem(itemId: String): Result<Unit>
}
