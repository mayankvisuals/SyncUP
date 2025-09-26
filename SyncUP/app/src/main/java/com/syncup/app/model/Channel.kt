package com.syncup.app.model

// Firebase ke saath kaam karne ke liye class ko update kiya gaya hai
data class Channel(
    var id: String = "",
    var name: String = "",
    var createdAt: Long = 0L // Timestamp abhi bhi hai
)

