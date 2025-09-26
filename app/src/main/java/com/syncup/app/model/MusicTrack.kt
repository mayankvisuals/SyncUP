package com.syncup.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicTrack(
    // --- FIX: Default values added to all fields for Firebase compatibility ---
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val startTimeMs: Long = 0L,
    val musicStreamUrl: String? = null
) : Parcelable

