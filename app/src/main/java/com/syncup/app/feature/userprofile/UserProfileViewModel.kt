package com.syncup.app.feature.userprofile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.model.AppNotification
import com.syncup.app.model.Channel
import com.syncup.app.model.NotificationType
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _isLoadingFollow = MutableStateFlow(false)
    val isLoadingFollow = _isLoadingFollow.asStateFlow()


    fun fetchUserProfile(userId: String) {
        viewModelScope.launch {
            try {
                val userSnapshot = db.child("users").child(userId).get().await()
                val baseUser = userSnapshot.getValue(User::class.java)

                val followersSnapshot = db.child("followers").child(userId).get().await()
                val followingSnapshot = db.child("following").child(userId).get().await()

                val followersCount = followersSnapshot.childrenCount.toInt()
                val followingCount = followingSnapshot.childrenCount.toInt()

                _userProfile.value = baseUser?.copy(
                    followersCount = followersCount,
                    followingCount = followingCount
                )

                checkIfMuted(userId)
                checkIfFollowing(userId)

            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to fetch user profile", e)
            }
        }
    }

    private fun checkIfFollowing(userId: String) {
        val currentUserId = currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.child("following").child(currentUserId).child(userId).get().await()
                _isFollowing.value = snapshot.exists()
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to check following status", e)
            }
        }
    }

    fun toggleFollow() {
        val otherUser = _userProfile.value ?: return
        val currentUserId = currentUser?.uid ?: return
        val currentUserName = currentUser?.displayName ?: "Unknown"
        val currentUserPhoto = currentUser?.photoUrl?.toString()

        if (_isLoadingFollow.value) return

        viewModelScope.launch {
            _isLoadingFollow.value = true
            try {
                val newFollowingStatus = !_isFollowing.value
                val updates = mutableMapOf<String, Any?>()

                if (newFollowingStatus) { // Follow karna hai
                    updates["/following/$currentUserId/${otherUser.id}"] = true
                    updates["/followers/${otherUser.id}/$currentUserId"] = true
                    // Check for follow back
                    val isFollowingCurrentUser = db.child("following").child(otherUser.id).child(currentUserId).get().await().exists()
                    val notificationType = if (isFollowingCurrentUser) NotificationType.FOLLOW_BACK else NotificationType.FOLLOW
                    // Send notification to the other user
                    sendFollowNotification(otherUser.id, currentUserId, currentUserName, currentUserPhoto, notificationType)

                } else { // Unfollow karna hai
                    updates["/following/$currentUserId/${otherUser.id}"] = null
                    updates["/followers/${otherUser.id}/$currentUserId"] = null
                }

                db.updateChildren(updates).await()
                _isFollowing.value = newFollowingStatus
                fetchUserProfile(otherUser.id)
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to toggle follow", e)
            } finally {
                _isLoadingFollow.value = false
            }
        }
    }

    private suspend fun sendFollowNotification(
        targetUserId: String,
        actorId: String,
        actorUsername: String,
        actorPhotoUrl: String?,
        type: NotificationType
    ) {
        try {
            val notificationId = UUID.randomUUID().toString()
            val notification = AppNotification(
                id = notificationId,
                type = type.name,
                actorId = actorId,
                actorUsername = actorUsername,
                actorPhotoUrl = actorPhotoUrl
            )
            db.child("notifications").child(targetUserId).child(notificationId).setValue(notification).await()
        } catch (e: Exception) {
            Log.e("UserProfileVM", "Failed to send notification", e)
        }
    }


    private fun checkIfMuted(otherUserId: String) {
        val currentUserId = currentUser?.uid ?: return
        val channelId = if (currentUserId > otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        viewModelScope.launch {
            try {
                val snapshot = db.child("channels").child(channelId).child("mutedBy").child(currentUserId).get().await()
                _isMuted.value = snapshot.exists() && snapshot.getValue(Boolean::class.java) == true
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to check mute status", e)
            }
        }
    }

    fun toggleMute() {
        val otherUserId = _userProfile.value?.id ?: return
        val currentUserId = currentUser?.uid ?: return
        val channelId = if (currentUserId > otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        val newMuteStatus = !_isMuted.value
        viewModelScope.launch {
            try {
                val muteRef = db.child("channels").child(channelId).child("mutedBy").child(currentUserId)
                if (newMuteStatus) {
                    muteRef.setValue(true).await()
                } else {
                    muteRef.removeValue().await()
                }
                _isMuted.value = newMuteStatus
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to toggle mute", e)
            }
        }
    }

    fun startPersonalChat(otherUser: User, onChatStarted: (channelId: String, channelName: String) -> Unit) {
        val currentUserId = currentUser?.uid ?: return
        val otherUserId = otherUser.id
        val channelId = if (currentUserId > otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        val channelRef = db.child("channels").child(channelId)
        viewModelScope.launch {
            try {
                val snapshot = channelRef.get().await()
                if (!snapshot.exists()) {
                    val newChannel = Channel(
                        id = channelId, name = "", isPersonal = true,
                        members = mapOf(currentUserId to "member", otherUserId to "member")
                    )
                    channelRef.setValue(newChannel).await()
                }
                onChatStarted(channelId, otherUser.name)
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Failed to start chat", e)
            }
        }
    }
}
