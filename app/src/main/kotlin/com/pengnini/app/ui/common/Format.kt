package com.pengnini.app.ui.common

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
