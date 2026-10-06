package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Room
import com.athar.ludo3d.domain.model.RoomPlayer
import com.athar.ludo3d.domain.model.RoomStatus
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.RoomError
import com.athar.ludo3d.domain.repository.RoomRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Real Realtime Database implementation of section 4/5's room system. Every
// write here goes to YOUR Firebase project once google-services.json is in
// place (see README) — this is not a mock. Joins are transactional
// (runTransaction) so two players tapping "Join" on a room with one open
// slot at the same instant can't both succeed and silently corrupt the
// player list — exactly the kind of race condition four real phones hitting
// the same room WILL produce.
@Singleton
class FirebaseRoomRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authRepository: AuthRepository
) : RoomRepository {

    private val roomsRef: DatabaseReference get() = database.getReference("rooms")
    private val colorOrder = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)

    override suspend fun createRoom(hostName: String, maxPlayers: Int): Result<String> = runCatching {
        val uid = requireAuthUid()
        val code = generateUniqueRoomCode(hostName)
        val roomRef = roomsRef.child(code)

        val roomData = mapOf(
            "hostId" to uid,
            "status" to RoomStatus.WAITING.name,
            "maxPlayers" to maxPlayers,
            "isPrivate" to true,
            "createdAt" to ServerValue.TIMESTAMP,
            "players" to mapOf(
                uid to mapOf(
                    "name" to hostName,
                    "color" to PlayerColor.RED.name,
                    "ready" to false,
                    "connected" to true,
                    "isAI" to false,
                    "joinedAt" to ServerValue.TIMESTAMP
                )
            )
        )
        roomRef.setValue(roomData).await()
        attachPresence(code, uid)
        code
    }

    override suspend fun joinRoom(roomCode: String, playerName: String): Result<Unit> = runCatching {
        val uid = requireAuthUid()
        val normalizedCode = roomCode.trim().uppercase()
        val roomRef = roomsRef.child(normalizedCode)

        // Fast pre-check so we can surface RoomNotFound with a clean message
        // before attempting a transaction against a path that doesn't exist.
        val exists = roomRef.get().await().exists()
        if (!exists) throw RoomError.RoomNotFound

        var failure: RoomError? = null

        roomRef.runTransactionSuspend { data ->
            val status = data.child("status").value as? String
            if (status == RoomStatus.IN_PROGRESS.name || status == RoomStatus.FINISHED.name) {
                failure = RoomError.GameAlreadyStarted
                return@runTransactionSuspend Transaction.success(data) // no mutation: abort-by-noop, checked via `failure`
            }

            val maxPlayers = (data.child("maxPlayers").value as? Long)?.toInt() ?: 4
            val playersNode = data.child("players")
            val existingUids = playersNode.children.map { it.key }
            if (uid in existingUids) return@runTransactionSuspend Transaction.success(data) // already joined, idempotent

            if (existingUids.size >= maxPlayers) {
                failure = RoomError.RoomFull
                return@runTransactionSuspend Transaction.success(data)
            }

            val usedColors = playersNode.children.mapNotNull { it.child("color").value as? String }.toSet()
            val nextColor = colorOrder.firstOrNull { it.name !in usedColors }
            if (nextColor == null) {
                failure = RoomError.RoomFull
                return@runTransactionSuspend Transaction.success(data)
            }

            val newPlayer = mapOf(
                "name" to playerName,
                "color" to nextColor.name,
                "ready" to false,
                "connected" to true,
                "isAI" to false,
                "joinedAt" to ServerValue.TIMESTAMP
            )
            playersNode.child(uid).value = newPlayer
            Transaction.success(data)
        }

        failure?.let { throw it }
        attachPresence(normalizedCode, uid)
    }

    override fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        val ref = roomsRef.child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toRoom(roomId))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun setReady(roomId: String, ready: Boolean) {
        val uid = requireAuthUid()
        roomsRef.child(roomId).child("players").child(uid).child("ready").setValue(ready).await()
    }

    override suspend fun leaveRoom(roomId: String) {
        val uid = requireAuthUid()
        val roomRef = roomsRef.child(roomId)
        val snapshot = roomRef.get().await()
        val wasHost = snapshot.child("hostId").value as? String == uid

        roomRef.child("players").child(uid).removeValue().await()

        if (wasHost) {
            // Host left: promote the earliest-joined remaining player, or leave the
            // room hostless if empty (a listener elsewhere can clean up empty rooms).
            val remaining = roomRef.child("players").get().await()
            val nextHost = remaining.children.minByOrNull { (it.child("joinedAt").value as? Long) ?: Long.MAX_VALUE }
            if (nextHost != null) {
                roomRef.child("hostId").setValue(nextHost.key).await()
            }
        }
    }

    override suspend fun startGame(roomId: String): Result<Unit> = runCatching {
        val uid = requireAuthUid()
        val roomRef = roomsRef.child(roomId)
        val snapshot = roomRef.get().await()

        if (snapshot.child("hostId").value as? String != uid) throw RoomError.NotHost

        val playersSnapshot = snapshot.child("players")
        val playerCount = playersSnapshot.childrenCount.toInt()
        if (playerCount < 2) throw RoomError.NotEnoughPlayers

        val allReady = playersSnapshot.children.all { it.child("ready").value as? Boolean == true }
        if (!allReady) throw RoomError.NotAllReady

        // NOTE: this flips room status to IN_PROGRESS but does NOT yet write an
        // authoritative gameState/ subtree — that hand-off (turn order, dice,
        // token positions per firebase/DATABASE_DESIGN.md) is Phase 5's job,
        // along with the Cloud Function that becomes the only writer of it
        // (Phase 6). Writing it here would be exactly the kind of client-owned
        // "fake sync" the spec explicitly rules out.
        roomRef.child("status").setValue(RoomStatus.IN_PROGRESS.name).await()
    }

    // ---- internal helpers ----

    private fun requireAuthUid(): String =
        authRepository.currentUser()?.uid ?: error("Not authenticated")

    /** onDisconnect(): Firebase's own server-side presence hook — fires even if the app is killed, not just backgrounded. */
    private fun attachPresence(roomId: String, uid: String) {
        val connectedRef = roomsRef.child(roomId).child("players").child(uid).child("connected")
        connectedRef.onDisconnect().setValue(false)
    }

    private suspend fun generateUniqueRoomCode(hostName: String, attempts: Int = 5): String {
        repeat(attempts) {
            val code = buildRoomCode(hostName)
            val taken = roomsRef.child(code).get().await().exists()
            if (!taken) return code
        }
        error("Could not generate a unique room code after $attempts attempts")
    }

    /** Produces codes in the spec's style, e.g. "ATHAR4587": name prefix + 4 random digits. */
    private fun buildRoomCode(hostName: String): String {
        val letters = hostName.filter { it.isLetter() }.take(5).ifEmpty { "LUDO" }.uppercase()
        val digits = Random.nextInt(1000, 10000)
        return "$letters$digits"
    }

    private fun DataSnapshot.toRoom(roomId: String): Room? {
        if (!exists()) return null
        val hostId = child("hostId").value as? String ?: return null
        val status = (child("status").value as? String)?.let { runCatching { RoomStatus.valueOf(it) }.getOrNull() }
            ?: RoomStatus.WAITING
        val maxPlayers = (child("maxPlayers").value as? Long)?.toInt() ?: 4
        val isPrivate = child("isPrivate").value as? Boolean ?: true
        val createdAt = child("createdAt").value as? Long ?: 0L

        val players = child("players").children.mapNotNull { p ->
            val uid = p.key ?: return@mapNotNull null
            val name = p.child("name").value as? String ?: return@mapNotNull null
            val color = (p.child("color").value as? String)?.let { runCatching { PlayerColor.valueOf(it) }.getOrNull() }
            RoomPlayer(
                userId = uid,
                name = name,
                color = color,
                ready = p.child("ready").value as? Boolean ?: false,
                connected = p.child("connected").value as? Boolean ?: true,
                isAI = p.child("isAI").value as? Boolean ?: false,
                joinedAt = p.child("joinedAt").value as? Long ?: 0L
            )
        }

        return Room(
            roomId = roomId,
            hostId = hostId,
            status = status,
            maxPlayers = maxPlayers,
            isPrivate = isPrivate,
            createdAt = createdAt,
            players = players
        )
    }
}

/**
 * Added by Athar
 * Suspend wrapper around Firebase's callback-based runTransaction, so joinRoom()
 * above can read this linearly instead of nesting callbacks. Firebase itself
 * handles the retry-on-conflict semantics of a transaction internally.
 */
private suspend fun DatabaseReference.runTransactionSuspend(
    transactionUpdate: (MutableData) -> Transaction.Result
): DataSnapshot = suspendCancellableCoroutine { continuation ->
    runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result =
            transactionUpdate(currentData)

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            when {
                error != null -> continuation.resumeWithException(error.toException())
                snapshot != null -> continuation.resume(snapshot)
                else -> continuation.resumeWithException(IllegalStateException("Transaction produced no snapshot"))
            }
        }
    })
}
