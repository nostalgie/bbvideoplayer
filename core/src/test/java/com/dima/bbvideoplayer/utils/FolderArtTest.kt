/**
 * Tests for [FolderArt] — cache keys and the custom-cover store.
 */
package com.dima.bbvideoplayer.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FolderArtTest {

    private lateinit var context: Context

    private val folderPath = "/storage/emulated/0/Movies"
    private val imageBytes = byteArrayOf(1, 2, 3, 4)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FolderArt.customArtDir(context).deleteRecursively()
        FolderArt.thumbsDir(context).deleteRecursively()
    }

    @Test
    fun cacheKey_isStableForTheSamePath() {
        assertThat(FolderArt.cacheKey(folderPath)).isEqualTo(FolderArt.cacheKey(folderPath))
    }

    @Test
    fun cacheKey_differsPerPath() {
        assertThat(FolderArt.cacheKey(folderPath))
            .isNotEqualTo(FolderArt.cacheKey("$folderPath/Season 1"))
    }

    @Test
    fun cacheKey_isAnMd5HexDigest() {
        assertThat(FolderArt.cacheKey(folderPath)).matches("[0-9a-f]{32}")
    }

    @Test
    fun customCoverFile_returnsNullWhenNothingStored() {
        assertThat(FolderArt.customCoverFile(context, folderPath)).isNull()
    }

    @Test
    fun customCoverFile_ignoresEmptyFiles() {
        val file = File(FolderArt.customArtDir(context), "${FolderArt.cacheKey(folderPath)}.jpg")
        file.parentFile?.mkdirs()
        file.createNewFile()

        assertThat(FolderArt.customCoverFile(context, folderPath)).isNull()
    }

    @Test
    fun setCustomCover_copiesImageAndDropsStaleThumb() {
        val thumb = File(FolderArt.thumbsDir(context), "${FolderArt.cacheKey(folderPath)}.jpg")
        thumb.parentFile?.mkdirs()
        thumb.writeBytes(imageBytes)

        val uri = Uri.parse("content://test/cover.jpg")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(imageBytes))

        assertThat(FolderArt.setCustomCover(context, folderPath, uri)).isTrue()
        assertThat(FolderArt.customCoverFile(context, folderPath)).isNotNull()
        assertThat(thumb.exists()).isFalse()
    }

    @Test
    fun clearCustomCover_removesStoredArt() {
        val uri = Uri.parse("content://test/cover.jpg")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(imageBytes))
        FolderArt.setCustomCover(context, folderPath, uri)

        FolderArt.clearCustomCover(context, folderPath)

        assertThat(FolderArt.customCoverFile(context, folderPath)).isNull()
    }
}
