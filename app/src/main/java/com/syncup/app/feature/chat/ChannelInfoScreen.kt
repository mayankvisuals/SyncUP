package com.syncup.app.feature.chat.channelinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.auth
import com.syncup.app.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoScreen(navController: NavController, channelId: String) {
    val viewModel: ChannelInfoViewModel = hiltViewModel()
    val channel by viewModel.channelDetails.collectAsState()
    val members by viewModel.members.collectAsState()
    val potentialMembers by viewModel.potentialMembers.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) {
        viewModel.loadChannelInfo(channelId)
    }

    Scaffold(
        // --- BUG FIX: Consistent background color ---
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Group Icon",
                        modifier = Modifier.size(70.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = channel?.name ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${members.size} members",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Mute and Leave options
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.toggleMuteChannel(channelId) }
                    ) {
                        Icon(if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications, contentDescription = "Mute")
                        Text(if(isMuted) "Unmute" else "Mute", fontSize = 12.sp)
                    }

                    // Owner group chhod nahi sakta
                    if (currentUserRole != "owner") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showLeaveConfirmDialog = true }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave", tint = MaterialTheme.colorScheme.error)
                            Text("Leave", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }


            // Members List Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Members",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentUserRole == "owner" || currentUserRole == "admin") {
                        TextButton(onClick = { showAddMembersDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Members")
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Add")
                        }
                    }
                }
            }

            // Members List
            items(members, key = { it.id }) { member ->
                UserListItem(
                    user = member,
                    currentUserRole = currentUserRole,
                    onClick = {
                        // Agar user khud nahi hai to uske profile par navigate karo
                        if(member.id != viewModel.currentUser?.uid) {
                            navController.navigate("user_profile/${member.id}")
                        }
                    },
                    onKick = { viewModel.kickMember(channelId, member.id) },
                    onPromote = { viewModel.promoteToAdmin(channelId, member.id) },
                    onDemote = { viewModel.demoteToMember(channelId, member.id) }
                )
            }
        }
    }

    if (showAddMembersDialog) {
        AddMembersDialog(
            potentialMembers = potentialMembers,
            onDismiss = { showAddMembersDialog = false },
            onAddMembers = { selectedIds ->
                viewModel.addMembersToChannel(channelId, selectedIds)
                showAddMembersDialog = false
            }
        )
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this group?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.leaveChannel(channelId) {
                            navController.popBackStack("home", inclusive = false)
                        }
                        showLeaveConfirmDialog = false
                    }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddMembersDialog(
    potentialMembers: List<User>,
    onDismiss: () -> Unit,
    onAddMembers: (List<String>) -> Unit
) {
    var selectedUserIds by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Members") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(potentialMembers, key = { it.id }) { user ->
                    val isSelected = selectedUserIds.contains(user.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUserIds = if (isSelected) {
                                    selectedUserIds - user.id
                                } else {
                                    selectedUserIds + user.id
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(user.photoUrl).crossfade(true).build(),
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(user.name, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddMembers(selectedUserIds.toList()) },
                enabled = selectedUserIds.isNotEmpty()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun UserListItem(
    user: User,
    currentUserRole: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onKick: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val currentUserId = com.google.firebase.Firebase.auth.currentUser?.uid

    // Yeh conditions decide karengi ki menu me kya dikhega
    val canKick = (currentUserRole == "owner" && user.role != "owner") || (currentUserRole == "admin" && user.role == "member")
    val canPromote = currentUserRole == "owner" && user.role == "member"
    val canDemote = currentUserRole == "owner" && user.role == "admin"
    val showMoreIcon = (canKick || canPromote || canDemote) && user.id != currentUserId

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.photoUrl)
                .crossfade(true)
                .placeholder(android.R.drawable.sym_def_app_icon)
                .error(android.R.drawable.sym_def_app_icon)
                .build(),
            contentDescription = "Profile Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if(user.verified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedTick(size = 16.dp)
                }

                if (user.role == "owner" || user.role == "admin") {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = user.role.replaceFirstChar { it.uppercase() },
                        color = if (user.role == "owner") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }

        if (showMoreIcon) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (canPromote) {
                        DropdownMenuItem(text = { Text("Promote to Admin") }, onClick = {
                            onPromote()
                            showMenu = false
                        })
                    }
                    if (canDemote) {
                        DropdownMenuItem(text = { Text("Demote to Member") }, onClick = {
                            onDemote()
                            showMenu = false
                        })
                    }
                    if (canKick) {
                        DropdownMenuItem(text = { Text("Kick Member", color = MaterialTheme.colorScheme.error) }, onClick = {
                            onKick()
                            showMenu = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun VerifiedTick(size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF3797F0)) // Blue color
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified",
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f)
        )
    }
}
