package com.dima.bbvideoplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.ui.components.MAX_PIN_ATTEMPTS
import com.dima.bbvideoplayer.ui.components.PinDialog
import com.dima.bbvideoplayer.ui.components.PinValidator
import com.dima.bbvideoplayer.ui.screens.kidplayer.BatteryIndicator
import com.dima.bbvideoplayer.ui.screens.kidplayer.PlayerControlsOverlay
import com.dima.bbvideoplayer.ui.screens.kidplayer.PlayerControlsVisibility
import com.dima.bbvideoplayer.ui.screens.kidplayer.SecretDoorGesture

/**
 * Phone kid player: [BaseKidPlayerScreen] with the phone-specific pieces —
 * left-hand controls overlay, battery indicator, secret-door parent entry
 * protected by the PIN dialog.
 */
@Composable
fun KidPlayerScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    onSecretDoorActivated: () -> Unit,
    pendingStartVideoIndex: Int = -1,
    onPendingIndexConsumed: () -> Unit = {}
) {
    var showParentPin by remember { mutableStateOf(false) }
    val parentPin by videoRepository.parentPin.collectAsStateWithLifecycle(
        initialValue = VideoRepository.DEFAULT_PARENT_PIN
    )
    val controlsSide by videoRepository.controlsSide.collectAsStateWithLifecycle(
        initialValue = VideoRepository.CONTROLS_SIDE_LEFT
    )
    // Hoisted above the dialog: attempt counter and lockout survive dialog reopen,
    // so the brute-force protection cannot be reset by cancelling and retrying.
    val pinValidator = remember(parentPin) { PinValidator(parentPin, MAX_PIN_ATTEMPTS) }
    val controlsVisibility = remember { PlayerControlsVisibility() }

    if (showParentPin) {
        PinDialog(
            title = "Введите ПИН для родительского режима",
            correctPin = parentPin,
            pinValidator = pinValidator,
            onDismiss = { showParentPin = false },
            onPinCorrect = {
                showParentPin = false
                onSecretDoorActivated()
            }
        )
    }

    BaseKidPlayerScreen(
        videoRepository = videoRepository,
        videoLibraryService = videoLibraryService,
        videoPlayerManager = videoPlayerManager,
        playbackStateRepository = playbackStateRepository,
        controlsVisibility = controlsVisibility,
        controlsOverlay = { ui ->
            PlayerControlsOverlay(
                visible = ui.visible,
                filteredVideoCount = ui.videoCount,
                isPlaying = ui.isPlaying,
                sliderValue = ui.sliderValue,
                onSliderChange = ui.onSliderChange,
                onSliderChangeFinished = ui.onSliderChangeFinished,
                onPrevious = ui.onPrevious,
                onNext = ui.onNext,
                onPlayPause = ui.onPlayPause,
                onSeekBackward = ui.onSeekBackward,
                onSeekForward = ui.onSeekForward,
                controlsSide = controlsSide
            )
        },
        parentArea = {
            BatteryIndicator()
            SecretDoorGesture(
                onActivated = { showParentPin = true }
            )
        },
        pendingStartVideoIndex = pendingStartVideoIndex,
        onPendingIndexConsumed = onPendingIndexConsumed
    )
}
