package com.syncup.app.model

// User ka data represent karne ke liye data class
data class User(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val role: String = "member",
    val verified: Boolean = false,
    // --- FOLLOW COUNTS KE LIYE NAYE FIELDS ---
    val followersCount: Int = 0,
    val followingCount: Int = 0
)

