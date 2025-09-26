@file:OptIn(ExperimentalLayoutApi::class)

package com.syncup.app.feature.chat

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.syncup.app.R
import com.syncup.app.SuperbaseStorageUtils
import com.syncup.app.model.Message
import com.syncup.app.model.ReplyMeta
import com.syncup.app.utils.ActiveChannelManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, channelId: String, channelName: String) {
    val viewModel: ChatViewModel = hiltViewModel()
    val messages by viewModel.messages.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val channelDetails by viewModel.channelDetails.collectAsState()
    val otherUserDetails by viewModel.otherUserDetails.collectAsState()
    val otherUserId by viewModel.otherUserId.collectAsState()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUserId = viewModel.currentUser?.uid
    var expandedMessageId by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(editingMessage) {
        editingMessage?.let {
            messageText = it.message
        }
    }


    val lastSeenMessageMap by remember(messages, currentUserId) {
        derivedStateOf {
            val map = mutableMapOf<String, MutableList<String>>()
            if (currentUserId == null) return@derivedStateOf emptyMap()
            val otherUserIds = messages.flatMap { it.seenBy.keys }.distinct().filter { it != currentUserId }
            otherUserIds.forEach { userId ->
                val lastSeenMessageOfYours = messages
                    .filter { it.senderId == currentUserId && it.seenBy.containsKey(userId) }
                    .maxByOrNull { it.createdAt }
                if (lastSeenMessageOfYours != null) {
                    val userName = (lastSeenMessageOfYours.seenBy[userId] as? String) ?: return@forEach
                    map.getOrPut(lastSeenMessageOfYours.id) { mutableListOf() }.add(userName)
                }
            }
            map
        }
    }


    DisposableEffect(lifecycleOwner, channelId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ActiveChannelManager.activeChannelId = channelId
                viewModel.markMessagesAsRead(channelId)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                ActiveChannelManager.activeChannelId = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ActiveChannelManager.activeChannelId = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(channelId) {
        viewModel.listenForMessages(channelId)
        viewModel.listenForChannelDetails(channelId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.my_wallpaper),
            contentDescription = "Chat background wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        val headerModifier = when {
                            channelDetails?.isPersonal == false -> Modifier.clickable { navController.navigate("channel_info/$channelId") }
                            channelDetails?.isPersonal == true && otherUserId != null -> Modifier.clickable { navController.navigate("user_profile/$otherUserId") }
                            else -> Modifier
                        }
                        Row(
                            modifier = headerModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (channelDetails?.isPersonal == true) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)){
                                    if(otherUserDetails != null){
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(otherUserDetails?.photoUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Profile Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Gray))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Group Icon",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (channelDetails?.isPersonal == true) otherUserDetails?.name ?: channelName else channelName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    if (otherUserDetails?.verified == true) {
                                        Spacer(Modifier.width(6.dp))
                                        VerifiedTick(size = 18.dp)
                                    }
                                }
                                AnimatedVisibility(visible = typingUsers.isNotEmpty() || isUploadingMedia) {
                                    val statusText = when {
                                        isUploadingMedia -> "Sending media..."
                                        typingUsers.isEmpty() -> ""
                                        channelDetails?.isPersonal == true -> "typing..."
                                        typingUsers.size == 1 -> "${typingUsers.first()} is typing..."
                                        else -> "${typingUsers.take(2).joinToString(" & ")} are typing..."
                                    }
                                    Text(statusText, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    // --- HEADER OVERLAP FIX: TopAppBar ko bataya gaya hai ki status bar ke liye jagah chhodni hai ---
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Apply padding from this Scaffold
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp) // Inner padding for LazyColumn items
                ) {
                    itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                        val showDateHeader = shouldShowDateHeader(messages, index)
                        if (showDateHeader) {
                            DateHeader(timestamp = message.createdAt)
                        }

                        ChatBubble(
                            message = message,
                            navController = navController,
                            currentUserId = currentUserId,
                            isExpanded = expandedMessageId == message.id,
                            onExpand = { expandedMessageId = message.id },
                            onDismiss = { expandedMessageId = null },
                            onReply = { viewModel.setReplyingTo(it) },
                            onReaction = { emoji ->
                                viewModel.toggleReaction(channelId, message.id, emoji)
                                expandedMessageId = null
                            },
                            onUnsend = { viewModel.unsendMessage(channelId, message.id) },
                            onEdit = { viewModel.startEditingMessage(message) }
                        )

                        if (lastSeenMessageMap.containsKey(message.id)) {
                            val seenByNames = lastSeenMessageMap[message.id]?.joinToString(", ") ?: ""
                            if (seenByNames.isNotBlank()) {
                                Text(
                                    text = "✓ Seen by $seenByNames",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, end = 16.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                MessageInput(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        viewModel.onTyping(channelId, it)
                    },
                    onSendMessage = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(channelId, otherUserDetails?.name ?: channelName, messageText.trim())
                            messageText = ""
                        }
                    },
                    onSendMedia = { uri, type ->
                        viewModel.sendMediaMessage(channelId, otherUserDetails?.name ?: channelName, uri, type, messageText.trim())
                        messageText = ""
                    },
                    replyingTo = replyingTo,
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    editingMessage = editingMessage,
                    onCancelEdit = { viewModel.cancelEditingMessage() },
                    isUploading = isUploadingMedia
                )
            }
        }
    }
}

fun isFileSizeValid(uri: Uri, context: Context, mediaType: String): Boolean {
    val maxPhotoSize = 5 * 1024 * 1024 // 5 MB
    val maxVideoSize = 15 * 1024 * 1024 // 15 MB
    val maxSize = if (mediaType == "image") maxPhotoSize else maxVideoSize

    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val size = inputStream?.available() ?: 0
        inputStream?.close()
        size <= maxSize
    } catch (e: Exception) {
        false
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSendMedia: (Uri, String) -> Unit,
    replyingTo: Message?,
    onCancelReply: () -> Unit,
    editingMessage: Message?,
    onCancelEdit: () -> Unit,
    isUploading: Boolean
) {
    val isEditing = editingMessage != null
    val headerText = if (isEditing) "Editing Message" else if (replyingTo != null) "Replying to ${replyingTo.senderName}" else ""
    val context = LocalContext.current

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            val mediaType = when {
                mimeType?.startsWith("image/") == true -> "image"
                mimeType?.startsWith("video/") == true -> "video"
                else -> null
            }

            if (mediaType != null) {
                if (isFileSizeValid(it, context, mediaType)) {
                    onSendMedia(it, mediaType)
                } else {
                    val limit = if (mediaType == "image") "5 MB" else "15 MB"
                    Toast.makeText(context, "File size should be less than $limit", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Unsupported file type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f)) // Or your desired MessageInput background
    ) {
        AnimatedVisibility(visible = isEditing || replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if(isEditing) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply, // Changed for editing
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(headerText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    ReplyPreview(replyingTo = if(isEditing) editingMessage?.replyTo?.let { ReplyMeta(it.messageId, it.senderName, it.message, it.mediaType, it.thumbnailUrl) }?.let {
                        // This is a bit of a hack to fit ReplyMeta into Message structure for preview.
                        // Ideally, ReplyPreview would take ReplyMeta or adapt.
                        Message(id = "", senderId = "", senderName = it.senderName, message = it.message, mediaType = it.mediaType, thumbnailUrl = it.thumbnailUrl)
                    } else replyingTo)
                }
                IconButton(onClick = if(isEditing) onCancelEdit else onCancelReply) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Handles bottom navigation bar insets
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                enabled = !isUploading
            ) {
                Icon(AttachFile, contentDescription = "Attach Media")
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    // consider setting containerColor explicitly if needed
                ),
                enabled = !isUploading
            )
            IconButton(onClick = onSendMessage, enabled = !isUploading && value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ReplyPreview(replyingTo: Message?) { // Kept original signature, MessageInput adapts
    if (replyingTo?.thumbnailUrl != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = replyingTo.thumbnailUrl,
                contentDescription = "Reply preview",
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (replyingTo.mediaType == "image") "Photo" else if (replyingTo.mediaType == "video") "Video" else "Media",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }
    } else {
        Text(
            text = replyingTo?.message ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
    }
}

@Composable
fun ReplyPreviewInBubble(reply: ReplyMeta?) {
    if (reply?.thumbnailUrl != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = reply.thumbnailUrl,
                contentDescription = "Reply preview",
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (reply.mediaType == "image") "Photo" else if (reply.mediaType == "video") "Video" else "Media",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }
    } else {
        Text(
            text = reply?.message ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    navController: NavController,
    currentUserId: String?,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onReaction: (String) -> Unit,
    onUnsend: () -> Unit,
    onEdit: () -> Unit
) {
    val isCurrentUser = message.senderId == currentUserId
    val clipboardManager = LocalClipboardManager.current
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = spring(), label = "offsetX")
    val context = LocalContext.current
    val storageUtils = remember { SuperbaseStorageUtils(context) }


    val swipeProgress = (abs(animatedOffsetX) / 150f).coerceIn(0f, 1f)
    val replyIconAlpha by animateFloatAsState(targetValue = if (swipeProgress > 0.3f) 1f else 0f, label = "replyIconAlpha")


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(isCurrentUser) { // Pass key to reset if isCurrentUser changes, though unlikely here
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = 150f // Define threshold for triggering reply
                        if (isCurrentUser) { // Swiping left for own messages
                            if (offsetX < -threshold) onReply(message)
                        } else { // Swiping right for other's messages
                            if (offsetX > threshold) onReply(message)
                        }
                        offsetX = 0f // Reset offset
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offsetX + dragAmount
                        // Allow swipe only in one direction based on who sent the message
                        if (isCurrentUser) { // Own message, allow swipe left (negative offset)
                            if (newOffset < 0) offsetX = newOffset.coerceIn(-300f, 0f) // Limit swipe
                        } else { // Other's message, allow swipe right (positive offset)
                            if (newOffset > 0) offsetX = newOffset.coerceIn(0f, 300f) // Limit swipe
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Reply",
            tint = Color.White,
            modifier = Modifier
                .align(if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = replyIconAlpha
                    scaleX = swipeProgress
                    scaleY = swipeProgress
                    translationX = if (isCurrentUser) swipeProgress * -30f else swipeProgress * 30f // Move icon with swipe
                }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }, // Apply animated offset
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            // Dropdown Menu is anchored to the message content, not outside
            // This Box was for the expanded menu, let's keep it related to the bubble
            Box(
                modifier = Modifier
                    .align(if (isCurrentUser) Alignment.End else Alignment.Start)
            ) {
                // The actual message bubble content
                Column(
                    modifier = Modifier
                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.8f)
                        .combinedClickable(
                            onDoubleClick = { if (message.mediaType != "video") onReaction("❤️") }, // Example reaction
                            onLongClick = { onExpand() },
                            onClick = {
                                if (isExpanded) { // If menu is already expanded, clicking bubble content should dismiss it
                                    onDismiss()
                                } else if (message.mediaUrl != null) {
                                    val encodedUrl = URLEncoder.encode(message.mediaUrl, StandardCharsets.UTF_8.toString())
                                    navController.navigate("media_preview?mediaUrl=$encodedUrl&mediaType=${message.mediaType}")
                                }
                                // If not media and not expanded, click does nothing by default here
                            }
                        ),
                    horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
                ) {
                    message.replyTo?.let { reply ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(2.dp).height(30.dp).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(reply.senderName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                ReplyPreviewInBubble(reply = message.replyTo)
                            }
                        }
                    }
                    if (!isCurrentUser) {
                        Text(
                            text = message.senderName,
                            color = Color.LightGray, // Consider theming this color
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp, end = 12.dp) // ensure padding for sender name
                        )
                    }
                    MessageBubbleContent(message = message, isCurrentUser = isCurrentUser)
                }

                // DropdownMenu for reactions and actions, shown when isExpanded is true
                // Position it relative to the bubble content
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .align(if (isCurrentUser) Alignment.TopEnd else Alignment.TopStart) // Align with the bubble
                            .padding(top = 4.dp) // Spacing from bubble
                    ) {
                        ReactionPanel(onReaction = { emoji -> onReaction(emoji); onDismiss() }, modifier = Modifier.padding(bottom = 4.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column {
                                DropdownMenuItem(text = { Text("Copy") }, onClick = { clipboardManager.setText(AnnotatedString(message.message)); onDismiss() })
                                if (message.mediaUrl != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text("Save") }, onClick = {
                                        storageUtils.saveMediaToGallery(message.mediaUrl, message.mediaType ?: "file") { success ->
                                            val toastMessage = if (success) "Saved to gallery" else "Failed to save"
                                            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    })
                                }
                                if (isCurrentUser) {
                                    if(message.mediaType == null && message.message.isNotBlank()) { // Can only edit text messages
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { onEdit(); onDismiss() })
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text("Unsend") }, onClick = { onUnsend(); onDismiss() })
                                }
                            }
                        }
                    }
                }
            }


            if (message.reactions.isNotEmpty()) {
                val groupedReactions = message.reactions.values.groupingBy { it }.eachCount()
                FlowRow(
                    modifier = Modifier
                        .padding(start = if (isCurrentUser) 0.dp else 12.dp, top = 2.dp, end = if (isCurrentUser) 12.dp else 0.dp)
                        .align(if (isCurrentUser) Alignment.End else Alignment.Start)
                ) {
                    groupedReactions.forEach { (emoji, count) ->
                        Text(
                            text = "$emoji ${if (count > 1) count else ""}".trim(), // Show count only if > 1
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 2.dp) // Added bottom padding for spacing in FlowRow
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubbleContent(message: Message, isCurrentUser: Boolean) {
    val bubbleColor = if (isCurrentUser) Color(0xFF3797F0) else Color(0xFF2C2C2E) // Example colors
    val bubbleShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .background(color = bubbleColor, shape = bubbleShape)
            .clip(bubbleShape) // Ensure content respects the bubble shape
    ) {
        Column(
            modifier = Modifier.padding(
                start = if (message.mediaUrl != null) 4.dp else 12.dp,
                end = if (message.mediaUrl != null) 4.dp else 12.dp,
                top = if (message.mediaUrl != null) 4.dp else 8.dp,
                bottom = 4.dp // Consistent bottom padding
            )
        ) {
            if (message.mediaUrl != null) {
                MediaPreviewInChat(
                    message = message,
                    modifier = Modifier.padding(bottom = if (message.message.isNotBlank()) 4.dp else 0.dp)
                )
            }
            if(message.message.isNotBlank()) {
                Text(
                    text = message.message,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = if (message.mediaUrl != null) 8.dp else 0.dp) // Conditional horizontal padding for text
                )
            }
            // Spacer(modifier = Modifier.height(4.dp)) // Can be conditional if only text
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = if (message.message.isNotBlank() && message.mediaUrl == null) 4.dp else 0.dp), // Add space if only text
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isEdited) {
                    Text(
                        "(edited)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = formatTimestamp(message.createdAt),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(end = if (message.mediaUrl != null || message.message.isBlank()) 8.dp else 0.dp) // More padding if media or no text
                )
            }
        }
    }
}

@Composable
fun MediaPreviewInChat(message: Message, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(180.dp) // Consider making these configurable or dynamic
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp)), // Slightly less than bubble for inset look
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(message.thumbnailUrl ?: message.mediaUrl) // Fallback to mediaUrl if thumbnail is missing
                .crossfade(true)
                .placeholder(R.drawable.logo) // Replace with a generic placeholder
                .error(R.drawable.logo)       // Replace with a generic error placeholder
                .build(),
            contentDescription = "Media thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Overlay to darken the image slightly for better icon visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )
        if (message.mediaType == "video") {
            Icon(
                imageVector = Icons.Default.PlayCircleFilled, // Using filled icon for better visibility
                contentDescription = "Play Video",
                tint = Color.White.copy(alpha = 0.9f), // More opaque
                modifier = Modifier.size(50.dp)
            )
        }
    }
}


@Composable
fun ReactionPanel(onReaction: (String) -> Unit, modifier: Modifier = Modifier) {
    val emojis = listOf("❤️", "😂", "👍", "😢", "😠", "🔥") // Common reactions
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp), // Fully rounded ends
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Themed color
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    modifier = Modifier
                        .clickable { onReaction(emoji) }
                        .padding(8.dp), // Adequate touch target
                    fontSize = 24.sp // Larger emoji
                )
            }
        }
    }
}


fun shouldShowDateHeader(messages: List<Message>, index: Int): Boolean {
    if (index == 0) return true
    val currentMessage = messages[index]
    val previousMessage = messages[index - 1]
    if (currentMessage.createdAt == 0L || previousMessage.createdAt == 0L) return false // Handle uninitialized timestamps
    val currentCal = Calendar.getInstance().apply { timeInMillis = currentMessage.createdAt }
    val previousCal = Calendar.getInstance().apply { timeInMillis = previousMessage.createdAt }
    return currentCal.get(Calendar.YEAR) != previousCal.get(Calendar.YEAR) ||
            currentCal.get(Calendar.DAY_OF_YEAR) != previousCal.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun DateHeader(timestamp: Long) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = formatDateHeader(timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f), // Slightly more opaque for readability
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)) // Slightly darker background
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun formatDateHeader(timestamp: Long): String {
    val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()

    // Today
    if (messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
        return "Today"
    }

    // Yesterday
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
        return "Yesterday"
    }

    // Within the current year
    if (messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
        return SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp)) // e.g., "5 June"
    }

    // Different year
    return SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp)) // e.g., "5 June 2023"
}


private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "" // Handle cases where timestamp might be default/zero
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun VerifiedTick(size: Dp) {
    val gradientColors = listOf(
        Color(0xFF8A2BE2),
        Color(0xFFC71585),
        Color(0xFF4169E1)
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(VerifiedShape())
            .background(Brush.linearGradient(colors = gradientColors))
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified",
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f) // Icon is 60% of the shape size
        )
    }
}

class VerifiedShape(private val points: Int = 10) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius * 0.7f // Make inner points more pronounced
            val angleStep = (2 * Math.PI / (points * 2)).toFloat()

            moveTo(
                centerX + outerRadius * cos(-Math.PI.toFloat() / 2), // Start at the top point
                centerY + outerRadius * sin(-Math.PI.toFloat() / 2)
            )

            for (i in 1 until points * 2) {
                val currentRadius = if (i % 2 == 1) innerRadius else outerRadius
                val angle = (i * angleStep) - (Math.PI.toFloat() / 2)
                lineTo(
                    centerX + currentRadius * cos(angle),
                    centerY + currentRadius * sin(angle)
                )
            }
            close()
        })
    }
}

// Re-defined AttachFile Icon using Material Icons if available, or keep custom
private val AttachFile: ImageVector
    get() {
        // Prefer Material Icons if it fits the style
        // return Icons.Filled.AttachFile
        // If you need the exact custom one:
        if (_attachFile != null) {
            return _attachFile!!
        }
        _attachFile = ImageVector.Builder(
            name = "AttachFileCustom", // Renamed to avoid conflict if Material Icon is also used
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), // Assuming you want black, or use MaterialTheme.colorScheme.onSurface
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f
            ) {
                moveTo(16.5f, 6.0f)
                verticalLineToRelative(11.5f)
                curveToRelative(0.0f, 2.21f, -1.79f, 4.0f, -4.0f, 4.0f)
                reflectiveCurveToRelative(-4.0f, -1.79f, -4.0f, -4.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
                reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f)
                verticalLineToRelative(10.5f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(10.0f)
                verticalLineToRelative(9.5f)
                curveToRelative(0.0f, 1.38f, 1.12f, 2.5f, 2.5f, 2.5f)
                reflectiveCurveToRelative(2.5f, -1.12f, 2.5f, -2.5f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(7.0f, 2.79f, 7.0f, 5.0f)
                verticalLineToRelative(12.5f)
                curveToRelative(0.0f, 3.04f, 2.46f, 5.5f, 5.5f, 5.5f)
                reflectiveCurveToRelative(5.5f, -2.46f, 5.5f, -5.5f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-1.5f)
                close()
            }
        }.build()
        return _attachFile!!
    }

private var _attachFile: ImageVector? = null

