package com.dima.bbvideoplayer.ui.screens

import com.dima.bbvideoplayer.data.PlaybackStateRepository.PlaybackState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KidPlayerPlannerTest {

    private val uris = listOf("file:///a.mp4", "file:///b.mp4", "file:///c.mp4")

    @Test
    fun emptyPlaylist_isNoOp() {
        val d = KidPlayerPlanner.decide(emptyList(), null, false, -1, null)
        assertThat(d.markSetupDone).isFalse()
    }

    @Test
    fun pendingIndex_startsAtZeroPositionAndConsumes() {
        val d = KidPlayerPlanner.decide(uris, "file:///a.mp4", true, 1, null)
        assertThat(d.startIndex).isEqualTo(1)
        assertThat(d.startPositionMs).isEqualTo(0L)
        assertThat(d.consumePendingIndex).isTrue()
        assertThat(d.markSetupDone).isTrue()
    }

    @Test
    fun pendingIndex_beyondBounds_isClampedToLast() {
        val d = KidPlayerPlanner.decide(uris, null, false, 99, null)
        assertThat(d.startIndex).isEqualTo(2)
    }

    @Test
    fun firstRestore_usesSavedUriAndPosition() {
        val d = KidPlayerPlanner.decide(uris, null, false, -1, PlaybackState("file:///b.mp4", 42_000L))
        assertThat(d.startIndex).isEqualTo(1)
        assertThat(d.startPositionMs).isEqualTo(42_000L)
        assertThat(d.clearSavedState).isFalse()
        assertThat(d.markSetupDone).isTrue()
    }

    @Test
    fun firstRestore_savedVideoGone_clearsSavedState() {
        val d = KidPlayerPlanner.decide(uris, null, false, -1, PlaybackState("file:///gone.mp4", 42_000L))
        assertThat(d.startIndex).isEqualTo(0)
        assertThat(d.startPositionMs).isEqualTo(0L)
        assertThat(d.clearSavedState).isTrue()
    }

    @Test
    fun firstRestore_noSavedState_startsAtTopWithoutClear() {
        val d = KidPlayerPlanner.decide(uris, null, false, -1, null)
        assertThat(d.startIndex).isEqualTo(0)
        assertThat(d.startPositionMs).isEqualTo(0L)
        assertThat(d.clearSavedState).isFalse()
    }

    @Test
    fun refresh_currentVideoStillPresent_keepsItsIndexAndCurrentPosition() {
        val d = KidPlayerPlanner.decide(uris, "file:///c.mp4", true, -1, null)
        assertThat(d.startIndex).isEqualTo(2)
        assertThat(d.startPositionMs).isNull()
        assertThat(d.markSetupDone).isTrue()
    }

    @Test
    fun refresh_currentVideoRemoved_restartsFromTop() {
        val d = KidPlayerPlanner.decide(uris, "file:///removed.mp4", true, -1, null)
        assertThat(d.startIndex).isEqualTo(0)
        assertThat(d.startPositionMs).isEqualTo(0L)
        assertThat(d.markSetupDone).isTrue()
    }

    @Test
    fun refresh_noCurrentVideo_restartsFromTop() {
        val d = KidPlayerPlanner.decide(uris, null, true, -1, null)
        assertThat(d.startIndex).isEqualTo(0)
        assertThat(d.markSetupDone).isTrue()
    }
}
