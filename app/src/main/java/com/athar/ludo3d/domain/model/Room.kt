package com.athar.ludo3d.domain.model

// Added by Athar
// Mirrors rooms/{roomId} in Realtime Database (see firebase/DATABASE_DESIGN.md).
// Kept separate from the in-game GameState (domain/model/GameState.kt), which
// only exists once status flips to IN_PROGRESS and gameplay sync (Phase 5)
// takes over.
enum class RoomStatus { WAITING, IN_PROGRESS, FINISHED }

data class RoomPlayer(
    val userId: String,
    val name: String,
    val color: PlayerColor? = null, // assigned when they join; null is never written to a real player slot
    val ready: Boolean = false,
    val connected: Boolean = true,
    val isAI: Boolean = false,
    val joinedAt: Long = 0L
)

data class Room(
    val roomId: String,        // the shareable code, e.g. "ATHAR4587"
    val hostId: String,
    val status: RoomStatus = RoomStatus.WAITING,
    val maxPlayers: Int = 4,
    val isPrivate: Boolean = true,
    val createdAt: Long = 0L,
    val players: List<RoomPlayer> = emptyList()
) {
    val isFull: Boolean get() = players.size >= maxPlayers
    val allReady: Boolean get() = players.isNotEmpty() && players.all { it.ready }
    val canStart: Boolean get() = players.size >= 2 && allReady
}
