package com.dima.bbvideoplayer.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.bbvideoplayer.data.PlaybackStateRepository
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.player.VideoPlayerManager
import com.dima.bbvideoplayer.ui.screens.BaseKidPlayerScreen
import com.dima.bbvideoplayer.ui.screens.kidplayer.PlayerControlsVisibility

/**
 * TV kid player: [BaseKidPlayerScreen] with the bottom controls bar, a visible
 * "Родителям" button instead of the phone's secret door + PIN, and D-pad
 * plumbing (BACK toggles the bar; any D-pad key re-shows it when hidden).
 */
@Composable
fun TvPlayerScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    onOpenParentDashboard: () -> Unit,
    pendingStartVideoIndex: Int = -1,
    onPendingIndexConsumed: () -> Unit = {}
) {
    val controlsVisibility = remember { PlayerControlsVisibility() }
    val keyCatcherFocusRequester = remember { FocusRequester() }

    // BACK never leaves the player: show the bar when hidden, hide when shown.
    BackHandler {
        if (controlsVisibility.visible) controlsVisibility.hide() else controlsVisibility.show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BaseKidPlayerScreen(
            videoRepository = videoRepository,
            videoLibraryService = videoLibraryService,
            videoPlayerManager = videoPlayerManager,
            playbackStateRepository = playbackStateRepository,
            controlsVisibility = controlsVisibility,
            onControlsVisibilityChanged = { visible ->
                if (!visible) {
                    // The bar is fading out — nothing will hold focus after the
                    // buttons leave composition; park focus on the key catcher.
                    keyCatcherFocusRequester.requestFocus()
                }
            },
            controlsOverlay = { ui ->
                TvPlayerControlsOverlay(ui)
            },
            parentArea = {
                TvParentButton(onClick = onOpenParentDashboard)
            },
            pendingStartVideoIndex = pendingStartVideoIndex,
            onPendingIndexConsumed = onPendingIndexConsumed
        )

        // Invisible focus target used while the controls bar is hidden.
        Box(
            modifier = Modifier
                .size(1.dp)
                .focusRequesterAndCatchKeys(keyCatcherFocusRequester, controlsVisibility)
        )
    }
}

private fun Modifier.focusRequesterAndCatchKeys(
    focusRequester: FocusRequester,
    controlsVisibility: PlayerControlsVisibility
): Modifier = this
    .focusRequester(focusRequester)
    .focusable()
    .onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            !controlsVisibility.visible &&
            event.isDpadKey()
        ) {
            controlsVisibility.show()
            true
        } else {
            false
        }
    }

private fun KeyEvent.isDpadKey(): Boolean = when (key) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter -> true
    else -> false
}

@Composable
private fun BoxScope.TvParentButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f)
        ),
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Text(
            text = "Родителям",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
