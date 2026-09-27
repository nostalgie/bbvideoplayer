package com.dima.bbvideoplayer.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoPathUtilsTest {

    @Test
    fun extractFolderInfo_fileUri_returnsAbbreviatedPathAndFileName() {
        val result = extractFolderInfo("file:///storage/emulated/0/Movies/video.mp4")
        assertThat(result).isEqualTo("Внутр./0/Movies" to "video.mp4")
    }

    @Test
    fun extractFolderInfo_sdCardUri_abbreviatesVolume() {
        val result = extractFolderInfo("file:///storage/ABCD-1234/Video/clip.mkv")
        assertThat(result?.first).startsWith("SDAB")
        assertThat(result?.second).isEqualTo("clip.mkv")
    }

    @Test
    fun extractFolderInfo_contentUri_decodesFileName() {
        val result = extractFolderInfo(
            "content://com.android.externalstorage.documents/document/primary%3AMovies%2Ffilm.mp4"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.second).isEqualTo("film.mp4")
        assertThat(result.first).isEqualTo("Movies")
    }

    @Test
    fun extractFolderInfo_unknownScheme_returnsNull() {
        assertThat(extractFolderInfo("http://example.com/video.mp4")).isNull()
    }

    @Test
    fun abbreviateFolderPath_internalStorage() {
        assertThat(abbreviateFolderPath("/storage/emulated/0/Movies"))
            .isEqualTo("Внутр./0/Movies")
    }

    @Test
    fun abbreviateFolderPath_removableSdCard() {
        assertThat(abbreviateFolderPath("/storage/ABCD-EF12"))
            .isEqualTo("SDAB")
    }

    @Test
    fun abbreviateFolderPath_nonStandardPath() {
        assertThat(abbreviateFolderPath("/mnt/media/0/Films"))
            .isEqualTo("mnt/media/0/Films")
    }

    @Test
    fun extractParentFolderPath_returnsAbsoluteFolderForFileUri() {
        val folder = extractParentFolderPath("file:///storage/emulated/0/Movies/video.mp4")
        assertThat(folder).isEqualTo("/storage/emulated/0/Movies")
    }

    @Test
    fun extractParentFolderPath_returnsNullForNonFileUri() {
        assertThat(extractParentFolderPath("content://media/video/1")).isNull()
    }

    @Test
    fun groupVideosByFolderData_groupsByFolder() {
        val uris = listOf(
            "file:///storage/emulated/0/Movies/a.mp4",
            "file:///storage/emulated/0/Movies/b.mp4",
            "file:///storage/emulated/0/Anime/c.mp4"
        )
        val result = groupVideosByFolderData(uris)
        assertThat(result.folderMap.keys).containsAtLeast("Внутр./0/Movies", "Внутр./0/Anime")
        assertThat(result.folderMap["Внутр./0/Movies"]).hasSize(2)
        assertThat(result.folderMap["Внутр./0/Anime"]).hasSize(1)
    }

    @Test
    fun groupVideosByFolderData_ungroupedWhenParseFails() {
        val result = groupVideosByFolderData(listOf("invalid"))
        assertThat(result.ungroupedFiles).hasSize(1)
        assertThat(result.folderMap).isEmpty()
    }

    @Test
    fun calculateFolderDepths_relativeNesting() {
        val depths = calculateFolderDepths(listOf("Внутр./A", "Внутр./A/B", "Внутр./A/B/C"))
        assertThat(depths["Внутр./A"]).isEqualTo(0)
        assertThat(depths["Внутр./A/B"]).isEqualTo(1)
        assertThat(depths["Внутр./A/B/C"]).isEqualTo(2)
    }

    @Test
    fun isSdCardPath_matchesKnownPatterns() {
        assertThat(isSdCardPath("/storage/ABCD-1234")).isTrue()
        assertThat(isSdCardPath("/storage/sdcard1")).isTrue()
        assertThat(isSdCardPath("/storage/emulated/0")).isFalse()
    }
}
