package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.model.LeaderboardEntry
import com.athar.ludo3d.domain.repository.LeaderboardRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Section 16. Global leaderboard is a straightforward orderByChild("wins")
// query — cheap because Realtime Database indexes support this natively
// (see the ".indexOn" hint added to database.rules.json for the users
// node). Friends leaderboard is implemented as "rank a given list of uids,"
// which is honest about the actual gap: there's no friends/social graph
// feature built yet (not in this spec's Phase 1-10 breakdown), so the
// friend-uid list has nowhere to come from yet. Once a friends system
// exists, its repository would supply that uid list and this method needs
// no changes.
@Singleton
class FirebaseLeaderboardRepository @Inject constructor(
    private val database: FirebaseDatabase
) : LeaderboardRepository {

    override suspend fun getGlobalLeaderboard(limit: Int): Result<List<LeaderboardEntry>> = runCatching {
        val snapshot = database.getReference("users")
            .orderByChild("wins")
            .limitToLast(limit)
            .get()
            .await()

        snapshot.children
            .mapNotNull { it.toLeaderboardEntry() }
            .sortedByDescending { it.wins }
    }

    override suspend fun getFriendsLeaderboard(friendUids: List<String>): Result<List<LeaderboardEntry>> = runCatching {
        if (friendUids.isEmpty()) return@runCatching emptyList()
        friendUids
            .mapNotNull { uid -> database.getReference("users").child(uid).get().await().toLeaderboardEntry(uid) }
            .sortedByDescending { it.wins }
    }

    private fun DataSnapshot.toLeaderboardEntry(uidOverride: String? = null): LeaderboardEntry? {
        val uid = uidOverride ?: key ?: return null
        val name = child("name").value as? String ?: return null
        return LeaderboardEntry(
            uid = uid,
            name = name,
            avatarId = child("avatarId").value as? String ?: "default",
            wins = (child("wins").value as? Long)?.toInt() ?: 0,
            gamesPlayed = (child("gamesPlayed").value as? Long)?.toInt() ?: 0,
            level = (child("level").value as? Long)?.toInt() ?: 1,
            xp = (child("xp").value as? Long)?.toInt() ?: 0
        )
    }
}
