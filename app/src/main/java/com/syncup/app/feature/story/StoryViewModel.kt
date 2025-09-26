package com.syncup.app.feature.story

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.SuperbaseStorageUtils
import com.syncup.app.model.Story
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    application: Application
) : AndroidViewModel(application) {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val storageUtils = SuperbaseStorageUtils(application)

    val initialPage: StateFlow<Int> = savedStateHandle.getStateFlow("storyStartIndex", 0)

    private val _viewers = MutableStateFlow<List<User>>(emptyList())
    val viewers = _viewers.asStateFlow()

    fun markStoryAsViewed(story: Story, storyAuthorId: String) {
        if (currentUserId == null || currentUserId == storyAuthorId) return // Can't view your own story
        // Mark story as viewed only if not already viewed
        if (!story.viewedBy.containsKey(currentUserId)) {
            db.child("stories").child(storyAuthorId).child(story.id)
                .child("viewedBy").child(currentUserId).setValue(true)
        }
    }

    fun fetchStoryViewers(story: Story) {
        viewModelScope.launch {
            try {
                val viewerIds = story.viewedBy.keys
                val viewersList = mutableListOf<User>()
                for (id in viewerIds) {
                    val userSnapshot = db.child("users").child(id).get().await()
                    userSnapshot.getValue(User::class.java)?.let {
                        viewersList.add(it)
                    }
                }
                _viewers.value = viewersList
            } catch (e: Exception) {
                Log.e("StoryViewModel", "Failed to fetch story viewers", e)
            }
        }
    }

    fun deleteStory(story: Story, storyAuthorId: String, onComplete: () -> Unit) {
        if (currentUserId != storyAuthorId) return // Can only delete your own story

        viewModelScope.launch {
            try {
                // Delete from Supabase Storage
                storageUtils.deleteImage(story.mediaUrl)
                // Delete from Firebase Realtime Database
                db.child("stories").child(storyAuthorId).child(story.id).removeValue().await()
                onComplete()
            } catch (e: Exception) {
                Log.e("StoryViewModel", "Failed to delete story", e)
            }
        }
    }
}

