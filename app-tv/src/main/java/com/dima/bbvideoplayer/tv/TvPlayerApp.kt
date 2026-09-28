package com.dima.bbvideoplayer.tv

import android.app.Application
import android.util.Log
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager

/**
 * Application class for the Android TV edition.
 * Holds process-wide singletons that must survive Activity recreation.
 * Unlike the phone app there is no kiosk/lock-task state here.
 */
class TvPlayerApp : Application() {

    val videoRepository: VideoRepository by lazy {
        VideoRepository(this)
    }

    val videoLibraryService: VideoLibraryService by lazy {
        VideoLibraryService(this, videoRepository)
    }

    /** Single libVLC player instance for the process — do not release on Activity.onDestroy. */
    val videoPlayerManager: VideoPlayerManager by lazy {
        VideoPlayerManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TvPlayerApp initialized")
    }

    companion object {
        private const val TAG = "TvPlayerApp"
    }
}
