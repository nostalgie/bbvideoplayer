package com.dima.bbvideoplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dima.bbvideoplayer.admin.LockTaskManager
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.navigation.AppNavHost
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.utils.LauncherHelper
import com.dima.bbvideoplayer.utils.OrientationHelper
import com.dima.bbvideoplayer.utils.StoragePermissionHelper
import com.dima.bbvideoplayer.ui.theme.BbVideoPlayerTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var lockTaskManager: LockTaskManager
    private val app: BbVideoPlayerApp
        get() = application as BbVideoPlayerApp
    private val videoRepository
        get() = app.videoRepository
    private val videoLibraryService
        get() = app.videoLibraryService
    private val videoPlayerManager: VideoPlayerManager
        get() = app.videoPlayerManager
    private lateinit var playbackStateRepository: PlaybackStateRepository

    /** Single source of truth for kiosk state — set from Compose [AppState]. */
    private var appState: AppState? = null
    private var startupOrientationListener: OrientationEventListener? = null
    private var attemptedOrientationRecreate = false
    private var lastHomeRedirectElapsed = 0L
    private var pendingResetToKidMode = false

    /** The video Surface dies when the screen goes off; rebind it on SCREEN_ON. */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                videoPlayerManager.refreshVideoSurface()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        attemptedOrientationRecreate =
            savedInstanceState?.getBoolean(STATE_ORIENTATION_RECREATED) == true
        OrientationHelper.lockToDisplayRotation(this)
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)

        val appContext = applicationContext
        lockTaskManager = LockTaskManager(appContext)
        playbackStateRepository = PlaybackStateRepository(appContext)

        // Protected system broadcast: the two-arg registration needs no export flag.
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        )

        videoLibraryService.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                videoLibraryService.startPeriodicScan()
                try {
                    awaitCancellation()
                } finally {
                    videoLibraryService.stopPeriodicScan()
                }
            }
        }

        requestVideoPermissionIfNeeded()

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContent {
            BbVideoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val state = rememberAppState(
                        lockTaskManager = lockTaskManager,
                        videoRepository = videoRepository,
                        videoLibraryService = videoLibraryService,
                        videoPlayerManager = videoPlayerManager,
                        playbackStateRepository = playbackStateRepository,
                        suspendedFromKiosk = app.suspendedFromKiosk,
                        onEnterKidMode = { enterKidMode() },
                        onExitKidMode = { exitKidMode() },
                        onSuspendKiosk = { suspendKiosk() }
                    )

                    SideEffect {
                        appState = state
                        if (pendingResetToKidMode) {
                            state.resetToKidMode = true
                            pendingResetToKidMode = false
                        }
                    }

                    AppNavHost(
                        navController = navController,
                        appState = state
                    )
                }
            }
        }

        startupOrientationListener = OrientationHelper.listenForStartupCorrection(this) {
            startupOrientationListener = null
        }
        window.decorView.post { finalizeStartupOrientation() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ORIENTATION_RECREATED, attemptedOrientationRecreate)
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.pause()
        savePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Surface rebind happens only on ACTION_SCREEN_ON (see screenStateReceiver);
        // an unconditional refresh here rebuilt the vout on every foregrounding.

        val state = appState
        if (state != null &&
            !state.exitingToHome &&
            lockTaskManager.isDeviceOwner() &&
            !lockTaskManager.isLockTaskRunning()
        ) {
            Log.d(TAG, "Re-starting kiosk mode in onResume")
            state.enterKidMode()
        }
        if (state?.isLockTaskActive == true) {
            videoPlayerManager.play()
        }
    }

    override fun onDestroy() {
        startupOrientationListener?.disable()
        startupOrientationListener = null
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Not registered (registration failed earlier)
        }
        super.onDestroy()
        // Do NOT stop lock task or release the player here.
        // As the HOME launcher in kiosk mode, onDestroy runs on every restart;
        // releasing the player or stopping lock task causes a crash/relaunch loop.
    }

    private fun enterKidMode(): Boolean {
        Log.d(TAG, "Entering Kid Mode — starting Lock Task")
        if (lockTaskManager.isDeviceOwner()) {
            lockTaskManager.applyKioskPolicies()
        }
        val started = lockTaskManager.startKioskMode(this)
        hideSystemUI()
        return started
    }

    private fun exitKidMode() {
        Log.d(TAG, "Exiting Kid Mode — stopping Lock Task")
        lockTaskManager.stopKioskMode(this)
    }

    private fun suspendKiosk() {
        Log.d(TAG, "Suspending kiosk — returning to home (Device Owner retained)")
        app.suspendedFromKiosk = true
        appState?.exitingToHome = true
        exitKidMode()
        lockTaskManager.removeKioskPolicies()
        launchSystemHomeOnce()
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (isLauncherIntent(intent)) {
            app.suspendedFromKiosk = false
            app.cachedExternalLauncher = null
            val state = appState
            state?.exitingToHome = false
            if (state != null) {
                state.resetToKidMode = true
            } else {
                pendingResetToKidMode = true
            }
            return
        }
        if (app.suspendedFromKiosk && LauncherHelper.isHomeOnlyIntent(intent)) {
            Log.d(TAG, "HOME intent while suspended — forwarding to system launcher")
            appState?.exitingToHome = true
            launchSystemHomeOnce()
        }
    }

    private fun launchSystemHomeOnce() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHomeRedirectElapsed < HOME_REDIRECT_COOLDOWN_MS) {
            Log.d(TAG, "Skipping duplicate home redirect")
            return
        }
        lastHomeRedirectElapsed = now
        app.cachedExternalLauncher = LauncherHelper.launchSystemHome(
            this,
            app.cachedExternalLauncher
        ) ?: app.cachedExternalLauncher
    }

    private fun savePlaybackState() {
        val uri = videoPlayerManager.getCurrentVideoUri() ?: return
        val positionMs = videoPlayerManager.currentPosition

        playbackStateRepository.save(
            PlaybackStateRepository.PlaybackState(
                videoUri = uri,
                positionMs = positionMs
            )
        )
        Log.d(TAG, "Saved playback state: uri=$uri, position=${positionMs}ms")
    }

    private fun finalizeStartupOrientation() {
        if (!OrientationHelper.needsOrientationCorrection(this)) {
            OrientationHelper.restoreFreeRotation(this)
            return
        }

        if (attemptedOrientationRecreate) {
            Log.w(TAG, "Orientation mismatch persists after recreate — using sensor listener")
            OrientationHelper.restoreFreeRotation(this)
            return
        }

        Log.w(TAG, "Orientation mismatch at cold start — recreating activity")
        attemptedOrientationRecreate = true
        OrientationHelper.lockToDisplayRotation(this)
        recreate()
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun requestVideoPermissionIfNeeded() {
        if (StoragePermissionHelper.hasStoragePermission(this)) return

        // API 30-32: no runtime dialog can satisfy the All-Files-Access check,
        // so route the user to the system settings screen.
        if (StoragePermissionHelper.needsManageAllFilesAccess() &&
            !android.os.Environment.isExternalStorageManager()
        ) {
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            }
            return
        }

        val permissions = StoragePermissionHelper.requiredPermissions()
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions, 0)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val STATE_ORIENTATION_RECREATED = "orientation_recreated"
        private const val HOME_REDIRECT_COOLDOWN_MS = 2_000L

        /**
         * True for taps on this app launcher icon. TV (Leanback) launchers send
         * ACTION_MAIN with CATEGORY_LEANBACK_LAUNCHER only, so it counts as a
         * launcher tap too.
         */
        internal fun isLauncherIntent(intent: Intent?): Boolean =
            intent != null && (
                intent.hasCategory(Intent.CATEGORY_LAUNCHER) ||
                    intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                )
    }
}
