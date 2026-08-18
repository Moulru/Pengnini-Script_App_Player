package com.pengnini.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pengnini.app.R

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val s = totalSec % 60
    val m = (totalSec / 60) % 60
    val h = totalSec / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatResolution(w: Int, h: Int): String? {
    if (w <= 0 || h <= 0) return null
    val tag = when {
        h >= 2160 -> "4K"
        h >= 1080 -> "1080p"
        h >= 720 -> "720p"
        h >= 480 -> "480p"
        else -> "${h}p"
    }
    return tag
}

fun formatFileSize(bytes: Long): String? {
    if (bytes <= 0) return null
    val gb = 1024.0 * 1024 * 1024
    val mb = 1024.0 * 1024
    return when {
        bytes >= gb -> "%.1fGB".format(bytes / gb)
        bytes >= mb -> "%.0fMB".format(bytes / mb)
        else -> "%.0fKB".format(bytes / 1024.0)
    }
}

/** "5분 전" 같은 상대 시각. null이면 아직 스캔한 적 없음. */
@Composable
fun formatRelativeTime(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0) return stringResource(R.string.time_never)
    val diff = System.currentTimeMillis() - epochMs
    val min = diff / 60_000
    val hour = min / 60
    val day = hour / 24
    return when {
        min < 1 -> stringResource(R.string.time_just_now)
        min < 60 -> stringResource(R.string.time_minutes_ago, min)
        hour < 24 -> stringResource(R.string.time_hours_ago, hour)
        else -> stringResource(R.string.time_days_ago, day)
    }
}
