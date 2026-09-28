package com.dima.bbvideoplayer.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.bbvideoplayer.ui.screens.kidplayer.PlayerControlsUi
import com.dima.bbvideoplayer.ui.theme.BlueButton
import com.dima.bbvideoplayer.ui.theme.GreenPrimary

private val ControlsFadeSpec = tween<Float>(500)
private val SeekStepMs = 10_000L
private const val NaturalButtonSizeDp = 80f

/**
 * TV controls bar: a horizontal button row plus the seek slider, both anchored
 * to the bottom of the screen (phone edition keeps them in a left-hand column).
 * Focus is placed on play/pause whenever the bar becomes visible.
 */
@Composable
fun BoxScope.TvPlayerControlsOverlay(ui: PlayerControlsUi) {
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(ui.visible) {
        if (ui.visible) playPauseFocusRequester.requestFocus()
    }

    AnimatedVisibility(
        visible = ui.visible,
        enter = fadeIn(ControlsFadeSpec),
        exit = fadeOut(ControlsFadeSpec),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
                .padding(start = 140.dp, end = 140.dp, top = 32.dp, bottom = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonSize = NaturalButtonSizeDp.dp

                TvControlButton(
                    text = "⏮",
                    enabled = ui.videoCount > 1,
                    backgroundColor = BlueButton,
                    size = buttonSize,
                    onClick = ui.onPrevious
                )

                TvControlButton(
                    icon = Icons.Default.FastRewind,
                    backgroundColor = BlueButton,
                    size = buttonSize,
                    onClick = { ui.onSeekBackward(SeekStepMs) }
                )

                TvControlButton(
                    icon = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    backgroundColor = if (ui.isPlaying) BlueButton else GreenPrimary,
                    size = buttonSize,
                    focusRequester = playPauseFocusRequester,
                    onClick = ui.onPlayPause
                )

                TvControlButton(
                    icon = Icons.Default.FastForward,
                    backgroundColor = BlueButton,
                    size = buttonSize,
                    onClick = { ui.onSeekForward(SeekStepMs) }
                )

                TvControlButton(
                    text = "⏭",
                    enabled = ui.videoCount > 1,
                    backgroundColor = BlueButton,
                    size = buttonSize,
                    onClick = ui.onNext
                )
            }

            Slider(
                value = ui.sliderValue,
                onValueChange = ui.onSliderChange,
                onValueChangeFinished = ui.onSliderChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}

/**
 * D-pad friendly control button: a white focus ring + grow effect show where
 * the remote focus is, which colored kid-style buttons alone cannot convey.
 */
@Composable
fun TvControlButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String = "",
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    size: Dp = 72.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1f,
        label = "tvControlButtonScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f)
        ),
        shadowElevation = if (isFocused) 12.dp else 6.dp,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(size)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 36 / 80)
                )
            } else {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
