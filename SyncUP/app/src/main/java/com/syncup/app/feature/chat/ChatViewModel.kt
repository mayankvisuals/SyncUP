package com.syncup.app.feature.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.syncup.app.R
import com.syncup.app.model.Message
import com.syncup.app.model.ReplyMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext val context: Context
) : ViewModel() {

    private val db = Firebase.database.reference
    val currentUser = Firebase.auth.currentUser

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    // --- TYPING INDICATOR KE LIYE NAYA STATE ---
    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers = _typingUsers.asStateFlow()

    private var typingJob: Job? = null
    private var typingStatusRef: DatabaseReference? = null
    private var typingListener: ValueEventListener? = null


    fun setReplyingTo(message: Message?) {
        _replyingTo.value = message
    }

    fun sendMessage(channelID: String, channelName: String, messageText: String) {
        val messageRef = db.child("messages").child(channelID).push()
        val currentReply = _replyingTo.value
        val replyMeta = if (currentReply != null) {
            ReplyMeta(currentReply.id, currentReply.senderName, currentReply.message)
        } else { null }

        val message = Message(
            id = messageRef.key ?: UUID.randomUUID().toString(),
            senderId = currentUser?.uid ?: "",
            message = messageText,
            createdAt = System.currentTimeMillis(),
            senderName = currentUser?.displayName ?: "",
            replyTo = replyMeta,
            seenBy = mapOf(currentUser?.uid!! to true) // Bhejne wala hamesha seen kar chuka hota hai
        )

        messageRef.setValue(message).addOnCompleteListener {
            if (it.isSuccessful) {
                setReplyingTo(null)
                onTyping(channelID, "") // Message bhejte hi typing status hata do
                postNotificationToUsers(channelID, channelName, message.senderName, messageText)
            }
        }
    }

    // --- SEEN BY FEATURE ---
    fun markMessagesAsRead(channelId: String) {
        val currentUserId = currentUser?.uid ?: return
        viewModelScope.launch {
            // Sirf un messages ko update karo jo doosron ne bheje hain aur aapne nahi dekhe
            val unreadMessages = _messages.value.filter {
                it.senderId != currentUserId && !it.seenBy.containsKey(currentUserId)
            }
            for (message in unreadMessages) {
                db.child("messages").child(channelId).child(message.id).child("seenBy")
                    .child(currentUserId).setValue(true)
            }
        }
    }

    // --- TYPING INDICATOR LOGIC ---
    fun onTyping(channelId: String, text: String) {
        typingJob?.cancel()
        typingStatusRef = db.child("typing_status").child(channelId).child(currentUser?.uid!!)
        typingStatusRef?.onDisconnect()?.removeValue() // Agar app band ho jaaye to status hata do

        if (text.isNotBlank()) {
            // Agar user type kar raha hai, to database me status set karo
            typingStatusRef?.setValue(currentUser?.displayName ?: "Someone")
            // 3 second baad typing status hata do
            typingJob = viewModelScope.launch {
                delay(3000)
                typingStatusRef?.removeValue()
            }
        } else {
            // Agar text field khali hai, to status hata do
            typingStatusRef?.removeValue()
        }
    }

    fun listenForMessages(channelID: String) {
        // Messages ko suno
        db.child("messages").child(channelID).orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { it.getValue(Message::class.java) }
                    _messages.value = list
                    markMessagesAsRead(channelID) // Jab naye message aayein to unhe read mark kar do
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Typing users ko suno
        typingStatusRef = db.child("typing_status").child(channelID)
        typingListener = typingStatusRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                // Khud ko typing list me na dikhayein
                _typingUsers.value = users.filter { it != currentUser?.displayName }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        subscribeForNotifications(channelID)
    }

    // Screen band hone par listeners ko hata do
    override fun onCleared() {
        super.onCleared()
        typingListener?.let { typingStatusRef?.removeEventListener(it) }
        typingStatusRef?.removeValue()
    }

    // Notification ka code waisa hi hai
    private fun subscribeForNotifications(channelId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("group_$channelId")
    }

    private fun postNotificationToUsers(channelId: String, channelName: String, senderName: String, messageContent: String) {
        val fcmUrl = "https://fcm.googleapis.com/v1/projects/syncup-6d238/messages:send"
        val currentUserId = Firebase.auth.currentUser?.uid ?: ""

        val jsonBody = JSONObject().apply {
            put("message", JSONObject().apply {
                put("topic", "group_$channelId")
                put("data", JSONObject().apply {
                    put("channelId", channelId)
                    put("channelName", channelName)
                    put("senderId", currentUserId)
                    put("senderName", senderName)
                    put("messageContent", messageContent)
                })
                put("android", JSONObject().apply {
                    put("priority", "high")
                })
            })
        }

        val requestBody = jsonBody.toString()

        val request = object : StringRequest(
            Method.POST, fcmUrl,
            Response.Listener {
                Log.d("ChatViewModel", "Notification sent successfully")
            },
            Response.ErrorListener { error ->
                Log.e("ChatViewModel", "Failed to send notification: ${error.message}")
            }) {
            override fun getBody(): ByteArray = requestBody.toByteArray()

            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer ${getAccessToken()}"
                headers["Content-Type"] = "application/json"
                return headers
            }
        }

        val queue = Volley.newRequestQueue(context)
        queue.add(request)
    }

    private fun getAccessToken(): String {
        val inputStream = context.resources.openRawResource(R.raw.syncup_key)
        val googleCreds = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
        return googleCreds.refreshAccessToken().tokenValue
    }
}

