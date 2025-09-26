package com.syncup.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.google.firebase.auth.FirebaseAuth
import com.syncup.app.feature.auth.signin.SignInScreen
import com.syncup.app.feature.auth.signup.SignUpScreen
import com.syncup.app.feature.chat.ChatScreen
import com.syncup.app.feature.chat.MediaPreviewScreen
import com.syncup.app.feature.chat.channelinfo.ChannelInfoScreen
import com.syncup.app.feature.home.HomeScreen
import com.syncup.app.feature.notifications.NotificationScreen
import com.syncup.app.feature.profile.EditProfileScreen
import com.syncup.app.feature.profile.ProfileScreen
import com.syncup.app.feature.search.SearchScreen
import com.syncup.app.feature.story.CreateStoryScreen
import com.syncup.app.feature.story.StoryScreen
import com.syncup.app.feature.userprofile.UserProfileScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val label: String? = null, val icon: @Composable (() -> Unit)? = null) {
    object Home : Screen("home", "Chats", { Icon(Icons.Default.Chat, contentDescription = "Chats") })
    object Search : Screen("search", "Search", { Icon(Icons.Default.Search, contentDescription = "Search") })
    object Activity : Screen("activity", "Activity", { Icon(Icons.Default.Favorite, contentDescription = "Activity") })
    object Profile : Screen("profile", "Profile", { Icon(Icons.Default.Person, contentDescription = "Profile") })
}

sealed class MainScreen(val route: String) {
    object Main : MainScreen("main")
}

val bottomBarRoutes = setOf(Screen.Home.route, Screen.Search.route, Screen.Activity.route, Screen.Profile.route)

@Composable
fun MainApp(
    startChannelId: String? = null,
    startChannelName: String? = null
) {
    val navController = rememberNavController()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = if (currentUser != null) MainScreen.Main.route else "login"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(key1 = startChannelId) {
        if (startChannelId != null) {
            navController.navigate("chat/$startChannelId/${startChannelName ?: "Chat"}")
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                AppBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = startDestination,
            // JUMP FIX: We DO NOT apply the scaffold's padding to the NavHost.
            // This makes the NavHost's size static, preventing the jump.
            // Each screen will now be responsible for its own padding.
            modifier = Modifier
        ) {
            composable("login") { SignInScreen(navController) }
            composable("signup") { SignUpScreen(navController) }

            navigation(startDestination = Screen.Home.route, route = MainScreen.Main.route) {
                // We now pass the main scaffold's padding down to the screens that need it.
                composable(Screen.Home.route) { HomeScreen(navController, innerPadding) }
                composable(Screen.Search.route) { SearchScreen(navController) }
                composable(Screen.Activity.route) { NotificationScreen(navController) }
                composable(Screen.Profile.route) { ProfileScreen(navController) }
            }

            composable(
                route = "chat/{channelId}/{channelName}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("channelName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                val channelName = backStackEntry.arguments?.getString("channelName") ?: "Chat"
                ChatScreen(navController, channelId, channelName)
            }
            composable(
                route = "user_profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                UserProfileScreen(navController, userId)
            }
            composable(
                route = "channel_info/{channelId}",
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                ChannelInfoScreen(navController, channelId)
            }
            composable("edit_profile") { EditProfileScreen(navController) }

            composable(
                route = "media_preview?mediaUrl={mediaUrl}&mediaType={mediaType}",
                arguments = listOf(
                    navArgument("mediaUrl") { type = NavType.StringType },
                    navArgument("mediaType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("mediaUrl")?.let {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                } ?: ""
                val type = backStackEntry.arguments?.getString("mediaType") ?: ""
                MediaPreviewScreen(navController, url, type)
            }

            composable(
                route = "story/{storyStartIndex}",
                arguments = listOf(navArgument("storyStartIndex") { type = NavType.IntType; defaultValue = 0 })
            ) {
                StoryScreen(navController = navController)
            }

            composable(
                route = "create_story/{mediaUri}",
                arguments = listOf(navArgument("mediaUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("mediaUri") ?: ""
                val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
                CreateStoryScreen(navController, decodedUri)
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val items = listOf(Screen.Home, Screen.Search, Screen.Activity, Screen.Profile)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        items.forEach { screen ->
            NavigationBarItem(
                icon = { screen.icon?.invoke() },
                label = { Text(screen.label ?: "") },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

