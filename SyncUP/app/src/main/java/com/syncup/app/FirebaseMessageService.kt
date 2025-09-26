package com.syncup.app

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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

        // --- SMART NOTIFICATION FIX ---
        // Rule 2: Agar user usi channel me active hai, to notification na dikhayein
        if (channelId != null && channelId == ActiveChannelManager.activeChannelId) {
            Log.d("FCM", "User is in active channel. Suppressing notification.")
            return
        }
        // --- END OF FIX ---

        val channelName = data["channelName"]
        val senderName = data["senderName"]
        val messageContent = data["messageContent"]

        val notificationTitle = channelName ?: "New Message"
        val notificationBody = "$senderName: $messageContent"

        Log.d("FCM", "Showing notification: Title='$notificationTitle', Body='$notificationBody'")

        // Intent me ab channelName bhi bhejenge
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("channelId", channelId)
            putExtra("channelName", channelName) // Zaroori hai
        }

        // Har notification ke liye unique PendingIntent
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

