package com.pengnini.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class DragRegion { NONE, LEFT, RIGHT }

@Composable
fun GestureOverlay(
    seekSec: Int,
    enableBrightness: Boolean,
    enableVolume: Boolean,
    enableZoom: Boolean,
    onToggleControls: () -> Unit,
    onSeekRelative: (deltaMs: Long) -> Unit,
    onZoom: (delta: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var brightness by remember { mutableStateOf(0.5f) }
    var volume by remember { mutableStateOf(0.5f) }

    // boolean 대신 version 카운터: drag callback 마다 ++ → LaunchedEffect의 key가 매번 바뀌어
    // 이전 hide-delay 가 cancel되고 새 900ms 가 다시 시작 → 마지막 콜백 이후 정확히 900ms 후 사라짐
    var brightnessVersion by remember { mutableStateOf(0) }
    var volumeVersion by remember { mutableStateOf(0) }
    val brightnessVisible = brightnessVersion > 0
    val volumeVisible = volumeVersion > 0

    var dragRegion by remember { mutableStateOf(DragRegion.NONE) }

    LaunchedEffect(brightnessVersion) {
        if (brightnessVersion > 0) {
            delay(900)
            brightnessVersion = 0
        }
    }
    LaunchedEffect(volumeVersion) {
        if (volumeVersion > 0) {
            delay(900)
            volumeVersion = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput("zoom-$enableZoom") {
                if (!enableZoom) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                onZoom(zoom)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput("tap-$seekSec") {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { off ->
                        val isLeft = off.x < size.width / 2
                        onSeekRelative((if (isLeft) -1 else 1) * seekSec * 1000L)
                    },
                )
            }
            .pointerInput("vdrag-$enableBrightness-$enableVolume") {
                if (!enableBrightness && !enableVolume) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        val fraction = off.x / size.width
                        dragRegion = when {
                            fraction < 0.4f && enableBrightness -> {
                                val cur = activity?.window?.attributes?.screenBrightness
                                brightness = cur?.takeIf { it in 0f..1f } ?: 0.5f
                                brightnessVersion++
                                DragRegion.LEFT
                            }
                            fraction > 0.6f && enableVolume -> {
                                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                volumeVersion++
                                DragRegion.RIGHT
                            }
                            else -> DragRegion.NONE
                        }
                    },
                    onDragEnd = { dragRegion = DragRegion.NONE },
                    onDragCancel = { dragRegion = DragRegion.NONE },
                    onVerticalDrag = { change, dragAmountY ->
                        if (dragRegion == DragRegion.NONE) return@detectVerticalDragGestures
                        val range = size.height * 0.5f
                        val delta = -dragAmountY / range
                        when (dragRegion) {
                            DragRegion.LEFT -> {
                                brightness = (brightness + delta).coerceIn(0f, 1f)
                                activity?.window?.let { w ->
                                    val lp = w.attributes
                                    lp.screenBrightness = brightness
                                    w.attributes = lp
                                }
                                brightnessVersion++
                            }
                            DragRegion.RIGHT -> {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                audio.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (volume * maxVol).toInt(),
                                    0,
                                )
                                volumeVersion++
                            }
                            else -> {}
                        }
                        change.consume()
                    },
                )
            },
    ) {
        if (brightnessVisible) {
            IndicatorChip(
                alignment = Alignment.CenterStart,
                label = "밝기 ${(brightness * 100).toInt()}%",
            )
        }
        if (volumeVisible) {
            IndicatorChip(
                alignment = Alignment.CenterEnd,
                label = "볼륨 ${(volume * 100).toInt()}%",
            )
        }
    }
}

@Composable
private fun BoxScope.IndicatorChip(alignment: Alignment, label: String) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 40.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

internal fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
