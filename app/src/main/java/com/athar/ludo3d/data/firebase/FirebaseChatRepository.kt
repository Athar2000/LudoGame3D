package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.model.ChatMessage
import com.athar.ludo3d.domain.model.ChatMessageType
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.ChatRepository
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Section 13. Messages are pushed with database.push() (a chronologically-
// sortable unique key) under rooms/{roomId}/chat, immutable once written —
// see database.rules.json's chat rules for the server-side half of spam
// protection (no edits, capped length, real server timestamp). The other
// half — a minimum interval between sends — is enforced here client-side,
// since RTDB rules alone can't cheaply check "how long since this same
// sender's last message" without an extra read per write.
@Singleton
class FirebaseChatRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authRepository: AuthRepository
) : ChatRepository {

    private var lastSentAtMillis = 0L

    override fun observeMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        // DatabaseReference already extends Query; limitToLast() could be
        // chained here for very long-lived rooms to cap history loaded.
        val ref = database.getReference("rooms").child(roomId).child("chat")

        val messages = linkedMapOf<String, ChatMessage>()

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toChatMessage()?.let { messages[snapshot.key!!] = it }
                trySend(messages.values.sortedBy { it.sentAtMillis })
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit // messages are immutable
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun sendMessage(roomId: String, text: String, type: ChatMessageType): Result<Unit> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message is empty" }
        require(trimmed.length <= 200) { "Message is too long" }

        val now = System.currentTimeMillis()
        if (now - lastSentAtMillis < MIN_INTERVAL_MS) {
            error("You're sending messages too quickly")
        }
        lastSentAtMillis = now

        val user = authRepository.currentUser() ?: error("Not authenticated")
        val ref = database.getReference("rooms").child(roomId).child("chat").push()
        ref.setValue(
            mapOf(
                "senderId" to user.uid,
                "senderName" to user.displayName,
                "text" to trimmed,
                "type" to type.name,
                "sentAt" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    private fun DataSnapshot.toChatMessage(): ChatMessage? {
        val id = key ?: return null
        val senderId = child("senderId").value as? String ?: return null
        val senderName = child("senderName").value as? String ?: return null
        val text = child("text").value as? String ?: return null
        val type = (child("type").value as? String)?.let { runCatching { ChatMessageType.valueOf(it) }.getOrNull() }
            ?: ChatMessageType.TEXT
        val sentAt = child("sentAt").value as? Long ?: 0L
        return ChatMessage(id, senderId, senderName, text, type, sentAt)
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1_500L
    }
}
