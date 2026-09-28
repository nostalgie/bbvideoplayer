package com.dima.bbvideoplayer.ui.screens

import com.dima.bbvideoplayer.data.PlaybackStateRepository

/**
 * Pure decision logic for which video KidPlayerScreen should start (or keep) playing.
 * Extracted from the compose effect so the branching is unit-testable.
 *
 * [KidStartDecision.startPositionMs] of null means "keep the player current position"
 * (used when the same video continues under a reordered/refreshed library).
 * [KidStartDecision.markSetupDone] = true means the caller must run
 * setVideoList and set the setup/restore flags.
 */
data class KidStartDecision(
    val startIndex: Int,
    val startPositionMs: Long?,
    val consumePendingIndex: Boolean,
    val markSetupDone: Boolean,
    val clearSavedState: Boolean
)

object KidPlayerPlanner {

    fun decide(
        playableUris: List<String>,
        currentUri: String?,
        hasRestoredState: Boolean,
        pendingStartVideoIndex: Int,
        saved: PlaybackStateRepository.PlaybackState?
    ): KidStartDecision {
        if (playableUris.isEmpty()) {
            return KidStartDecision(0, 0L, false, false, false)
        }

        if (pendingStartVideoIndex >= 0) {
            return KidStartDecision(
                startIndex = pendingStartVideoIndex.coerceIn(0, playableUris.lastIndex),
                startPositionMs = 0L,
                consumePendingIndex = true,
                markSetupDone = true,
                clearSavedState = false
            )
        }

        if (!hasRestoredState) {
            if (saved != null) {
                val savedIndex = playableUris.indexOf(saved.videoUri)
                if (savedIndex >= 0) {
                    return KidStartDecision(savedIndex, saved.positionMs, false, true, false)
                }
                // Saved video is gone from the library: start fresh and drop the stale state.
                return KidStartDecision(0, 0L, false, true, true)
            }
            return KidStartDecision(0, 0L, false, true, false)
        }

        // Library refresh after setup: keep watching where we were.
        val currentIndex = currentUri?.let { playableUris.indexOf(it) }
        return if (currentIndex != null && currentIndex >= 0) {
            KidStartDecision(currentIndex, null, false, true, false)
        } else {
            // Current video disappeared (renamed/removed): fall back to the top of the new list.
            KidStartDecision(0, 0L, false, true, false)
        }
    }
}
