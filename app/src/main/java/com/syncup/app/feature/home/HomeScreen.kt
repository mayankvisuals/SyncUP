package com.syncup.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.syncup.app.model.Channel
import com.syncup.app.model.PersonalChat
import com.syncup.app.model.User
import com.syncup.app.model.UserStory
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavController, innerPadding: PaddingValues) {
    val viewModel: HomeViewModel = hiltViewModel()
    val channels by viewModel.channels.collectAsState()
    val personalChats by viewModel.personalChats.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()

    var showAddChannelDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val tabTitles = listOf("Messages", "Channels")

    val storyMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
            navController.navigate("create_story/$encodedUri")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SyncUp",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // --- SHIFT FIX: IconButton ab hamesha layout me rahega, bas dikhega nahi ---
            // Isse header ki height constant rehti hai aur screen shift nahi hoti.
            IconButton(
                onClick = { showAddChannelDialog = true },
                enabled = pagerState.currentPage == 1
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Channel",
                    tint = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (pagerState.currentPage == 1) 1f else 0f
                    )
                )
            }
        }

        StoriesSection(
            stories = stories,
            currentUser = currentUserProfile,
            onStoryClick = { index ->
                navController.navigate("story/$index")
            },
            onAddStoryClick = {
                storyMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(text = title) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> PersonalChatsList(personalChats, navController, viewModel)
                1 -> GroupChannelsList(channels, navController)
            }
        }
    }

    if (showAddChannelDialog) {
        AddChannelDialog(
            onDismiss = { showAddChannelDialog = false },
            onAddChannel = { channelName ->
                viewModel.addChannel(channelName)
                showAddChannelDialog = false
            }
        )
    }
}

@Composable
fun StoriesSection(
    stories: List<UserStory>,
    currentUser: User?,
    onStoryClick: (Int) -> Unit,
    onAddStoryClick: () -> Unit
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val currentUserStoryIndex = stories.indexOfFirst { it.user.id == currentUserId }
            AddStoryCircle(
                userPhotoUrl = currentUser?.photoUrl,
                hasStory = currentUserStoryIndex != -1,
                onClick = {
                    if (currentUserStoryIndex != -1) {
                        onStoryClick(currentUserStoryIndex)
                    } else {
                        onAddStoryClick()
                    }
                }
            )
        }

        items(stories.size) { index ->
            val userStory = stories[index]
            if (userStory.user.id != currentUserId) {
                StoryCircle(userStory = userStory, onClick = { onStoryClick(index) })
            }
        }
    }
}

@Composable
fun AddStoryCircle(userPhotoUrl: String?, hasStory: Boolean, onClick: () -> Unit) {
    val storyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF8A2BE2), Color(0xFFC71585), Color(0xFF4169E1))
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val borderModifier = if (hasStory) {
                Modifier.border(width = 2.5.dp, brush = storyGradient, shape = CircleShape)
            } else {
                Modifier
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(userPhotoUrl)
                    .error(android.R.drawable.sym_def_app_icon)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .crossfade(true)
                    .build(),
                contentDescription = "Your Profile",
                modifier = Modifier
                    .fillMaxSize()
                    .then(borderModifier)
                    .padding(4.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            if (!hasStory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Story",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Text("Your Story", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
    }
}


@Composable
fun StoryCircle(userStory: UserStory, onClick: () -> Unit) {
    val storyGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF8A2BE2), Color(0xFFC71585), Color(0xFF4169E1))
    )
    val hasUnreadStories = true

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = if (hasUnreadStories) storyGradient else SolidColor(Color.Gray),
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(userStory.user.photoUrl)
                    .error(android.R.drawable.sym_def_app_icon)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .crossfade(true)
                    .build(),
                contentDescription = "User Story",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = userStory.user.username,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(70.dp),
            textAlign = TextAlign.Center
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonalChatsList(chats: List<PersonalChat>, navController: NavController, viewModel: HomeViewModel) {
    var selectedChatForMenu by remember { mutableStateOf<PersonalChat?>(null) }
    var showHideConfirmDialog by remember { mutableStateOf(false) }

    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No personal chats yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(chats, key = { it.channelId }) { chat ->
                Box {
                    PersonalChatListItem(
                        chat = chat,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                navController.navigate("chat/${chat.channelId}/${chat.otherUser?.name ?: "Chat"}")
                            },
                            onLongClick = {
                                selectedChatForMenu = chat
                            }
                        )
                    )

                    DropdownMenu(
                        expanded = selectedChatForMenu == chat,
                        onDismissRequest = { selectedChatForMenu = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (chat.isMuted) "Unmute" else "Mute") },
                            onClick = {
                                viewModel.toggleMutePersonalChat(chat.channelId, chat.isMuted)
                                selectedChatForMenu = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hide Chat") },
                            onClick = {
                                showHideConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }
    }


    if (showHideConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHideConfirmDialog = false },
            title = { Text("Hide Chat") },
            text = { Text("Are you sure? This chat will reappear if you or the other person sends a new message.") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedChatForMenu?.let {
                            viewModel.hidePersonalChat(it.channelId)
                        }
                        showHideConfirmDialog = false
                        selectedChatForMenu = null
                    }) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChannelsList(channels: List<Channel>, navController: NavController) {
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No group channels yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelListItem(
                    channel = channel,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            navController.navigate("chat/${channel.id}/${channel.name}")
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun PersonalChatListItem(chat: PersonalChat, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chat.otherUser?.photoUrl)
                    .crossfade(true)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .error(android.R.drawable.sym_def_app_icon)
                    .build(),
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.otherUser?.name ?: "Unknown User",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (chat.otherUser?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedTick(size = 17.dp)
                    }
                    if (chat.isMuted) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", modifier = Modifier.size(16.dp), tint = Color.Gray)
                    }
                }
                Text(
                    text = chat.lastMessage,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatTimestamp(chat.timestamp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChannelListItem(channel: Channel, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Group Icon",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = channel.lastMessage.ifEmpty { "Tap to start chatting..." },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatTimestamp(channel.lastMessageTimestamp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddChannelDialog(onDismiss: () -> Unit, onAddChannel: (String) -> Unit) {
    var channelName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a new channel") },
        text = {
            OutlinedTextField(
                value = channelName,
                onValueChange = { channelName = it },
                label = { Text("Channel Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelName.isNotBlank()) {
                        onAddChannel(channelName)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(date)
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
            modifier = Modifier.size(size * 0.6f)
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
            val innerRadius = outerRadius * 0.7f
            val angleStep = (2 * Math.PI / (points * 2)).toFloat()

            moveTo(
                centerX + outerRadius * cos(-Math.PI.toFloat() / 2),
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

