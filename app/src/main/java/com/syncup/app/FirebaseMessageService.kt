package com.syncup.app

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.syncup.app.model.Channel
import com.syncup.app.utils.ActiveChannelManager

class FirebaseMessageService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val senderId = data["senderId"]
        val channelId = data["channelId"]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Rule 1: Agar sender aap hi hain, to notification na dikhayein
        if (senderId != null && senderId == currentUserId) {
            Log.d("FCM", "Sender is self. Ignoring notification.")
            return
        }

        // Rule 2: Agar user usi channel me active hai, to notification na dikhayein
        if (channelId != null && channelId == ActiveChannelManager.activeChannelId) {
            Log.d("FCM", "User is in active channel. Suppressing notification.")
            return
        }

        // Rule 3: Mute status check karenge
        if (channelId != null && currentUserId != null) {
            val channelRef = FirebaseDatabase.getInstance().getReference("channels").child(channelId)
            channelRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val channel = snapshot.getValue(Channel::class.java)
                    val isMuted = channel?.mutedBy?.get(currentUserId) ?: false
                    if (isMuted) {
                        Log.d("FCM", "Channel is muted. Suppressing notification.")
                        return
                    }
                    // Agar muted nahi hai, toh hi notification dikhayenge
                    showNotificationFromData(data)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Error hone par bhi notification dikha denge (fallback)
                    Log.e("FCM", "Error checking mute status", error.toException())
                    showNotificationFromData(data)
                }
            })
        } else {
            // Agar channelId ya userId nahi hai, toh seedha notification dikha do
            showNotificationFromData(data)
        }
    }

    private fun showNotificationFromData(data: Map<String, String>) {
        val channelId = data["channelId"]
        val channelName = data["channelName"]
        val senderName = data["senderName"]
        val messageContent = data["messageContent"]
        val isPersonal = data["isPersonal"]?.toBoolean() ?: false

        val notificationTitle: String
        val notificationBody: String

        // Notification format logic
        if (isPersonal) {
            notificationTitle = senderName ?: "New Message"
            notificationBody = messageContent ?: ""
        } else {
            notificationTitle = channelName ?: "New Message"
            notificationBody = "$senderName: $messageContent"
        }

        Log.d("FCM", "Showing notification: Title='$notificationTitle', Body='$notificationBody'")

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("channelId", channelId)
            putExtra("channelName", if (isPersonal) senderName else channelName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Unique request code
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationHelper = NotificationHelper(this)
        notificationHelper.showNotification(
            title = notificationTitle,
            message = notificationBody,
            pendingIntent = pendingIntent
        )
    }
}

