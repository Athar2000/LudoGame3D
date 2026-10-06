package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.GameStatus
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Room
import com.athar.ludo3d.domain.model.Token
import com.athar.ludo3d.domain.repository.GameSyncError
import com.athar.ludo3d.domain.repository.GameSyncRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
//
// PHASE 6 REWRITE: this class used to reconstruct a LudoGameEngine locally
// and write the result straight to Realtime Database (Phase 5). It no
// longer does either of those things for anything that mutates game state —
// database.rules.json now sets gameState's ".write" to false for every
// client, so a direct write from here would simply be rejected by Firebase
// itself. Every mutating call below is instead a single httpsCallable to a
// Cloud Function in functions/src/index.ts, which runs the identical rules
// engine (functions/src/ludoEngine.ts, a field-for-field TypeScript port of
// the Kotlin LudoGameEngine) with the Admin SDK.
//
// What stayed the same: observeGameState() still reads Realtime Database
// directly — reads were never the trust problem, only writes were — so the
// live-update behavior every phone in the room relies on is unchanged from
// Phase 5. A hostile client can still read the game state, just not forge
// it.
@Singleton
class FirebaseGameSyncRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val functions: FirebaseFunctions
) : GameSyncRepository {

    private val roomsRef: DatabaseReference get() = database.getReference("rooms")

    override suspend fun startGame(room: Room): Result<Unit> =
        callFunction("startGame", mapOf("roomId" to room.roomId))

    override fun observeGameState(roomId: String): Flow<GameState?> = callbackFlow {
        val ref = roomsRef.child(roomId).child("gameState")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toGameState(roomId))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun rollDice(roomId: String): Result<Unit> =
        callFunction("rollDice", mapOf("roomId" to roomId))

    override suspend fun submitMove(roomId: String, tokenId: Int): Result<Unit> =
        callFunction("submitMove", mapOf("roomId" to roomId, "tokenId" to tokenId))

    override suspend fun forceSkipExpiredTurn(roomId: String): Result<Unit> =
        callFunction("forceSkipExpiredTurn", mapOf("roomId" to roomId))

    override suspend fun leaveGame(roomId: String): Result<Unit> =
        callFunction("leaveGame", mapOf("roomId" to roomId))

    override suspend fun requestRematch(roomId: String): Result<Unit> =
        callFunction("requestRematch", mapOf("roomId" to roomId))

    // ---- internal helpers ----

    /**
     * Calls a Cloud Function and maps its structured error (the ERROR_CODES
     * strings from functions/src/index.ts, sent as the HttpsError message)
     * back into the matching GameSyncError subtype, so the ViewModel/UI layer
     * sees the same typed errors whether the failure came from server-side
     * validation (now) or the old client-side validation (Phase 5) — no
     * screen had to change when this rewrite happened.
     */
    private suspend fun callFunction(name: String, data: Map<String, Any?>): Result<Unit> = runCatching {
        functions.getHttpsCallable(name).call(data).await()
        Unit
    }.recoverCatching { e ->
        throw e.toGameSyncError()
    }

    private fun Throwable.toGameSyncError(): Throwable {
        val ffe = this as? FirebaseFunctionsException ?: return this
        return when (ffe.message) {
            "NOT_YOUR_TURN" -> GameSyncError.NotYourTurn
            "GAME_NOT_FOUND" -> GameSyncError.GameNotFound
            "GAME_NOT_IN_PROGRESS" -> GameSyncError.GameNotInProgress
            "ALREADY_ROLLED" -> GameSyncError.AlreadyRolled
            "NO_DICE_ROLLED" -> GameSyncError.NoDiceRolled
            "INVALID_MOVE" -> GameSyncError.InvalidMove
            "TURN_NOT_EXPIRED_YET" -> GameSyncError.TurnNotExpiredYet
            "NOT_HOST" -> GameSyncError.NotHost
            else -> this
        }
    }

    private fun DataSnapshot.toGameState(roomId: String): GameState? {
        if (!exists()) return null
        val status = (child("status").value as? String)?.let { runCatching { GameStatus.valueOf(it) }.getOrNull() }
            ?: return null
        val currentPlayerIndex = (child("currentPlayerIndex").value as? Long)?.toInt() ?: 0
        val diceValue = (child("diceValue").value as? Long)?.toInt()
        val hasRolledThisTurn = child("hasRolledThisTurn").value as? Boolean ?: false
        val consecutiveSixes = (child("consecutiveSixes").value as? Long)?.toInt() ?: 0
        val winnerId = child("winnerId").value as? String
        val turnStartedAt = child("turnStartedAt").value as? Long ?: 0L

        val players = child("players").children.mapNotNull { p ->
            val userId = p.child("userId").value as? String ?: return@mapNotNull null
            val name = p.child("name").value as? String ?: return@mapNotNull null
            val color = (p.child("color").value as? String)?.let { runCatching { PlayerColor.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            val isAI = p.child("isAI").value as? Boolean ?: false
            val tokens = p.child("tokens").children.mapNotNull { t ->
                val id = (t.child("id").value as? Long)?.toInt() ?: return@mapNotNull null
                val position = (t.child("position").value as? Long)?.toInt() ?: Token.BASE
                Token(id = id, position = position)
            }.sortedBy { it.id }
            Player(
                userId = userId,
                name = name,
                color = color,
                isAI = isAI,
                tokens = if (tokens.size == 4) tokens else List(4) { Token(it) }
            )
        }
        if (players.isEmpty()) return null

        return GameState(
            roomId = roomId,
            players = players,
            currentPlayerIndex = currentPlayerIndex.coerceIn(players.indices),
            diceValue = diceValue,
            consecutiveSixes = consecutiveSixes,
            hasRolledThisTurn = hasRolledThisTurn,
            status = status,
            winnerId = winnerId,
            turnStartedAtMillis = turnStartedAt
        )
    }
}
