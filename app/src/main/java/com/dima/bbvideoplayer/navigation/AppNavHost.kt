package com.dima.bbvideoplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dima.bbvideoplayer.AppState
import com.dima.bbvideoplayer.ui.screens.FilePickerScreen
import com.dima.bbvideoplayer.ui.screens.KidPlayerScreen
import com.dima.bbvideoplayer.ui.screens.ParentDashboardScreen
import com.dima.bbvideoplayer.ui.screens.ParentSettingsScreen
import com.dima.bbvideoplayer.ui.screens.PlaybackPickerScreen
import kotlinx.coroutines.delay

/**
 * Navigation routes for the app.
 */
object Routes {
    const val KID_PLAYER = "kid_player"
    const val PARENT_DASHBOARD = "parent_dashboard"
    const val PARENT_PLAYBACK = "parent_playback"
    const val PARENT_SETTINGS = "parent_settings"
    const val FILE_PICKER = "file_picker"
}

private const val KIOSK_START_DELAY_MS = 800L

/**
 * Main navigation host for the app.
 *
 * Uses [AppState] to access shared managers and callbacks,
 * reducing prop drilling through the navigation layer.
 *
 * @param navController Navigation controller
 * @param appState Centralized app state with managers and callbacks
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    appState: AppState
) {
    LaunchedEffect(appState.resetToKidMode) {
        if (appState.resetToKidMode) {
            navController.navigate(Routes.KID_PLAYER) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
            appState.resetToKidMode = false
        }
    }

    // Auto-enter kiosk after first frame so libVLC can initialize first.
    LaunchedEffect(Unit) {
        delay(KIOSK_START_DELAY_MS)
        if (!appState.exitingToHome &&
            !appState.lockTaskManager.isLockTaskRunning()
        ) {
            appState.enterKidMode()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.KID_PLAYER
    ) {
        composable(Routes.KID_PLAYER) {
            KidPlayerScreen(
                videoRepository = appState.videoRepository,
                videoLibraryService = appState.videoLibraryService,
                videoPlayerManager = appState.videoPlayerManager,
                playbackStateRepository = appState.playbackStateRepository,
                onSecretDoorActivated = {
                    appState.videoPlayerManager.pause()
                    navController.navigate(Routes.PARENT_DASHBOARD) {
                        popUpTo(Routes.KID_PLAYER) { inclusive = false }
                    }
                },
                pendingStartVideoIndex = appState.pendingStartVideoIndex,
                onPendingIndexConsumed = { appState.pendingStartVideoIndex = -1 }
            )
        }

        composable(Routes.PARENT_DASHBOARD) {
            ParentDashboardScreen(
                videoRepository = appState.videoRepository,
                videoLibraryService = appState.videoLibraryService,
                onBackToKidMode = {
                    appState.videoPlayerManager.play()
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                },
                onNavigateToFilePicker = {
                    navController.navigate(Routes.FILE_PICKER)
                },
                onNavigateToPlayback = {
                    navController.navigate(Routes.PARENT_PLAYBACK)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.PARENT_SETTINGS)
                },
                onPlayVideo = { index ->
                    appState.pendingStartVideoIndex = index
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                },
                onExit = { appState.suspendKiosk() }
            )
        }

        composable(Routes.PARENT_PLAYBACK) {
            PlaybackPickerScreen(
                videoRepository = appState.videoRepository,
                videoLibraryService = appState.videoLibraryService,
                onBack = { navController.popBackStack() },
                onPlayVideo = { index ->
                    appState.pendingStartVideoIndex = index
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                }
            )
        }

        composable(Routes.PARENT_SETTINGS) {
            ParentSettingsScreen(
                videoRepository = appState.videoRepository,
                onBack = { navController.popBackStack() },
                showControlsSideSetting = true,
                showPinControls = true
            )
        }

        composable(Routes.FILE_PICKER) {
            FilePickerScreen(
                videoRepository = appState.videoRepository,
                onBack = {
                    appState.videoPlayerManager.play()
                    navController.popBackStack(Routes.PARENT_DASHBOARD, inclusive = false)
                },
                onOpenExternalSettings = { appState.exitKidMode() }
            )
        }
    }
}
