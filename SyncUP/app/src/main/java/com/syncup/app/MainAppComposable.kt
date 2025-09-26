package com.syncup.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.syncup.app.feature.auth.signin.SignInScreen
import com.syncup.app.feature.auth.signup.SignUpScreen
import com.syncup.app.feature.chat.ChatScreen
import com.syncup.app.feature.home.HomeScreen

@Composable
fun MainApp(
    startChannelId: String? = null,
    startChannelName: String? = null // Naya parameter
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        val navController = rememberNavController()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val startDestination = if (currentUser != null) "home" else "login"

        // Notification se aane par direct navigate
        LaunchedEffect(key1 = startChannelId) {
            if (startChannelId != null) {
                // Hum ab channelId aur channelName dono ke saath navigate karenge
                navController.navigate("chat/$startChannelId/${startChannelName ?: "Chat"}")
            }
        }

        NavHost(navController = navController, startDestination = startDestination) {
            composable("login") { SignInScreen(navController) }
            composable("signup") { SignUpScreen(navController) }
            composable("home") { HomeScreen(navController) }

            // --- NAVIGATION FIX ---
            composable(
                // Route me ab channelName bhi hai
                route = "chat/{channelId}/{channelName}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("channelName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                val channelName = backStackEntry.arguments?.getString("channelName") ?: "Chat"
                // ChatScreen ko ab dono cheezein bhejenge
                ChatScreen(navController, channelId, channelName)
            }
        }
    }
}

