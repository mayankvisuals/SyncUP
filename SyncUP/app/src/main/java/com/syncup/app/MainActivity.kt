package com.syncup.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.syncup.app.ui.theme.SyncUPTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Notification permission ke liye launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Yahan aap handle kar sakte hain ki permission mili ya nahi
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()

        // App khulte hi permission maangein
        askNotificationPermission()

        // Notification se aaye data ko nikaalein
        val channelIdFromNotification = intent.getStringExtra("channelId")
        val channelNameFromNotification = intent.getStringExtra("channelName")

        setContent {
            SyncUPTheme {
                Surface {
                    // Data ko MainApp tak pahunchayein
                    MainApp(
                        startChannelId = channelIdFromNotification,
                        startChannelName = channelNameFromNotification
                    )
                }
            }
        }
    }

    private fun askNotificationPermission() {
        // Sirf Android 13 (API 33) ya usse naye version par hi permission maangein
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

