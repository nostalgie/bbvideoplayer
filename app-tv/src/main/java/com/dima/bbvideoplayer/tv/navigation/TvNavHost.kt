package com.dima.bbvideoplayer.tv.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.tv.ui.TvPlayerScreen
import com.dima.bbvideoplayer.ui.screens.FilePickerScreen
import com.dima.bbvideoplayer.ui.screens.ParentDashboardScreen
import com.dima.bbvideoplayer.ui.screens.ParentSettingsScreen
import com.dima.bbvideoplayer.ui.screens.PlaybackPickerScreen

/**
 * Navigation routes for the TV edition.
 */
object TvRoutes {
    const val KID_PLAYER = "kid_player"
    const val PARENT_DASHBOARD = "parent_dashboard"
    const val PARENT_PLAYBACK = "parent_playback"
    const val PARENT_SETTINGS = "parent_settings"
    const val FILE_PICKER = "file_picker"
}

/**
 * TV navigation. No kiosk auto-entry and no PIN gate: the parent dashboard
 * opens straight from the "Родителям" button on the player screen.
 */
@Composable
fun TvNavHost(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository
) {
    val navController = rememberNavController()
    var pendingStartVideoIndex by remember { mutableStateOf(-1) }
    val activity = LocalContext.current as? ComponentActivity

    NavHost(
        navController = navController,
        startDestination = TvRoutes.KID_PLAYER
    ) {
        composable(TvRoutes.KID_PLAYER) {
            TvPlayerScreen(
                videoRepository = videoRepository,
                videoLibraryService = videoLibraryService,
                videoPlayerManager = videoPlayerManager,
                playbackStateRepository = playbackStateRepository,
                onOpenParentDashboard = {
                    videoPlayerManager.pause()
                    navController.navigate(TvRoutes.PARENT_DASHBOARD)
                },
                pendingStartVideoIndex = pendingStartVideoIndex,
                onPendingIndexConsumed = { pendingStartVideoIndex = -1 }
            )
        }

        composable(TvRoutes.PARENT_DASHBOARD) {
            ParentDashboardScreen(
                videoRepository = videoRepository,
                videoLibraryService = videoLibraryService,
                onBackToKidMode = {
                    videoPlayerManager.play()
                    navController.popBackStack(TvRoutes.KID_PLAYER, inclusive = false)
                },
                onNavigateToFilePicker = {
                    navController.navigate(TvRoutes.FILE_PICKER)
                },
                onNavigateToPlayback = {
                    navController.navigate(TvRoutes.PARENT_PLAYBACK)
                },
                onNavigateToSettings = {
                    navController.navigate(TvRoutes.PARENT_SETTINGS)
                },
                onPlayVideo = { index ->
                    pendingStartVideoIndex = index
                    videoPlayerManager.play()
                    navController.popBackStack(TvRoutes.KID_PLAYER, inclusive = false)
                },
                // No kiosk on TV: "Выход" simply closes the app.
                onExit = { activity?.finishAffinity() }
            )
        }

        composable(TvRoutes.PARENT_PLAYBACK) {
            PlaybackPickerScreen(
                videoRepository = videoRepository,
                videoLibraryService = videoLibraryService,
                onBack = { navController.popBackStack() },
                onPlayVideo = { index ->
                    pendingStartVideoIndex = index
                    videoPlayerManager.play()
                    navController.popBackStack(TvRoutes.KID_PLAYER, inclusive = false)
                }
            )
        }

        composable(TvRoutes.PARENT_SETTINGS) {
            ParentSettingsScreen(
                videoRepository = videoRepository,
                onBack = { navController.popBackStack() },
                // No PIN and no phone-only controls placement on TV.
                showControlsSideSetting = false,
                showPinControls = false
            )
        }

        composable(TvRoutes.FILE_PICKER) {
            FilePickerScreen(
                videoRepository = videoRepository,
                onBack = {
                    navController.popBackStack(TvRoutes.PARENT_DASHBOARD, inclusive = false)
                }
            )
        }
    }
}
