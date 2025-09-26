package com.syncup.app.model

// Channel Data Class me roles aur mute status ke liye naye fields add kiye gaye hain
data class Channel(
    var id: String = "",
    var name: String = "",
    var createdAt: Long = 0L,
    var createdBy: String = "", // Channel kisne banaya (Owner)
    var isPersonal: Boolean = false, // Yeh batayega ki DM hai ya Group
    // members map ab role store karega: "owner", "admin", "member"
    var members: Map<String, String> = emptyMap(),
    var lastMessage: String = "", // Aakhri message
    var lastMessageTimestamp: Long = 0L, // Aakhri message ka time
    var mutedBy: Map<String, Boolean> = emptyMap(), // Kaun kaun se users ne mute kiya hai
    // --- NAYA FEATURE: Chat hide karne ke liye field ---
    var hiddenBy: Map<String, Long> = emptyMap() // UserId to Timestamp
)
