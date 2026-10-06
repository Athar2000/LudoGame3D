package com.athar.ludo3d.domain.model

// Added by Athar
// Section 16. A leaderboard entry is a read-only projection of a
// UserProfile — kept as its own type (rather than reusing UserProfile
// directly) so the leaderboard screen doesn't accidentally depend on fields
// (email, ownedItems) it has no business showing to other players.
data class LeaderboardEntry(
    val uid: String,
    val name: String,
    val avatarId: String,
    val wins: Int,
    val gamesPlayed: Int,
    val level: Int,
    val xp: Int
) {
    val winRatePercent: Int get() = if (gamesPlayed == 0) 0 else (wins * 100) / gamesPlayed
}

enum class LeaderboardScope { GLOBAL, FRIENDS }
