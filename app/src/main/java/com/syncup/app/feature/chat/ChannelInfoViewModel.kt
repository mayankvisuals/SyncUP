package com.syncup.app.feature.chat.channelinfo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.syncup.app.model.Channel
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChannelInfoViewModel @Inject constructor() : ViewModel() {

    private val db = Firebase.database.reference
    val currentUser = Firebase.auth.currentUser
    private val currentUserId = currentUser?.uid ?: ""

    private val _channelDetails = MutableStateFlow<Channel?>(null)
    val channelDetails = _channelDetails.asStateFlow()

    private val _members = MutableStateFlow<List<User>>(emptyList())
    val members = _members.asStateFlow()

    private val _potentialMembers = MutableStateFlow<List<User>>(emptyList())
    val potentialMembers = _potentialMembers.asStateFlow()

    private val _currentUserRole = MutableStateFlow("member")
    val currentUserRole = _currentUserRole.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private var channelListener: ValueEventListener? = null

    fun loadChannelInfo(channelId: String) {
        val channelRef = db.child("channels").child(channelId)
        channelListener = channelRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val channel = snapshot.getValue(Channel::class.java)
                _channelDetails.value = channel
                channel?.let {
                    _currentUserRole.value = it.members[currentUserId] ?: "member"
                    _isMuted.value = it.mutedBy[currentUserId] ?: false
                    loadMembers(it)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChannelInfoVM", "Error loading channel info: ${error.message}")
            }
        })
    }

    private fun loadMembers(channel: Channel) {
        viewModelScope.launch {
            try {
                val memberIds = channel.members.keys
                val memberList = mutableListOf<User>()
                for (id in memberIds) {
                    val userSnapshot = db.child("users").child(id).get().await()
                    userSnapshot.getValue(User::class.java)?.let { user ->
                        val userWithRole = user.copy(role = channel.members[id] ?: "member")
                        memberList.add(userWithRole)
                    }
                }
                _members.value = memberList.sortedBy {
                    when (it.role) {
                        "owner" -> 0
                        "admin" -> 1
                        else -> 2
                    }
                }
                // Potential members ko load karne ke liye naya function call karenge
                loadPotentialMembersBasedOnFollowing(memberIds)
            } catch (e: Exception) {
                Log.e("ChannelInfoVM", "Error loading members: ${e.message}")
            }
        }
    }

    // --- YEH FUNCTION UPDATE KIYA GAYA HAI ---
    private fun loadPotentialMembersBasedOnFollowing(existingMemberIds: Set<String>) {
        viewModelScope.launch {
            try {
                // Step 1: Un sabhi logon ki IDs laayein jinhe current user follow karta hai
                val followingSnapshot = db.child("following").child(currentUserId).get().await()
                val followingIds = followingSnapshot.children.mapNotNull { it.key }

                // Step 2: Har ID ke liye user details laayein aur list banayein
                val potentialList = mutableListOf<User>()
                for (userId in followingIds) {
                    // Agar user pehle se member nahi hai, tabhi use list me add karein
                    if (userId !in existingMemberIds) {
                        val userSnapshot = db.child("users").child(userId).get().await()
                        userSnapshot.getValue(User::class.java)?.let {
                            potentialList.add(it)
                        }
                    }
                }
                _potentialMembers.value = potentialList
            } catch (e: Exception) {
                Log.e("ChannelInfoVM", "Error loading potential members based on following: ${e.message}")
            }
        }
    }


    fun addMembersToChannel(channelId: String, userIdsToAdd: List<String>) {
        if (userIdsToAdd.isEmpty()) return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>()
                userIdsToAdd.forEach { userId ->
                    updates["/channels/$channelId/members/$userId"] = "member"
                }
                db.updateChildren(updates).await()
            } catch (e: Exception) {
                Log.e("ChannelInfoVM", "Error adding members: ${e.message}")
            }
        }
    }

    fun kickMember(channelId: String, memberId: String) {
        db.child("channels").child(channelId).child("members").child(memberId).removeValue()
    }

    fun promoteToAdmin(channelId: String, memberId: String) {
        db.child("channels").child(channelId).child("members").child(memberId).setValue("admin")
    }

    fun demoteToMember(channelId: String, memberId: String) {
        db.child("channels").child(channelId).child("members").child(memberId).setValue("member")
    }

    fun leaveChannel(channelId: String, onChannelLeft: () -> Unit) {
        viewModelScope.launch {
            db.child("channels").child(channelId).child("members").child(currentUserId).removeValue()
                .addOnSuccessListener {
                    onChannelLeft()
                }
        }
    }

    fun toggleMuteChannel(channelId: String) {
        val newMuteStatus = !_isMuted.value
        db.child("channels").child(channelId).child("mutedBy").child(currentUserId)
            .setValue(if (newMuteStatus) true else null) // true for mute, null to remove
    }

    override fun onCleared() {
        super.onCleared()
        channelListener?.let { db.removeEventListener(it) }
    }
}

