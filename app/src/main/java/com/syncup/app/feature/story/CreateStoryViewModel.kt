package com.syncup.app.feature.story

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.SuperbaseStorageUtils
import com.syncup.app.model.MusicTrack
import com.syncup.app.model.Story
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class CreateStoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private val storageUtils = SuperbaseStorageUtils(context)

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _uploadSuccess = MutableStateFlow(false)
    val uploadSuccess = _uploadSuccess.asStateFlow()

    fun uploadStory(uri: Uri, musicTrack: MusicTrack?) {
        val currentUserId = currentUser?.uid ?: return
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val mimeType = context.contentResolver.getType(uri)
                val mediaType = when {
                    mimeType?.startsWith("image/") == true -> "image"
                    mimeType?.startsWith("video/") == true -> "video"
                    else -> throw IllegalArgumentException("Unsupported media type")
                }

                val mediaUrl = storageUtils.uploadChatMedia(uri, mediaType)
                    ?: throw Exception("Media upload failed to get URL")
                val storyId = db.child("stories").child(currentUserId).push().key
                    ?: throw Exception("Could not generate story key")

                val newStory = Story(
                    id = storyId,
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis(),
                    musicTrack = musicTrack
                )

                db.child("stories").child(currentUserId).child(storyId).setValue(newStory).await()
                _uploadSuccess.value = true

            } catch (e: Exception) {
                Log.e("CreateStoryVM", "Failed to upload story", e)
                _uploadSuccess.value = false
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun resetUploadStatus() {
        _uploadSuccess.value = false
    }
}

