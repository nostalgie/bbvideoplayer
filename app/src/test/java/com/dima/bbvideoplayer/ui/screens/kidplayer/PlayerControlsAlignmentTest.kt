package com.dima.bbvideoplayer.ui.screens.kidplayer

import androidx.compose.ui.Alignment
import com.dima.bbvideoplayer.data.VideoRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the controls-side setting mapping used by [PlayerControlsOverlay].
 */
class PlayerControlsAlignmentTest {

    @Test
    fun leftValue_alignsColumnToTheStart() {
        assertThat(playerControlsAlignment(VideoRepository.CONTROLS_SIDE_LEFT))
            .isSameInstanceAs(Alignment.CenterStart)
    }

    @Test
    fun rightValue_alignsColumnToTheEnd() {
        assertThat(playerControlsAlignment(VideoRepository.CONTROLS_SIDE_RIGHT))
            .isSameInstanceAs(Alignment.CenterEnd)
    }

    @Test
    fun unknownValue_fallsBackToTheLeft() {
        assertThat(playerControlsAlignment("nonsense"))
            .isSameInstanceAs(Alignment.CenterStart)
    }
}
