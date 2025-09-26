package com.syncup.app.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    var message: String = "",
    val createdAt: Long = 0L,
    val senderName: String = "",
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val thumbnailUrl: String? = null,
    val replyTo: ReplyMeta? = null,
    val seenBy: Map<String, Any> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val isEdited: Boolean = false
)

data class ReplyMeta(
    val messageId: String = "",
    val senderName: String = "",
    val message: String = "",
    // Naye fields jo reply me media preview dikhayenge
    val mediaType: String? = null,
    val thumbnailUrl: String? = null
)

