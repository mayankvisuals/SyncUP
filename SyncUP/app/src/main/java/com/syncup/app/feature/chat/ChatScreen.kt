package com.syncup.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.syncup.app.R
import com.syncup.app.model.Message
import com.syncup.app.utils.ActiveChannelManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, channelId: String, channelName: String) {
    val viewModel: ChatViewModel = hiltViewModel()
    val messages by viewModel.messages.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

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
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // --- WALLPAPER FIX: Box wapas add kar diya gaya hai ---
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.my_wallpaper),
            contentDescription = "Chat background wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent, // Taaki wallpaper dikhe
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(channelName, color = Color.White)
                            AnimatedVisibility(visible = typingUsers.isNotEmpty()) {
                                val typingText = when {
                                    typingUsers.size == 1 -> "${typingUsers.first()} is typing..."
                                    else -> "${typingUsers.take(2).joinToString(" & ")} are typing..."
                                }
                                Text(typingText, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.3f))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(
                            message = message,
                            onReply = { viewModel.setReplyingTo(it) }
                        )
                    }
                }
                val lastMessage = messages.lastOrNull { it.senderId == viewModel.currentUser?.uid }
                if (lastMessage != null) {
                    val seenCount = lastMessage.seenBy.size - 1
                    if (seenCount > 0) {
                        Text(
                            text = "✓ Seen",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp, bottom = 4.dp)
                        )
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
                            viewModel.sendMessage(channelId, channelName, messageText.trim())
                            messageText = ""
                        }
                    },
                    replyingTo = replyingTo,
                    onCancelReply = { viewModel.setReplyingTo(null) }
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    replyingTo: Message?,
    onCancelReply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(message.senderName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Text(message.message, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Reply", tint = Color.Gray)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
            IconButton(onClick = onSendMessage) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message, onReply: (Message) -> Unit) {
    val isCurrentUser = message.senderId == Firebase.auth.currentUser?.uid
    val bubbleColor = if (isCurrentUser) Color(0xFF3797F0) else Color(0xFF2C2C2E)
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = spring(), label = "")
    val swipeOffset = if (animatedOffsetX > 0) animatedOffsetX else 0f
    val replyIconAlpha by animateFloatAsState(targetValue = if (swipeOffset > 20f) 1f else 0f, animationSpec = spring(), label = "")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150f) { onReply(message) }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val newOffset = offsetX + dragAmount
                        if (newOffset > 0) { offsetX = newOffset.coerceIn(0f, 300f) }
                    }
                )
            },
        contentAlignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Reply",
            tint = Color.White,
            modifier = Modifier
                .align(if (isCurrentUser) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 16.dp)
                .offset { IntOffset((swipeOffset / 3).roundToInt(), 0) }
                .graphicsLayer { alpha = replyIconAlpha }
        )
        Column(
            modifier = Modifier.offset { IntOffset(swipeOffset.roundToInt(), 0) },
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
                        Text(reply.message, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }

            if (!isCurrentUser) {
                Text(
                    text = message.senderName,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                if (isCurrentUser) {
                    Text(text = formatTimestamp(message.createdAt), color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                }
                Box(
                    modifier = Modifier
                        .background(color = bubbleColor, shape = RoundedCornerShape(16.dp))
                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = message.message, color = Color.White)
                }
                if (!isCurrentUser) {
                    Text(text = formatTimestamp(message.createdAt), color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

