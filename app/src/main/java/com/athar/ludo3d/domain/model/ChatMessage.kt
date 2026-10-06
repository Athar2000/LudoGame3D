package com.athar.ludo3d.domain.model

// Added by Athar
// Section 13: text chat, quick messages, and emoji reactions all flow
// through this one model with a `type` discriminator, rather than three
// separate systems — a quick message and an emoji reaction are really just
// pre-canned text, which keeps the Firebase schema and the UI simple.
enum class ChatMessageType { TEXT, QUICK, EMOJI }

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val type: ChatMessageType,
    val sentAtMillis: Long
)

/** Section 13's fixed quick-message set. */
val QUICK_MESSAGES = listOf("Good luck!", "Nice move!", "Well played!")

/** Section 13's fixed emoji-reaction set. */
val QUICK_EMOJIS = listOf("😂", "😎", "🎉", "🔥")
