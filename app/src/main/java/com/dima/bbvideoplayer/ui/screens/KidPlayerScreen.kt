package com.dima.bbvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.videolan.libvlc.util.VLCVideoLayout
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.ui.components.MAX_PIN_ATTEMPTS
import com.dima.bbvideoplayer.ui.components.PinDialog
import com.dima.bbvideoplayer.ui.components.PinValidator
import com.dima.bbvideoplayer.ui.screens.kidplayer.BatteryIndicator
import com.dima.bbvideoplayer.ui.screens.kidplayer.PlayerControlsOverlay
import com.dima.bbvideoplayer.ui.screens.kidplayer.SecretDoorGesture
import kotlinx.coroutines.delay

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
    val secretDoorTouchSizePx = with(LocalDensity.current) { 72.dp.toPx() }
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val watchedFolderSet = remember(watchedFolders) { watchedFolders.toSet() }

    val playableUris = remember(libraryState.videos, watchedFolderSet) {
        if (watchedFolderSet.isEmpty()) emptyList()
        else libraryState.videos
            .filter { it.sourceFolder in watchedFolderSet }
            .map { it.uriString }
    }

    LaunchedEffect(libraryState.uriMigrations) {
        libraryState.uriMigrations.forEach { (oldUri, newUri) ->
            playbackStateRepository.migrateUri(oldUri, newUri)
        }
    }

    var hasRestoredState by remember { mutableStateOf(false) }

    LaunchedEffect(playableUris, pendingStartVideoIndex) {
        videoPlayerManager.initialize()

        if (playableUris.isEmpty()) {
            videoPlayerManager.setVideoList(emptyList())
            return@LaunchedEffect
        }

        val saved = if (!hasRestoredState && pendingStartVideoIndex < 0) {
            playbackStateRepository.get()
        } else {
            null
        }
        val decision = KidPlayerPlanner.decide(
            playableUris = playableUris,
            currentUri = videoPlayerManager.getCurrentVideoUri(),
            hasRestoredState = hasRestoredState,
            pendingStartVideoIndex = pendingStartVideoIndex,
            saved = saved
        )
        if (decision.consumePendingIndex) {
            onPendingIndexConsumed()
        }
        if (decision.clearSavedState) {
            playbackStateRepository.clear()
        }
        if (decision.markSetupDone) {
            hasRestoredState = true
            val positionMs = decision.startPositionMs ?: videoPlayerManager.currentPosition
            videoPlayerManager.setVideoList(playableUris, decision.startIndex, positionMs)
        }
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(videoPlayerManager.isPlaying) }
    var showParentPin by remember { mutableStateOf(false) }
    val parentPin by videoRepository.parentPin.collectAsStateWithLifecycle(
        initialValue = VideoRepository.DEFAULT_PARENT_PIN
    )
    // Hoisted above the dialog: attempt counter and lockout survive dialog reopen,
    // so the brute-force protection cannot be reset by cancelling and retrying.
    val pinValidator = remember(parentPin) { PinValidator(parentPin, MAX_PIN_ATTEMPTS) }

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

    LaunchedEffect(videoPlayerManager) {
        while (true) {
            isPlaying = videoPlayerManager.isPlaying
            delay(300)
        }
    }

    LaunchedEffect(controlsVisible, controlsInteraction, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    var sliderValue by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1L) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(videoPlayerManager, controlsVisible, isSeeking) {
        if (!controlsVisible) return@LaunchedEffect
        while (controlsVisible) {
            if (!isSeeking) {
                val pos = videoPlayerManager.currentPosition
                val dur = videoPlayerManager.duration.coerceAtLeast(1L)
                duration = dur
                sliderValue = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    LaunchedEffect(videoPlayerManager, playableUris, playbackError == null) {
        if (playableUris.isEmpty() || playbackError != null) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val uri = videoPlayerManager.getCurrentVideoUri() ?: continue
            if (uri !in playableUris) continue
            playbackStateRepository.save(
                PlaybackStateRepository.PlaybackState(
                    videoUri = uri,
                    positionMs = videoPlayerManager.currentPosition
                )
            )
        }
    }

    DisposableEffect(videoPlayerManager) {
        videoPlayerManager.playbackListener = VideoPlayerManager.PlaybackListener(
            onError = { error ->
                playbackError = error
            },
            onReady = { playbackError = null }
        )
        onDispose { videoPlayerManager.playbackListener = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (playableUris.isNotEmpty()) {
            AndroidView(
                factory = { ctx -> VLCVideoLayout(ctx) },
                update = { layout ->
                    videoPlayerManager.attachVideoLayout(layout)
                    if (layout.width > 0 && layout.height > 0) {
                        videoPlayerManager.updateVideoSurfaceSize(layout.width, layout.height)
                    }
                },
                onRelease = { videoPlayerManager.detachVideoLayout() },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        videoPlayerManager.updateVideoSurfaceSize(size.width, size.height)
                    }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (watchedFolders.isEmpty()) {
                        "🎬 Нет видео\n\nРодитель может добавить папки\nчерез настройки"
                    } else {
                        "🎬 Нет выбранных папок\n\nПожалуйста, выберите папки\nв настройках родителя"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        playbackError?.let { error ->
            Text(
                text = "⚠️ Не удалось воспроизвести видео\n($error)",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        if (playableUris.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(controlsVisible, controlsInteraction, isPlaying, secretDoorTouchSizePx) {
                        detectTapGestures { offset ->
                            val inSecretZone = offset.x >= size.width - secretDoorTouchSizePx &&
                                offset.y <= secretDoorTouchSizePx
                            if (!inSecretZone) {
                                if (isPlaying) {
                                    controlsVisible = !controlsVisible
                                } else {
                                    controlsVisible = true
                                }
                                controlsInteraction++
                            }
                        }
                    }
            )

            PlayerControlsOverlay(
                visible = controlsVisible,
                filteredVideoCount = playableUris.size,
                isPlaying = isPlaying,
                sliderValue = sliderValue,
                onSliderChange = { newValue ->
                    isSeeking = true
                    sliderValue = newValue
                    controlsInteraction++
                },
                onSliderChangeFinished = {
                    videoPlayerManager.seekTo((sliderValue * duration).toLong())
                    isSeeking = false
                    controlsInteraction++
                },
                onPrevious = {
                    controlsInteraction++
                    videoPlayerManager.previous()
                },
                onNext = {
                    controlsInteraction++
                    videoPlayerManager.next()
                },
                onPlayPause = {
                    controlsInteraction++
                    if (videoPlayerManager.isPlaying) {
                        videoPlayerManager.pause()
                        controlsVisible = true
                    } else {
                        videoPlayerManager.play()
                    }
                },
                onSeekBackward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekBackward(offsetMs)
                },
                onSeekForward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekForward(offsetMs)
                }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatteryIndicator()
            SecretDoorGesture(
                onActivated = { showParentPin = true }
            )
        }
    }
}
