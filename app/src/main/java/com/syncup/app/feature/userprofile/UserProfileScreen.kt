package com.syncup.app.feature.userprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.syncup.app.model.User
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(navController: NavController, userId: String) {
    val viewModel: UserProfileViewModel = hiltViewModel()
    val user by viewModel.userProfile.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isLoadingFollow by viewModel.isLoadingFollow.collectAsState()
    val isCurrentUserProfile = FirebaseAuth.getInstance().currentUser?.uid == userId

    LaunchedEffect(userId) {
        viewModel.fetchUserProfile(userId)
    }

    Scaffold(
        // --- BUG FIX: Consistent background color ---
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentUserProfile = user
        if (currentUserProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            UserProfileContent(
                user = currentUserProfile,
                isCurrentUserProfile = isCurrentUserProfile,
                isFollowing = isFollowing,
                isLoadingFollow = isLoadingFollow,
                onToggleFollow = { viewModel.toggleFollow() },
                modifier = Modifier.padding(paddingValues)
            ) {
                viewModel.startPersonalChat(currentUserProfile) { channelId, channelName ->
                    navController.navigate("chat/$channelId/$channelName")
                }
            }
        }
    }
}

@Composable
fun UserProfileContent(
    user: User,
    isCurrentUserProfile: Boolean,
    isFollowing: Boolean,
    isLoadingFollow: Boolean,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile picture
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(user.photoUrl).crossfade(true).build(),
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = "Default Photo", modifier = Modifier.size(80.dp), tint = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Name and verified tick
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (user.verified) {
                Spacer(modifier = Modifier.width(6.dp))
                VerifiedTick(size = 24.dp)
            }
        }
        Text(text = "@${user.username}", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // --- FOLLOWER/FOLLOWING COUNT UI ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FollowCount(count = user.followersCount, label = "Followers")
            FollowCount(count = user.followingCount, label = "Following")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Bio
        Text(text = user.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        if (!isCurrentUserProfile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Follow/Unfollow Button
                Button(
                    onClick = onToggleFollow,
                    enabled = !isLoadingFollow,
                    modifier = Modifier.weight(1f),
                    colors = if (isFollowing) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                ) {
                    if (isLoadingFollow) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (isFollowing) "Unfollow" else "Follow")
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Message Button
                OutlinedButton(onClick = onMessageClick, modifier = Modifier.weight(1f)) {
                    Text("Message")
                }
            }
        }
    }
}

@Composable
fun FollowCount(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}


@Composable
fun VerifiedTick(size: Dp) {
    val gradientColors = listOf(
        Color(0xFF8A2BE2), Color(0xFFC71585), Color(0xFF4169E1)
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
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius * 0.7f
            val angleStep = (2 * Math.PI / (points * 2)).toFloat()
            moveTo(centerX + outerRadius * cos(-Math.PI.toFloat() / 2), centerY + outerRadius * sin(-Math.PI.toFloat() / 2))
            for (i in 1 until points * 2) {
                val currentRadius = if (i % 2 == 1) innerRadius else outerRadius
                val angle = (i * angleStep) - (Math.PI.toFloat() / 2)
                lineTo(centerX + currentRadius * cos(angle), centerY + currentRadius * sin(angle))
            }
            close()
        })
    }
}
