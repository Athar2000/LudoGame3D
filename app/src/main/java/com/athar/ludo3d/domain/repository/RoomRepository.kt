package com.athar.ludo3d.domain.repository

import com.athar.ludo3d.domain.model.Room
import kotlinx.coroutines.flow.Flow

// Added by Athar
sealed class RoomError(message: String) : Exception(message) {
    data object RoomNotFound : RoomError("Room not found. Check the code and try again.")
    data object RoomFull : RoomError("This room is already full.")
    data object GameAlreadyStarted : RoomError("This game has already started.")
    data object NotHost : RoomError("Only the host can do that.")
    data object NotEnoughPlayers : RoomError("Need at least 2 ready players to start.")
    data object NotAllReady : RoomError("All players must be ready to start.")
    data class Unknown(val raw: String) : RoomError(raw)
}

interface RoomRepository {
    /** Creates a new private room, joins the caller as host, and returns the shareable room code. */
    suspend fun createRoom(hostName: String, maxPlayers: Int): Result<String>

    /** Joins an existing room by its code. Fails with RoomError.RoomNotFound / RoomFull / GameAlreadyStarted. */
    suspend fun joinRoom(roomCode: String, playerName: String): Result<Unit>

    /** Live updates for a room — player list, ready states, connection status, game status. */
    fun observeRoom(roomId: String): Flow<Room?>

    suspend fun setReady(roomId: String, ready: Boolean)

    /** Removes the caller from the room; if they were host, hosting passes to the next player. */
    suspend fun leaveRoom(roomId: String)

    /** Host-only. Fails with RoomError.NotHost / NotEnoughPlayers / NotAllReady otherwise. */
    suspend fun startGame(roomId: String): Result<Unit>
}
