package com.syncup.app.feature.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.syncup.app.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var notificationListener: ValueEventListener? = null

    init {
        listenForNotifications()
    }

    private fun listenForNotifications() {
        if (currentUserId == null) {
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        val notificationsRef = db.child("notifications").child(currentUserId).orderByChild("timestamp")

        notificationListener = notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notificationList = snapshot.children.mapNotNull { it.getValue(AppNotification::class.java) }
                _notifications.value = notificationList.reversed() // Show newest first
                _isLoading.value = false
                markNotificationsAsRead()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("NotificationViewModel", "Failed to fetch notifications", error.toException())
                _isLoading.value = false
            }
        })
    }

    private fun markNotificationsAsRead() {
        if (currentUserId == null) return
        viewModelScope.launch {
            val unreadNotifications = _notifications.value.filter { !it.read }
            if (unreadNotifications.isNotEmpty()) {
                val updates = mutableMapOf<String, Any>()
                unreadNotifications.forEach { notification ->
                    updates["/notifications/$currentUserId/${notification.id}/read"] = true
                }
                db.updateChildren(updates).addOnFailureListener {
                    Log.e("NotificationViewModel", "Failed to mark notifications as read", it)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationListener?.let {
            currentUserId?.let { userId ->
                db.child("notifications").child(userId).removeEventListener(it)
            }
        }
    }
}
