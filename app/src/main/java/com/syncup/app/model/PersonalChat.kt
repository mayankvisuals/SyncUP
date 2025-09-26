package com.syncup.app.model

// Personal Chat ki list UI me dikhane ke liye naya data class
data class PersonalChat(
    val channelId: String,
    val otherUser: User?,
    val lastMessage: String,
    val timestamp: Long,
    // --- NAYA FEATURE: Mute status ke liye field ---
    val isMuted: Boolean
)
