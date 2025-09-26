package com.syncup.app.service

import com.syncup.app.model.MusicTrack
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.* // <-- FIX: Changed from cio to android
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data classes to match the YouTube API JSON response structure
@Serializable
data class YoutubeSearchResponse(
    val items: List<YoutubeVideoItem>
)

@Serializable
data class YoutubeVideoItem(
    val id: VideoId,
    val snippet: VideoSnippet
)

@Serializable
data class VideoId(
    val videoId: String
)

@Serializable
data class VideoSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: Thumbnails
)

@Serializable
data class Thumbnails(
    @SerialName("default") val default: Thumbnail,
    @SerialName("high") val high: Thumbnail
)

@Serializable
data class Thumbnail(
    val url: String
)

class YoutubeApiService(private val apiKey: String) {

    // --- FIX: HttpClient engine changed from CIO to Android ---
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun searchMusic(query: String): List<MusicTrack> {
        val url = "https://www.googleapis.com/youtube/v3/search"
        try {
            val response: YoutubeSearchResponse = client.get(url) {
                parameter("part", "snippet")
                parameter("q", "$query audio") // Add "audio" to get better music results
                parameter("type", "video")
                parameter("maxResults", 20)
                parameter("key", apiKey)
            }.body()

            return response.items.map { video ->
                MusicTrack(
                    id = video.id.videoId,
                    title = video.snippet.title,
                    artist = video.snippet.channelTitle,
                    thumbnailUrl = video.snippet.thumbnails.high.url
                )
            }
        } catch (e: Exception) {
            // Log the error for debugging
            println("YouTube API Error: ${e.message}")
            return emptyList()
        }
    }
}

