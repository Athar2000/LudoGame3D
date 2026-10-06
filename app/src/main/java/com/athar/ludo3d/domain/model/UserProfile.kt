package com.athar.ludo3d.domain.model

// Added by Athar
// Mirrors users/{uid} in Realtime Database. coins/level/xp/wins/gamesPlayed
// are never written by this app directly — see database.rules.json and
// functions/src/{onUserCreate,rewardsAndShop}.ts and the stats update inside
// functions/src/index.ts's submitMove. name/avatarId/favoriteColor ARE
// client-writable (cosmetic, not value).
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String?,
    val avatarId: String,
    val coins: Int,
    val level: Int,
    val xp: Int,
    val wins: Int,
    val gamesPlayed: Int,
    val favoriteColor: PlayerColor?,
    val ownedItems: Set<String>,
    val lastDailyRewardClaimedDay: Int,
    val lastDailyRewardClaimedAtMillis: Long
) {
    val winRatePercent: Int get() = if (gamesPlayed == 0) 0 else (wins * 100) / gamesPlayed
    val losses: Int get() = gamesPlayed - wins
}
