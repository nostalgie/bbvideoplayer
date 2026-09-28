package com.dima.bbvideoplayer.data

import android.content.Context
import com.dima.bbvideoplayer.ui.screens.filepicker.isVideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import java.io.File

/**
 * Checks whether a local video file can be played via libVLC.
 * Results are cached by path + size + lastModified.
 */
class VideoCompatibilityChecker(context: Context) {

    private val libVlc: LibVLC by lazy {
        LibVLC(context, arrayListOf("--intf=dummy", "--no-audio"))
    }

    private val cache = ExpiringCache<String, Boolean>(
        ttlMs = 10 * 60 * 1000L,
        maxEntries = 500
    )
    private val probeSemaphore = Semaphore(3)

    fun cacheKey(file: File): String =
        "${file.absolutePath}:${file.length()}:${file.lastModified()}"

    suspend fun isPlayable(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (!isVideoFile(file.name)) return false

        val key = cacheKey(file)
        cache[key]?.let { return it }

        val result = probeSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                probeWithVlc(file)
            }
        }
        cache.put(key, result)
        return result
    }

    private fun probeWithVlc(file: File): Boolean {
        val media = Media(libVlc, file.absolutePath)
        return try {
            media.parse()
            var attempts = 0
            while (!media.isParsed && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            media.duration > 0
        } catch (_: Exception) {
            false
        } finally {
            media.release()
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
