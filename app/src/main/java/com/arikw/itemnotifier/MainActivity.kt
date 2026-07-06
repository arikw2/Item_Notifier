package com.arikw.itemnotifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arikw.itemnotifier.ui.AddItemScreen
import com.arikw.itemnotifier.ui.AddItemViewModel
import com.arikw.itemnotifier.ui.ItemListScreen
import com.arikw.itemnotifier.ui.theme.ItemNotifierTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askForNotificationPermission()
        val sharedUrl = extractSharedUrl(intent)

        setContent {
            ItemNotifierTheme {
                AppNav(sharedUrl)
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Handles "Share -> Item Notifier" from a browser or the Terminal X app. */
    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return AddItemViewModel.extractUrl(text)
    }
}

@Composable
private fun AppNav(sharedUrl: String?) {
    val navController = rememberNavController()

    // When launched via a share intent, jump straight into the add flow.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            navController.navigate("add?url=${Uri.encode(sharedUrl)}")
        }
    }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ItemListScreen(
                onAddItem = { navController.navigate("add") }
            )
        }
        composable(
            route = "add?url={url}",
            arguments = listOf(navArgument("url") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            AddItemScreen(
                initialUrl = backStackEntry.arguments?.getString("url"),
                onDone = { navController.popBackStack("list", inclusive = false) }
            )
        }
    }
}
