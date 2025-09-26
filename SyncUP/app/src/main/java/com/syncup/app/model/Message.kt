package com.syncup.app.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    val message: String = "",
    val createdAt: Long = 0L,
    val senderName: String = "",
    val senderImage: String? = null, // Firebase ke liye String? zaroori hai
    val imageUrl: String? = null,
    // --- Naye Features ke liye Fields ---
    val replyTo: ReplyMeta? = null,
    val seenBy: Map<String, Boolean> = emptyMap()
)

data class ReplyMeta(
    val messageId: String = "",
    val senderName: String = "",
    val message: String = ""
)

