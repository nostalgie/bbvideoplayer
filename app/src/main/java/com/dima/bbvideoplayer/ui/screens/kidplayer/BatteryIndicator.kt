package com.dima.bbvideoplayer.ui.screens.kidplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val LOW_BATTERY_THRESHOLD = 20
private const val MEDIUM_BATTERY_THRESHOLD = 50
private const val INDICATOR_ALPHA = 0.4f

@Composable
fun BatteryIndicator(modifier: Modifier = Modifier) {
    val batteryInfo = rememberBatteryInfo()
    val level = batteryInfo.level
    // Hide the indicator until the sticky battery broadcast provides a real
    // level; otherwise the first frame renders a red 0% alert.
    if (level == null) return

    val tint = if (level <= LOW_BATTERY_THRESHOLD && !batteryInfo.isCharging) {
        Color.Red.copy(alpha = INDICATOR_ALPHA)
    } else {
        Color.White.copy(alpha = INDICATOR_ALPHA)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = batteryIcon(level, batteryInfo.isCharging),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
        Text(
            text = "$level%",
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun batteryIcon(level: Int, isCharging: Boolean): ImageVector {
    return when {
        isCharging -> Icons.Default.BatteryChargingFull
        level <= LOW_BATTERY_THRESHOLD -> Icons.Default.BatteryAlert
        level <= MEDIUM_BATTERY_THRESHOLD -> Icons.Default.Battery5Bar
        else -> Icons.Default.BatteryFull
    }
}
