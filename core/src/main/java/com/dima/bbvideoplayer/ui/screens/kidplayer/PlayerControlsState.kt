package com.dima.bbvideoplayer.ui.screens.kidplayer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Controls visibility owned by [com.dima.bbvideoplayer.ui.screens.BaseKidPlayerScreen]
 * but hoisted so platform screens can react: the phone screen ignores it, the TV
 * screen shows the controls again on any D-pad key and maps BACK to show/hide.
 */
@Stable
class PlayerControlsVisibility(initiallyVisible: Boolean = true) {
    var visible: Boolean by mutableStateOf(initiallyVisible)
        internal set

    fun show() {
        visible = true
    }

    fun hide() {
        visible = false
    }
}

/**
 * Everything a platform controls overlay needs to render and act.
 * One instance per recomposition, built by BaseKidPlayerScreen.
 */
@Stable
class PlayerControlsUi(
    val visible: Boolean,
    val videoCount: Int,
    val isPlaying: Boolean,
    val sliderValue: Float,
    val onSliderChange: (Float) -> Unit,
    val onSliderChangeFinished: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekBackward: (Long) -> Unit,
    val onSeekForward: (Long) -> Unit
)
