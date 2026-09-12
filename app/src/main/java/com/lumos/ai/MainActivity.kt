package com.lumos.ai

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumos.ai.data.SettingsRepository
import com.lumos.ai.ui.chat.ChatScreen
import com.lumos.ai.ui.chat.ChatViewModel
import com.lumos.ai.ui.home.HomeScreen
import com.lumos.ai.ui.home.HomeViewModel
import com.lumos.ai.ui.settings.SettingsScreen
import com.lumos.ai.ui.theme.LumosTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Needed on Android 13+ so the "LUMOS is thinking…" generation
        // notification can actually display. The foreground service still
        // runs and keeps streaming alive even if this is denied — the user
        // just won't see the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            LumosTheme { LumosNav() }
        }
    }
}

@Composable
fun LumosNav() {
    val nav = rememberNavController()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
            HomeScreen(
                vm = vm,
                onOpenChat = { id -> nav.navigate("chat/$id") },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable(
            route = "chat/{id}",
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(app))
            val id = entry.arguments?.getLong("id") ?: -1L
            androidx.compose.runtime.LaunchedEffect(id) { vm.loadConversation(id) }
            ChatScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                repo = SettingsRepository(app),
                onBack = { nav.popBackStack() }
            )
        }
    }
}
