package com.syncup.app.feature.story

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.syncup.app.model.MusicTrack
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    navController: NavController,
    mediaUriString: String
) {
    val createStoryViewModel: CreateStoryViewModel = hiltViewModel()
    val musicSearchViewModel: MusicSearchViewModel = hiltViewModel()
    val context = LocalContext.current

    val mediaUri = remember { Uri.parse(mediaUriString) }
    var selectedMusicTrack by remember { mutableStateOf<MusicTrack?>(null) }
    val isUploading by createStoryViewModel.isUploading.collectAsState()
    val uploadSuccess by createStoryViewModel.uploadSuccess.collectAsState()

    var showMusicSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val mimeType = context.contentResolver.getType(mediaUri)

    val visualPlayer = remember { ExoPlayer.Builder(context).build() }
    var youtubePlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    var musicDuration by remember { mutableFloatStateOf(0f) }
    var musicTrimPosition by remember { mutableFloatStateOf(0f) }

    visualPlayer.volume = if (selectedMusicTrack != null) 0f else 1f

    LaunchedEffect(uploadSuccess) {
        if (uploadSuccess) {
            navController.popBackStack()
            createStoryViewModel.resetUploadStatus()
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
    ) {
        AndroidView(
            factory = {
                YouTubePlayerView(it).apply {
                    val options = IFramePlayerOptions.Builder().controls(0).build()
                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(player: YouTubePlayer) {
                            youtubePlayer = player
                        }
                        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                            musicDuration = duration
                        }
                    })
                }
            },
            modifier = Modifier.size(0.dp)
        )

        // Media Preview
        if (mimeType?.startsWith("image/") == true) {
            AsyncImage(
                model = mediaUri,
                contentDescription = "Story Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (mimeType?.startsWith("video/") == true) {
            DisposableEffect(mediaUri) {
                visualPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
                visualPlayer.prepare()
                visualPlayer.playWhenReady = true
                visualPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                onDispose { }
            }
            AndroidView({ PlayerView(it).apply { player = visualPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
        }

        // UI Controls
        CreateStoryUI(
            onClose = { navController.popBackStack() },
            onAddMusic = { showMusicSheet = true },
            onUpload = {
                val finalTrack = selectedMusicTrack?.copy(startTimeMs = (musicTrimPosition * 1000).roundToLong())
                createStoryViewModel.uploadStory(mediaUri, finalTrack)
            },
            isUploading = isUploading,
            selectedMusicTrack = selectedMusicTrack,
            musicDuration = musicDuration,
            musicTrimPosition = musicTrimPosition,
            onTrimChanged = { newPosition ->
                musicTrimPosition = newPosition
                youtubePlayer?.seekTo(newPosition)
            }
        )
    }

    if (showMusicSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMusicSheet = false },
            sheetState = sheetState
        ) {
            MusicSearchScreen(
                viewModel = musicSearchViewModel,
                onMusicSelected = { track ->
                    selectedMusicTrack = track
                    youtubePlayer?.loadVideo(track.id, 0f)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showMusicSheet = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CreateStoryUI(
    onClose: () -> Unit,
    onAddMusic: () -> Unit,
    onUpload: () -> Unit,
    isUploading: Boolean,
    selectedMusicTrack: MusicTrack?,
    musicDuration: Float,
    musicTrimPosition: Float,
    onTrimChanged: (Float) -> Unit
) {
    // Top controls
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        IconButton(onClick = onAddMusic) {
            Icon(Icons.Default.MusicNote, contentDescription = "Add Music", tint = Color.White)
        }
    }

    // Music Info and Trimmer
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        selectedMusicTrack?.let { track ->
            Card(
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
            Spacer(modifier = Modifier.height(24.dp))
            if (musicDuration > 0) {
                Slider(
                    value = musicTrimPosition,
                    onValueChange = onTrimChanged,
                    valueRange = 0f..musicDuration,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }

    // Upload Button
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else {
            FloatingActionButton(
                onClick = onUpload,
                modifier = Modifier.padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Send, contentDescription = "Upload Story", tint = Color.White)
            }
        }
    }
}

