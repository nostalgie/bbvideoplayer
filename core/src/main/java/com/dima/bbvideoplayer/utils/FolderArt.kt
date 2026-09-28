package com.dima.bbvideoplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import java.security.MessageDigest

/**
 * Cover art for watched-folder cards.
 *
 * Priority: a user-chosen image (stored under `filesDir/folder_art/`) wins;
 * otherwise a frame is grabbed from the folder's first video and cached on
 * disk under `filesDir/thumbs/`. Files are keyed by an MD5 of the folder
 * path, so no metadata store is needed — existence is the state.
 */
object FolderArt {

    private const val THUMB_MAX_WIDTH_PX = 512
    private const val JPEG_QUALITY = 85
    private const val NEAR_BLACK_LUMA_THRESHOLD = 24
    private const val SCENE_SNAPSHOT_TIMEOUT_MS = 10_000L

    /**
     * All cover generation runs on this single background thread: frame
     * extraction hammers the storage, and several parallel decoders make
     * ordinary directory listings visibly slow. One queue = UI stays fluid,
     * covers just appear later.
     */
    val thumbnailDispatcher: kotlinx.coroutines.CoroutineDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "folder-art").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    fun thumbsDir(context: Context): File =
        File(context.filesDir, "thumbs").apply { mkdirs() }

    fun customArtDir(context: Context): File =
        File(context.filesDir, "folder_art").apply { mkdirs() }

    /** Stable, collision-safe cache key for a folder path. */
    internal fun cacheKey(folderPath: String): String =
        MessageDigest.getInstance("MD5")
            .digest(folderPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun customCoverFile(context: Context, folderPath: String): File? {
        val file = File(customArtDir(context), "${cacheKey(folderPath)}.jpg")
        return if (file.isFile && file.length() > 0) file else null
    }

    /** Copies the picked image into the custom-art store. Returns true on success. */
    fun setCustomCover(context: Context, folderPath: String, imageUri: Uri): Boolean {
        return try {
        val target = File(customArtDir(context), "${cacheKey(folderPath)}.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        // The generated thumbnail becomes stale once a custom cover exists.
        thumbFile(context, folderPath).delete()
        true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomCover(context: Context, folderPath: String) {
        customCoverFile(context, folderPath)?.delete()
    }

    /**
     * Returns the cover image file for the folder: custom art if present,
     * else the cached/generated first-video frame; null when nothing works.
     * Blocking IO + possible frame extraction — call from a background
     * dispatcher. [cancelCheck] is polled during frame extraction: when it
     * throws (e.g. the UI that requested the cover is gone), generation
     * aborts immediately so it never competes with the video player.
     */
    fun coverFile(
        context: Context,
        folderPath: String,
        sampleVideoPath: String?,
        cancelCheck: () -> Unit = {}
    ): File? {
        customCoverFile(context, folderPath)?.let { return it }

        val thumb = thumbFile(context, folderPath)
        if (thumb.isFile && thumb.length() > 0) return thumb

        if (sampleVideoPath == null) return null
        synchronized(lockFor(folderPath)) {
            // Another thread may have produced the thumb while we waited.
            if (thumb.isFile && thumb.length() > 0) return thumb
            if (!generateThumbnail(sampleVideoPath, context, thumb, cancelCheck)) return null
        }
        return if (thumb.isFile && thumb.length() > 0) thumb else null
    }

    private fun thumbFile(context: Context, folderPath: String): File =
        File(thumbsDir(context), "${cacheKey(folderPath)}.jpg")

    internal fun generateThumbnail(videoPath: String, target: File): Boolean =
        generateThumbnail(videoPath, null, target) {}

    private fun generateThumbnail(
        videoPath: String,
        context: Context?,
        target: File,
        cancelCheck: () -> Unit
    ): Boolean {
        // The platform retriever handles mp4/mkv (but often grabs a black
        // fade-in frame — so several offsets are tried); libVLC is the
        // fallback for AVI/XVID it cannot decode at all.
        if (generateThumbnailRetriever(videoPath, target, cancelCheck)) return true
        target.delete()
        if (context == null) return false
        return generateThumbnailVlc(context.applicationContext, videoPath, target, cancelCheck)
    }

    private fun generateThumbnailRetriever(
        videoPath: String,
        target: File,
        cancelCheck: () -> Unit
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            // Offsets at 0/10/25/50% — the first frame is frequently black.
            val offsetsUs = if (durationMs > 0) {
                listOf(0L, durationMs / 10, durationMs / 4, durationMs / 2)
            } else {
                listOf(0L)
            }.map { it * 1000 }.distinct()

            var lastFrame: Bitmap? = null
            for (offsetUs in offsetsUs) {
                cancelCheck()
                val frame = retriever.getFrameAtTime(
                    offsetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue
                lastFrame = frame
                if (isNearBlack(frame)) continue
                return writeThumbnail(frame, target)
            }
            // All frames black: still better than nothing.
            return lastFrame?.let { writeThumbnail(it, target) } ?: false
        } catch (_: Exception) {
            target.delete()
            return false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Already released
            }
        }
    }

    // One lock per folder path so parallel card loads do not extract the same
    // frame twice; interned via a plain map lookup guarded by the object itself.
    private val locks = HashMap<String, Any>()

    @Synchronized
    private fun lockFor(folderPath: String): Any = locks.getOrPut(folderPath) { Any() }

    /** Downscaled luminance check — a fade-in/studio-logo frame is useless as a cover. */
    private fun isNearBlack(frame: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(frame, 16, 9, true)
        val pixels = IntArray(16 * 9)
        sample.getPixels(pixels, 0, 16, 0, 0, 16, 9)
        var maxLuma = 0
        for (pixel in pixels) {
            val luma = (pixel shr 16 and 0xFF) * 299 / 1000 +
                (pixel shr 8 and 0xFF) * 587 / 1000 +
                (pixel and 0xFF) * 114 / 1000
            if (luma > maxLuma) maxLuma = luma
        }
        return maxLuma < NEAR_BLACK_LUMA_THRESHOLD
    }

    private fun writeThumbnail(frame: Bitmap, target: File): Boolean {
        val scale = if (frame.width > THUMB_MAX_WIDTH_PX) {
            THUMB_MAX_WIDTH_PX.toFloat() / frame.width
        } else {
            1f
        }
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            frame
        }
        target.parentFile?.mkdirs()
        return target.outputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    }

    /**
     * libVLC fallback for containers the platform retriever cannot decode
     * (AVI/XVID). Plays the file silently into an off-screen surface with the
     * "scene" video filter, which dumps frames as JPEGs; the first frame
     * written becomes the cover. Serialized: parallel VLC instances are heavy.
     */
    @Synchronized
    private fun generateThumbnailVlc(
        context: Context,
        videoPath: String,
        target: File,
        cancelCheck: () -> Unit
    ): Boolean {
        var libVLC: org.videolan.libvlc.LibVLC? = null
        var player: org.videolan.libvlc.MediaPlayer? = null
        var media: org.videolan.libvlc.Media? = null
        val sceneDir = File(target.parentFile, "scene-tmp").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            libVLC = org.videolan.libvlc.LibVLC(
                context,
                arrayListOf("--vout=dummy", "--aout=dummy", "--no-audio", "--no-spu", "--quiet")
            )
            player = org.videolan.libvlc.MediaPlayer(libVLC)
            media = org.videolan.libvlc.Media(libVLC, videoPath).apply {
                addOption(":video-filter=scene")
                addOption(":scene-format=jpg")
                addOption(":scene-path=${sceneDir.absolutePath}")
                addOption(":scene-ratio=30")
            }
            player.media = media
            // The Android build of libVLC only ships its own video output,
            // which requires a surface — without one the decoder never starts
            // and the scene filter never runs. An off-screen SurfaceTexture
            // (never rendered) satisfies it.
            val surfaceTexture = android.graphics.SurfaceTexture(0).apply { detachFromGLContext() }
            val surface = android.view.Surface(surfaceTexture)
            player.vlcVout.setVideoSurface(surface, null)
            player.vlcVout.attachViews()
            player.play()

            val deadline = System.currentTimeMillis() + SCENE_SNAPSHOT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                cancelCheck()
                val frameFile = sceneDir.listFiles { f -> f.length() > 0 }
                    ?.minByOrNull { it.name }
                if (frameFile != null) {
                    player.stop()
                    val bitmap = android.graphics.BitmapFactory.decodeFile(frameFile.absolutePath)
                        ?: return false
                    return writeThumbnail(bitmap, target)
                }
                Thread.sleep(150)
            }
            player.stop()
            return false
        } catch (_: Exception) {
            try {
                player?.stop()
            } catch (_: Exception) {
                // Best effort
            }
            return false
        } finally {
            try {
                player?.release()
            } catch (_: Exception) {
                // Best effort
            }
            try {
                media?.release()
            } catch (_: Exception) {
                // Best effort
            }
            try {
                libVLC?.release()
            } catch (_: Exception) {
                // Best effort
            }
            sceneDir.deleteRecursively()
        }
    }
}
