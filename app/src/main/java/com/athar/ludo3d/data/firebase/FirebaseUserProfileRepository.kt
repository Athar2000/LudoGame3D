package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.UserProfile
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Reads/observes users/{uid} directly (reads were never the trust problem).
// Writes here are intentionally limited to the cosmetic fields
// database.rules.json actually allows a client to touch — name, avatarId,
// favoriteColor. coins/level/xp/wins/gamesPlayed have no write method on
// this class at all, because there is nothing this class could legitimately
// do with them: those are Cloud-Function-only (see onUserCreate.ts,
// rewardsAndShop.ts, and the stats update inside index.ts's submitMove).
@Singleton
class FirebaseUserProfileRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authRepository: AuthRepository
) : UserProfileRepository {

    private fun userRef(uid: String) = database.getReference("users").child(uid)

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val ref = userRef(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toUserProfile(uid))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val uid = requireUid()
        userRef(uid).child("name").setValue(name).await()
    }

    override suspend fun updateAvatar(avatarId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        userRef(uid).child("avatarId").setValue(avatarId).await()
    }

    override suspend fun updateFavoriteColor(color: PlayerColor): Result<Unit> = runCatching {
        val uid = requireUid()
        userRef(uid).child("favoriteColor").setValue(color.name).await()
    }

    private fun requireUid(): String =
        authRepository.currentUser()?.uid ?: error("Not authenticated")

    private fun DataSnapshot.toUserProfile(uid: String): UserProfile? {
        if (!exists()) return null
        val ownedItems = child("ownedItems").children.mapNotNull { it.key }.toSet()
        val favoriteColorStr = child("favoriteColor").value as? String
        return UserProfile(
            uid = uid,
            name = child("name").value as? String ?: "Player",
            email = child("email").value as? String,
            avatarId = child("avatarId").value as? String ?: "default",
            coins = (child("coins").value as? Long)?.toInt() ?: 0,
            level = (child("level").value as? Long)?.toInt() ?: 1,
            xp = (child("xp").value as? Long)?.toInt() ?: 0,
            wins = (child("wins").value as? Long)?.toInt() ?: 0,
            gamesPlayed = (child("gamesPlayed").value as? Long)?.toInt() ?: 0,
            favoriteColor = favoriteColorStr?.let { runCatching { PlayerColor.valueOf(it) }.getOrNull() },
            ownedItems = ownedItems,
            lastDailyRewardClaimedDay = (child("lastDailyRewardClaimedDay").value as? Long)?.toInt() ?: 0,
            lastDailyRewardClaimedAtMillis = child("lastDailyRewardClaimedAt").value as? Long ?: 0L
        )
    }
}
