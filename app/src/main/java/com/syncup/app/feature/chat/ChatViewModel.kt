package com.syncup.app.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.syncup.app.R
import com.syncup.app.SuperbaseStorageUtils
import com.syncup.app.model.Channel
import com.syncup.app.model.Message
import com.syncup.app.model.ReplyMeta
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext val context: Context
) : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    val currentUser = FirebaseAuth.getInstance().currentUser
    private val storageUtils = SuperbaseStorageUtils(context)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage = _editingMessage.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers = _typingUsers.asStateFlow()

    private val _channelDetails = MutableStateFlow<Channel?>(null)
    val channelDetails = _channelDetails.asStateFlow()

    private val _otherUserId = MutableStateFlow<String?>(null)
    val otherUserId = _otherUserId.asStateFlow()

    private val _otherUserDetails = MutableStateFlow<User?>(null)
    val otherUserDetails = _otherUserDetails.asStateFlow()

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia = _isUploadingMedia.asStateFlow()

    private var typingJob: Job? = null
    private var typingStatusRef: DatabaseReference? = null
    private var typingListener: ValueEventListener? = null
    private var channelListener: ValueEventListener? = null
    private var channelDetailsRef: DatabaseReference? = null
    private var hideTimestamp = 0L


    fun setReplyingTo(message: Message?) {
        _replyingTo.value = message
    }

    fun startEditingMessage(message: Message) {
        _editingMessage.value = message
    }

    fun cancelEditingMessage() {
        _editingMessage.value = null
    }

    private fun updateChannelLastMessage(channelId: String, messageText: String, mediaType: String? = null) {
        val lastMessageText = when {
            mediaType == "image" -> if(messageText.isBlank()) "📷 Photo" else messageText
            mediaType == "video" -> if(messageText.isBlank()) "📹 Video" else messageText
            else -> messageText
        }
        val channelUpdates = mapOf(
            "lastMessage" to lastMessageText,
            "lastMessageTimestamp" to System.currentTimeMillis()
        )
        db.child("channels").child(channelId).updateChildren(channelUpdates)
    }


    fun sendMessage(channelID: String, channelName: String, messageText: String) {
        val messageToEdit = _editingMessage.value
        if (messageToEdit != null) {
            val messageRef = db.child("messages").child(channelID).child(messageToEdit.id)
            val updates = mapOf(
                "message" to messageText,
                "isEdited" to true
            )
            messageRef.updateChildren(updates).addOnCompleteListener {
                cancelEditingMessage()
                updateChannelLastMessage(channelID, messageText)
            }
        } else {
            val messageRef = db.child("messages").child(channelID).push()
            val currentReply = _replyingTo.value

            val replyMeta = if (currentReply != null) {
                ReplyMeta(
                    messageId = currentReply.id,
                    senderName = currentReply.senderName,
                    message = currentReply.message.ifBlank { if (currentReply.mediaType == "image") "Photo" else "Video" },
                    mediaType = currentReply.mediaType,
                    thumbnailUrl = currentReply.thumbnailUrl
                )
            } else { null }

            val message = Message(
                id = messageRef.key ?: UUID.randomUUID().toString(),
                senderId = currentUser?.uid ?: "",
                message = messageText,
                createdAt = System.currentTimeMillis(),
                senderName = currentUser?.displayName ?: "Unknown",
                replyTo = replyMeta,
                seenBy = mapOf(currentUser?.uid!! to (currentUser.displayName ?: "Unknown"))
            )

            messageRef.setValue(message).addOnCompleteListener {
                if (it.isSuccessful) {
                    setReplyingTo(null)
                    onTyping(channelID, "")
                    updateChannelLastMessage(channelID, messageText)
                    postNotificationToUsers(channelID, channelName, message.senderName, messageText, isPersonal = channelDetails.value?.isPersonal ?: false)
                }
            }
        }
    }

    fun sendMediaMessage(channelId: String, channelName: String, uri: Uri, mediaType: String, messageText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isUploadingMedia.value = true
            try {
                // --- BUILD ERROR FIX: URI se Bitmap banaya gaya ---
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                val thumbnailUrl = storageUtils.uploadThumbnail(bitmap)
                val mediaUrl = storageUtils.uploadChatMedia(uri, mediaType)

                if (mediaUrl != null && thumbnailUrl != null) {
                    val messageRef = db.child("messages").child(channelId).push()
                    val currentReply = _replyingTo.value

                    val replyMeta = if (currentReply != null) {
                        ReplyMeta(
                            messageId = currentReply.id,
                            senderName = currentReply.senderName,
                            message = currentReply.message.ifBlank { if (currentReply.mediaType == "image") "Photo" else "Video" },
                            mediaType = currentReply.mediaType,
                            thumbnailUrl = currentReply.thumbnailUrl
                        )
                    } else { null }

                    val message = Message(
                        id = messageRef.key ?: UUID.randomUUID().toString(),
                        senderId = currentUser?.uid ?: "",
                        message = messageText,
                        createdAt = System.currentTimeMillis(),
                        senderName = currentUser?.displayName ?: "Unknown",
                        mediaUrl = mediaUrl,
                        mediaType = mediaType,
                        thumbnailUrl = thumbnailUrl,
                        replyTo = replyMeta,
                        seenBy = mapOf(currentUser?.uid!! to (currentUser.displayName ?: "Unknown"))
                    )
                    messageRef.setValue(message).await()
                    setReplyingTo(null)
                    onTyping(channelId, "")
                    updateChannelLastMessage(channelId, messageText, mediaType)
                    postNotificationToUsers(channelId, channelName, message.senderName, message.message.ifBlank { if (mediaType == "image") "Sent a photo" else "Sent a video" }, isPersonal = channelDetails.value?.isPersonal ?: false)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Media upload failed", e)
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }


    fun toggleReaction(channelId: String, messageId: String, emoji: String) {
        val currentUserId = currentUser?.uid ?: return
        val reactionRef = db.child("messages").child(channelId).child(messageId).child("reactions").child(currentUserId)

        reactionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.value == emoji) {
                    reactionRef.removeValue()
                } else {
                    reactionRef.setValue(emoji)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Reaction toggle failed", error.toException())
            }
        })
    }

    fun unsendMessage(channelId: String, messageId: String) {
        db.child("messages").child(channelId).child(messageId).removeValue()
    }


    fun markMessagesAsRead(channelId: String) {
        val currentUserId = currentUser?.uid ?: return
        val currentUserName = currentUser?.displayName ?: "Unknown"
        viewModelScope.launch {
            val unreadMessages = _messages.value.filter {
                it.senderId != currentUserId && !it.seenBy.containsKey(currentUserId)
            }

            if (unreadMessages.isEmpty()) return@launch

            val updates = mutableMapOf<String, Any>()
            for (message in unreadMessages) {
                val path = "/messages/$channelId/${message.id}/seenBy/$currentUserId"
                updates[path] = currentUserName
            }

            db.updateChildren(updates).addOnFailureListener {
                Log.e("ChatViewModel", "Failed to mark messages as read", it)
            }
        }
    }

    fun onTyping(channelId: String, text: String) {
        typingJob?.cancel()
        typingStatusRef = db.child("typing_status").child(channelId).child(currentUser?.uid!!)
        typingStatusRef?.onDisconnect()?.removeValue()

        if (text.isNotBlank()) {
            typingStatusRef?.setValue(currentUser?.displayName ?: "Someone")
            typingJob = viewModelScope.launch {
                delay(3000)
                typingStatusRef?.removeValue()
            }
        } else {
            typingStatusRef?.removeValue()
        }
    }

    fun listenForMessages(channelID: String) {
        db.child("messages").child(channelID).orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { it.getValue(Message::class.java) }
                    _messages.value = list.filter { it.createdAt > hideTimestamp }
                    markMessagesAsRead(channelID)
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        typingStatusRef = db.child("typing_status").child(channelID)
        typingListener = typingStatusRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                _typingUsers.value = users.filter { it != currentUser?.displayName }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        subscribeForNotifications(channelID)
    }

    fun listenForChannelDetails(channelId: String) {
        channelDetailsRef = db.child("channels").child(channelId)
        channelListener = channelDetailsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val channel = snapshot.getValue(Channel::class.java)
                _channelDetails.value = channel
                hideTimestamp = channel?.hiddenBy?.get(currentUser?.uid) ?: 0L
                if (channel?.isPersonal == true) {
                    val otherId = channel.members.keys.firstOrNull { it != currentUser?.uid }
                    _otherUserId.value = otherId
                    if (otherId != null) {
                        viewModelScope.launch {
                            try {
                                val userSnapshot = db.child("users").child(otherId).get().await()
                                _otherUserDetails.value = userSnapshot.getValue(User::class.java)
                            } catch (e: Exception) {
                                Log.e("ChatViewModel", "Failed to fetch other user details", e)
                            }
                        }
                    }
                } else {
                    _otherUserId.value = null
                    _otherUserDetails.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Failed to listen for channel details", error.toException())
            }
        })
    }


    override fun onCleared() {
        super.onCleared()
        typingListener?.let { typingStatusRef?.removeEventListener(it) }
        typingStatusRef?.removeValue()
        channelListener?.let { channelDetailsRef?.removeEventListener(it) }
    }

    private fun subscribeForNotifications(channelId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("group_$channelId")
    }

    private fun postNotificationToUsers(channelId: String, channelName: String, senderName: String, messageContent: String, isPersonal: Boolean) {
        val fcmUrl = "https://fcm.googleapis.com/v1/projects/syncup-6d238/messages:send"
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val jsonBody = JSONObject().apply {
            put("message", JSONObject().apply {
                put("topic", "group_$channelId")
                put("data", JSONObject().apply {
                    put("channelId", channelId)
                    put("channelName", channelName)
                    put("senderId", currentUserId)
                    put("senderName", senderName)
                    put("messageContent", messageContent)
                    put("isPersonal", isPersonal.toString())
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
                Log.e("ChatViewModel", "Failed to send notification: ${error.networkResponse?.statusCode} ${error.message}")
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
        googleCreds.refreshIfExpired()
        return googleCreds.accessToken.tokenValue
    }
}
