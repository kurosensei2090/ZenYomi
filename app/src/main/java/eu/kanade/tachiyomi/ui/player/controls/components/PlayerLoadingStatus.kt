package eu.kanade.tachiyomi.ui.player.controls.components

import android.net.TrafficStats
import android.os.Process
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Samples app network RX speed every second while [active] and formats it cleanly.
 */
@Composable
fun rememberLiveNetworkSpeedText(active: Boolean): String? {
    var speedBytesPerSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(active) {
        if (!active) {
            speedBytesPerSec = 0L
            return@LaunchedEffect
        }
        val uid = Process.myUid()
        var lastRx = TrafficStats.getUidRxBytes(uid)
        var lastTimeMs = System.currentTimeMillis()
        while (true) {
            delay(1000L)
            val nowRx = TrafficStats.getUidRxBytes(uid)
            val nowMs = System.currentTimeMillis()
            val dt = (nowMs - lastTimeMs).coerceAtLeast(1L)
            if (lastRx != TrafficStats.UNSUPPORTED.toLong() && nowRx != TrafficStats.UNSUPPORTED.toLong()) {
                val delta = (nowRx - lastRx).coerceAtLeast(0L)
                speedBytesPerSec = (delta * 1000L) / dt
            }
            lastRx = nowRx
            lastTimeMs = nowMs
        }
    }

    if (!active || speedBytesPerSec <= 0L) return null
    return when {
        speedBytesPerSec < 1024L -> "${speedBytesPerSec} B/s"
        speedBytesPerSec < 1024L * 1024L -> "${speedBytesPerSec / 1024L} KB/s"
        else -> "%.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0))
    }
}

@Composable
fun PlayerLoadingStatusBadge(
    statusText: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val speedText = rememberLiveNetworkSpeedText(active = active)
    val label = if (speedText != null) "$statusText • $speedText" else statusText

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
