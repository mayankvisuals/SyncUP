package com.syncup.app.model

// Data class representing a single story
data class Story(
    val id: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image", // "image" or "video"
    val timestamp: Long = System.currentTimeMillis(),
    val musicTrack: MusicTrack? = null, // Nested MusicTrack object
    val viewedBy: Map<String, Boolean> = emptyMap()
)

// Data class to group stories by user for the UI
data class UserStory(
    val user: User,
    val stories: List<Story>
)

