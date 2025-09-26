package com.syncup.app.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.syncup.app.feature.search.UserListItem
import com.syncup.app.model.User
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val user by viewModel.userProfile.collectAsState()
    val followers by viewModel.followers.collectAsState()
    val following by viewModel.following.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showFollowersDialog by remember { mutableStateOf(false) }
    var showFollowingDialog by remember { mutableStateOf(false) }

    Scaffold(
        // --- BUG FIX: Consistent background color ---
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(user?.username?.let { "@$it" } ?: "Profile") }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (user != null) {
            val currentUser = user!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                ProfileHeader(
                    user = currentUser,
                    onEditProfileClicked = { navController.navigate("edit_profile") }
                )
                Spacer(modifier = Modifier.height(24.dp))
                FollowerFollowingSection(
                    user = currentUser,
                    onFollowersClicked = { showFollowersDialog = true },
                    onFollowingClicked = { showFollowingDialog = true }
                )
                // Yahan par future me mutuals ka section aayega
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not load profile.")
            }
        }
    }

    if (showFollowersDialog) {
        UserListDialog(
            title = "Followers",
            users = followers,
            navController = navController,
            onDismiss = { showFollowersDialog = false }
        )
    }

    if (showFollowingDialog) {
        UserListDialog(
            title = "Following",
            users = following,
            navController = navController,
            onDismiss = { showFollowingDialog = false }
        )
    }
}

@Composable
fun ProfileHeader(user: User, onEditProfileClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
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
                    Icon(Icons.Default.Person, contentDescription = "Default Photo", modifier = Modifier.size(50.dp), tint = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (user.verified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedTick(size = 20.dp)
                    }
                }
                Text(text = "@${user.username}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = user.bio, style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(onClick = onEditProfileClicked) {
            Text("Edit Profile")
        }
    }
}

@Composable
fun FollowerFollowingSection(
    user: User,
    onFollowersClicked: () -> Unit,
    onFollowingClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onFollowersClicked)
        ) {
            Text(text = user.followersCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Followers", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onFollowingClicked)
        ) {
            Text(text = user.followingCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Following", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}


@Composable
fun UserListDialog(
    title: String,
    users: List<User>,
    navController: NavController,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            if (users.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp), contentAlignment = Alignment.Center
                ) {
                    Text("No users to show.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(users, key = { it.id }) { user ->
                        UserListItem(user = user) {
                            onDismiss() // Dismiss the dialog before navigating
                            navController.navigate("user_profile/${user.id}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        modifier = Modifier.padding(vertical = 16.dp)
    )
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
