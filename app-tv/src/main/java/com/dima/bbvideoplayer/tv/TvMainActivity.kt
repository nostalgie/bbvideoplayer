package com.dima.bbvideoplayer.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.ui.theme.BbVideoPlayerTheme
import com.dima.bbvideoplayer.tv.navigation.TvNavHost
import com.dima.bbvideoplayer.utils.StoragePermissionHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * Android TV entry point. No kiosk/lock-task plumbing (that is phone-only):
 * the activity just keeps the screen on, hides system bars and hosts the
 * player / parent dashboard / file picker navigation.
 */
class TvMainActivity : ComponentActivity() {

    private val app: TvPlayerApp
        get() = application as TvPlayerApp
    private val videoPlayerManager: VideoPlayerManager
        get() = app.videoPlayerManager
    private lateinit var playbackStateRepository: PlaybackStateRepository

    /** The video Surface dies when the screen goes off; rebind it on SCREEN_ON. */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                videoPlayerManager.refreshVideoSurface()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackStateRepository = PlaybackStateRepository(applicationContext)

        // Protected system broadcast: the two-arg registration needs no export flag.
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        )

        app.videoLibraryService.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.videoLibraryService.startPeriodicScan()
                try {
                    awaitCancellation()
                } finally {
                    app.videoLibraryService.stopPeriodicScan()
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
                    TvNavHost(
                        videoRepository = app.videoRepository,
                        videoLibraryService = app.videoLibraryService,
                        videoPlayerManager = videoPlayerManager,
                        playbackStateRepository = playbackStateRepository
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.pause()
        savePlaybackState()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Not registered (registration failed earlier)
        }
        super.onDestroy()
        // Do NOT release the player here: it must survive Activity recreation.
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
        private const val TAG = "TvMainActivity"
    }
}
