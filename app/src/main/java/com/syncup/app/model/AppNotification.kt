package com.syncup.app.model

// Represents different types of notifications in the app
enum class NotificationType {
    FOLLOW, FOLLOW_BACK, UNKNOWN
}

// Data class for storing notification information in Firebase
data class AppNotification(
    val id: String = "",
    val type: String = NotificationType.UNKNOWN.name,
    val timestamp: Long = System.currentTimeMillis(),
    val actorId: String = "", // ID of the user who performed the action
    val actorUsername: String = "",
    val actorPhotoUrl: String? = null,
    var read: Boolean = false
)
