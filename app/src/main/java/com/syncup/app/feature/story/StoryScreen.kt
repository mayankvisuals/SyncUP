package com.syncup.app.feature.story

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.syncup.app.feature.home.HomeViewModel
import com.syncup.app.feature.search.UserListItem
import com.syncup.app.model.Story
import com.syncup.app.model.User
import com.syncup.app.model.UserStory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    navController: NavController
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val storyViewModel: StoryViewModel = hiltViewModel()
    val stories by homeViewModel.stories.collectAsState()
    val initialPage by storyViewModel.initialPage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (stories.isEmpty() || stories.size <= initialPage) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { stories.size }
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        val userStory = stories[pageIndex]
        StoryContent(
            userStory = userStory,
            storyViewModel = storyViewModel,
            onClose = { navController.popBackStack() },
            onNextUser = {
                if (pageIndex < stories.size - 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pageIndex + 1)
                    }
                } else {
                    navController.popBackStack()
                }
            },
            onPrevUser = {
                if (pageIndex > 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                } else {
                    navController.popBackStack()
                }
            }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoryContent(
    userStory: UserStory,
    storyViewModel: StoryViewModel,
    onClose: () -> Unit,
    onNextUser: () -> Unit,
    onPrevUser: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { userStory.stories.size })
    var isPaused by remember { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    var showViewsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val viewers by storyViewModel.viewers.collectAsState()

    val visualPlayer = remember { ExoPlayer.Builder(context).build() }
    var youtubePlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }


    var currentProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(durationMillis = 5000), label = ""
    )

    // YouTube player ke liye listener, taaki gaana play hote hi signal de.
    val youtubeListener = remember {
        object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                youtubePlayer = player
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                // Jab gaana play ho toh 'isMusicPlaying' ko true set karein.
                isMusicPlaying = state == PlayerConstants.PlayerState.PLAYING
                if (state == PlayerConstants.PlayerState.VIDEO_CUED) {
                    youTubePlayer.play()
                }
            }
        }
    }


    // Yeh effect story ke playback ko control karta hai.
    LaunchedEffect(pagerState.currentPage, isPaused, youtubePlayer) {
        val player = youtubePlayer ?: return@LaunchedEffect

        if (isPaused) {
            visualPlayer.pause()
            player.pause()
            return@LaunchedEffect
        }

        // Nayi story ke liye state reset karein.
        currentProgress = 0f
        isMusicPlaying = false
        visualPlayer.stop()
        player.pause()

        val currentStory = userStory.stories[pagerState.currentPage]
        storyViewModel.markStoryAsViewed(currentStory, userStory.user.id)

        val musicTrack = currentStory.musicTrack
        val hasMusic = musicTrack != null

        // Music aur video players ko taiyaar karein.
        if (hasMusic) {
            visualPlayer.volume = 0f
            player.loadVideo(musicTrack.id, (musicTrack.startTimeMs / 1000f))
        } else {
            visualPlayer.volume = 1f
        }

        if (currentStory.mediaType == "video") {
            visualPlayer.setMediaItem(MediaItem.fromUri(currentStory.mediaUrl))
            visualPlayer.prepare()
            // Agar music nahi hai toh video turant play karein.
            if (!hasMusic) {
                visualPlayer.playWhenReady = true
            }
        }

        // LATENCY FIX: Yahan code tab tak rukega jab tak gaana play na ho.
        if (hasMusic) {
            // 'isMusicPlaying' ke true hone ka intezaar karein.
            snapshotFlow { isMusicPlaying }.first { it }
            // Music shuru hone ke baad video play karein.
            if (currentStory.mediaType == "video") {
                visualPlayer.playWhenReady = true
            }
        }

        // Ab jab sab sync ho chuka hai, tab progress bar aur timer shuru karein.
        val storyDuration = 5000L
        currentProgress = 0f
        delay(50) // Animation shuru karne ke liye chota delay.
        currentProgress = 1f

        delay(storyDuration)

        // Agli story par jaayein.
        if (pagerState.currentPage < userStory.stories.size - 1) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        } else {
            onNextUser()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            visualPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 3) {
                            coroutineScope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                } else {
                                    onPrevUser()
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                if (pagerState.currentPage < userStory.stories.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                } else {
                                    onNextUser()
                                }
                            }
                        }
                    },
                    onLongPress = { isPaused = true },
                    onPress = {
                        awaitRelease()
                        isPaused = false
                    }
                )
            }
    ) {
        AndroidView(factory = {
            YouTubePlayerView(it).apply {
                addYouTubePlayerListener(youtubeListener)
            }
        }, modifier = Modifier.size(0.dp))

        val currentStory = userStory.stories[pagerState.currentPage]
        if (currentStory.mediaType == "image") {
            AsyncImage(model = currentStory.mediaUrl, contentDescription = "Story Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            AndroidView({ PlayerView(it).apply { player = visualPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
        }
        // --- BUG FIX: UI elements ko transparent background diya gaya hai ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 8.dp, end = 8.dp)
            ) {
                userStory.stories.forEachIndexed { index, _ ->
                    LinearProgressIndicator(
                        progress = {
                            when {
                                index < pagerState.currentPage -> 1f
                                index == pagerState.currentPage -> animatedProgress
                                else -> 0f
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
            StoryHeader(
                user = userStory.user,
                onClose = onClose
            )
        }

        currentStory.musicTrack?.let { track ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = track.thumbnailUrl, contentDescription = "Album art", modifier = Modifier.size(40.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(track.title, color = Color.White, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(track.artist, color = Color.Gray, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }

        if (userStory.user.id == currentUserId) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable {
                        storyViewModel.fetchStoryViewers(currentStory)
                        showViewsSheet = true
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Views", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentStory.viewedBy.size} views",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showViewsSheet) {
        val currentStory = userStory.stories[pagerState.currentPage]
        ModalBottomSheet(
            onDismissRequest = { showViewsSheet = false },
            sheetState = sheetState
        ) {
            StoryViewsBottomSheet(
                viewers = viewers,
                onDelete = {
                    storyViewModel.deleteStory(currentStory, userStory.user.id) {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            showViewsSheet = false
                            if (pagerState.currentPage < userStory.stories.size - 1) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onClose()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun StoryViewsBottomSheet(viewers: List<User>, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Viewed By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Story", tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (viewers.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("No views yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(viewers) { user ->
                    UserListItem(user = user, onClick = {})
                }
            }
        }
    }
}

@Composable
fun StoryHeader(user: User, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = "User profile",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close Story", tint = Color.White)
        }
    }
}
