package com.syncup.app.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.syncup.app.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _personalChats = MutableStateFlow<List<PersonalChat>>(emptyList())
    val personalChats = _personalChats.asStateFlow()

    private val _stories = MutableStateFlow<List<UserStory>>(emptyList())
    val stories = _stories.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<User?>(null)
    val currentUserProfile = _currentUserProfile.asStateFlow()

    init {
        listenToFeeds()
        fetchCurrentUserProfile()
    }

    private fun fetchCurrentUserProfile() {
        currentUser?.uid?.let { userId ->
            db.child("users").child(userId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _currentUserProfile.value = snapshot.getValue(User::class.java)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("HomeViewModel", "Failed to fetch current user profile", error.toException())
                }
            })
        }
    }

    private fun listenToFeeds() {
        val currentUserId = currentUser?.uid ?: return

        // Fetch stories only from users the current user is following
        db.child("following").child(currentUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val followingIds = snapshot.children.mapNotNull { it.key }.toMutableList()
                followingIds.add(currentUserId) // Add self to see own stories
                fetchStoriesForUsers(followingIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeViewModel", "Failed to get following list", error.toException())
            }
        })

        // Fetch chats (no changes here)
        listenToAllChats()
    }

    private fun fetchStoriesForUsers(userIds: List<String>) {
        val storiesRef = db.child("stories")
        storiesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val userStoriesList = mutableListOf<UserStory>()
                    val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

                    for (userId in userIds) {
                        if (snapshot.hasChild(userId)) {
                            val userSnapshot = db.child("users").child(userId).get().await()
                            val user = userSnapshot.getValue(User::class.java)
                            if (user != null) {
                                val userStories = snapshot.child(userId).children.mapNotNull { storySnapshot ->
                                    storySnapshot.getValue(Story::class.java)
                                }.filter { it.timestamp > twentyFourHoursAgo } // Filter old stories

                                if (userStories.isNotEmpty()) {
                                    userStoriesList.add(UserStory(user, userStories.sortedBy { it.timestamp }))
                                }
                            }
                        }
                    }
                    _stories.value = userStoriesList.sortedByDescending { it.stories.lastOrNull()?.timestamp ?: 0 }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeViewModel", "Failed to fetch stories", error.toException())
            }
        })
    }


    private fun listenToAllChats() {
        val currentUserId = currentUser?.uid ?: return
        db.child("channels").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupList = mutableListOf<Channel>()
                val dmList = mutableListOf<PersonalChat>()

                snapshot.children.forEach { data ->
                    try {
                        val channel = data.getValue(Channel::class.java)?.copy(id = data.key ?: "")
                        channel?.let {
                            if (it.members.containsKey(currentUserId)) {
                                if (it.isPersonal) {
                                    val hiddenTimestamp = it.hiddenBy[currentUserId] ?: 0L
                                    if (it.lastMessageTimestamp > hiddenTimestamp) {
                                        val otherUserId = it.members.keys.firstOrNull { key -> key != currentUserId }
                                        if (otherUserId != null) {
                                            viewModelScope.launch {
                                                val otherUser = getOtherUserDetails(otherUserId)
                                                val isMuted = it.mutedBy[currentUserId] ?: false
                                                dmList.add(
                                                    PersonalChat(
                                                        channelId = it.id,
                                                        otherUser = otherUser,
                                                        lastMessage = it.lastMessage,
                                                        timestamp = it.lastMessageTimestamp,
                                                        isMuted = isMuted
                                                    )
                                                )
                                                _personalChats.value = dmList.sortedByDescending { pc -> pc.timestamp }
                                            }
                                        }
                                    }
                                } else {
                                    groupList.add(it)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error parsing channel: ${data.value}", e)
                    }
                }
                _channels.value = groupList.sortedByDescending { it.lastMessageTimestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeViewModel", "Database error: ${error.message}")
            }
        })
    }

    private suspend fun getOtherUserDetails(userId: String): User? {
        return try {
            val userSnapshot = db.child("users").child(userId).get().await()
            userSnapshot.getValue(User::class.java)
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to fetch user details", e)
            null
        }
    }

    fun addChannel(name: String) {
        val currentUserId = currentUser?.uid ?: return
        val key = db.child("channels").push().key ?: return
        val newChannel = Channel(
            id = key,
            name = name,
            createdAt = System.currentTimeMillis(),
            createdBy = currentUserId,
            members = mapOf(currentUserId to "owner"),
            isPersonal = false
        )
        db.child("channels").child(key).setValue(newChannel)
    }

    fun toggleMutePersonalChat(channelId: String, isCurrentlyMuted: Boolean) {
        val currentUserId = currentUser?.uid ?: return
        val muteRef = db.child("channels").child(channelId).child("mutedBy").child(currentUserId)
        if (isCurrentlyMuted) {
            muteRef.removeValue()
        } else {
            muteRef.setValue(true)
        }
    }

    fun hidePersonalChat(channelId: String) {
        val currentUserId = currentUser?.uid ?: return
        db.child("channels").child(channelId).child("hiddenBy").child(currentUserId).setValue(System.currentTimeMillis())
    }
}

