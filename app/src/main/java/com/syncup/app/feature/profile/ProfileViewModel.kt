package com.syncup.app.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _followers = MutableStateFlow<List<User>>(emptyList())
    val followers = _followers.asStateFlow()

    private val _following = MutableStateFlow<List<User>>(emptyList())
    val following = _following.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        fetchFullProfile()
    }

    private fun fetchFullProfile() {
        val userId = currentUser?.uid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch user profile, followers, and following lists
                fetchUserProfile(userId)
                fetchFollowers(userId)
                fetchFollowing(userId)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to fetch full profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchUserProfile(userId: String) {
        val userSnapshot = db.child("users").child(userId).get().await()
        val baseUser = userSnapshot.getValue(User::class.java)
        val followersSnapshot = db.child("followers").child(userId).get().await()
        val followingSnapshot = db.child("following").child(userId).get().await()
        val followersCount = followersSnapshot.childrenCount.toInt()
        val followingCount = followingSnapshot.childrenCount.toInt()
        _userProfile.value = baseUser?.copy(followersCount = followersCount, followingCount = followingCount)
    }

    private suspend fun fetchFollowers(userId: String) {
        val followerIdsSnapshot = db.child("followers").child(userId).get().await()
        val userList = mutableListOf<User>()
        for (child in followerIdsSnapshot.children) {
            val followerId = child.key ?: continue
            val userSnapshot = db.child("users").child(followerId).get().await()
            userSnapshot.getValue(User::class.java)?.let { userList.add(it) }
        }
        _followers.value = userList
    }

    private suspend fun fetchFollowing(userId: String) {
        val followingIdsSnapshot = db.child("following").child(userId).get().await()
        val userList = mutableListOf<User>()
        for (child in followingIdsSnapshot.children) {
            val followingId = child.key ?: continue
            val userSnapshot = db.child("users").child(followingId).get().await()
            userSnapshot.getValue(User::class.java)?.let { userList.add(it) }
        }
        _following.value = userList
    }
}
