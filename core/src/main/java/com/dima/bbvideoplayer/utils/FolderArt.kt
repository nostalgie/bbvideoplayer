package com.dima.bbvideoplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
     * Blocking IO + possible frame extraction — call from Dispatchers.IO.
     */
    fun coverFile(context: Context, folderPath: String, sampleVideoPath: String?): File? {
        customCoverFile(context, folderPath)?.let { return it }

        val thumb = thumbFile(context, folderPath)
        if (thumb.isFile && thumb.length() > 0) return thumb

        if (sampleVideoPath == null) return null
        synchronized(lockFor(folderPath)) {
            // Another thread may have produced the thumb while we waited.
            if (thumb.isFile && thumb.length() > 0) return thumb
            if (!generateThumbnail(videoPath = sampleVideoPath, target = thumb)) return null
        }
        return if (thumb.isFile && thumb.length() > 0) thumb else null
    }

    private fun thumbFile(context: Context, folderPath: String): File =
        File(thumbsDir(context), "${cacheKey(folderPath)}.jpg")

    internal fun generateThumbnail(videoPath: String, target: File): Boolean {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            val frame: Bitmap = retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return false

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
}
