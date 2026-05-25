package com.pengnini.app.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pengnini.app.Container
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.ui.common.formatDuration
import com.pengnini.app.ui.common.formatResolution
import kotlinx.coroutines.delay

private val SpeedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4.0f

private fun formatSpeed(s: Float): String {
    val rounded = (s * 100).toInt() / 100f
    return if (rounded * 10f == (rounded * 10f).toInt().toFloat()) {
        "%.1f×".format(rounded)
    } else {
        "%.2f×".format(rounded)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onPlayOther: (String) -> Unit = {},
    vm: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val video by vm.video.collectAsStateWithLifecycle()
    val prevUri by vm.prevUri.collectAsStateWithLifecycle()
    val nextUri by vm.nextUri.collectAsStateWithLifecycle()
    val loop by vm.loopEnabled.collectAsStateWithLifecycle()
    val keepOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val backgroundPlayback by vm.backgroundPlayback.collectAsStateWithLifecycle()
    val speedX10 by vm.playbackSpeedX10.collectAsStateWithLifecycle()
    val aspect by vm.playbackAspect.collectAsStateWithLifecycle()
    val subtitleAuto by vm.subtitleAuto.collectAsStateWithLifecycle()
    val subtitleSize by vm.subtitleSize.collectAsStateWithLifecycle()
    val seekSec by vm.gestureSeekSec.collectAsStateWithLifecycle()
    val gBrightness by vm.gestureBrightness.collectAsStateWithLifecycle()
    val gVolume by vm.gestureVolume.collectAsStateWithLifecycle()
    val gZoom by vm.gestureZoom.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val syncOffsetMs by vm.syncOffsetMs.collectAsStateWithLifecycle()
    val strokeMin by vm.strokeMin.collectAsStateWithLifecycle()
    val strokeMax by vm.strokeMax.collectAsStateWithLifecycle()

    // 회전 잠금 상태. null = 잠금 안 함 (시스템 따라가기), LANDSCAPE/PORTRAIT = 잠금.
    var orientationLock by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(orientationLock) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.requestedOrientation = orientationLock ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (video == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = Color.White) }
        return
    }
    val v = video!!

    var subtitleEnabled by remember { mutableStateOf(true) }
    var scriptPopupOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    var zoomScale by remember(v.uri) { mutableStateOf(1f) }
    var zoomVersion by remember { mutableStateOf(0) }
    var zoomIndicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(zoomVersion) {
        if (zoomVersion > 0) {
            zoomIndicatorVisible = true
            delay(900)
            zoomIndicatorVisible = false
        }
    }

    var sessionSpeed by remember(v.uri) { mutableStateOf(speedX10 / 10f) }

    val exoPlayer = remember(v.uri) {
        ExoPlayer.Builder(context).build().also {
            it.setMediaItem(buildMediaItem(v, subtitleAuto))
            it.prepare()
            if (v.lastPositionMs > 0 && v.lastPositionMs < v.durationMs - 5000) {
                it.seekTo(v.lastPositionMs)
            }
            it.playWhenReady = true
            it.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            it.playbackParameters = PlaybackParameters(sessionSpeed)
        }
    }

    LaunchedEffect(sessionSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(sessionSpeed)
    }
    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    var subtitleAutoInited by remember { mutableStateOf(false) }
    LaunchedEffect(subtitleAuto) {
        if (!subtitleAutoInited) {
            subtitleAutoInited = true
            return@LaunchedEffect
        }
        val cur = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        exoPlayer.setMediaItem(buildMediaItem(v, subtitleAuto))
        exoPlayer.prepare()
        exoPlayer.seekTo(cur)
        exoPlayer.playWhenReady = wasPlaying
    }

    LaunchedEffect(subtitleEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .build()
    }

    LaunchedEffect(v.uri) {
        if (v.funscriptUri != null && Container.handyRepo.hasKey) vm.startSync()
    }

    LaunchedEffect(syncState) {
        if (syncState is HandySyncState.Ready && exoPlayer.isPlaying) {
            vm.onPlay(exoPlayer.currentPosition)
        }
    }

    // 스크립트 오프셋 변경 → 200ms debounce 후 재생 중이면 Handy 재시작
    var offsetInited by remember { mutableStateOf(false) }
    LaunchedEffect(syncOffsetMs) {
        if (!offsetInited) {
            offsetInited = true
            return@LaunchedEffect
        }
        delay(200)
        if (exoPlayer.isPlaying && syncState is HandySyncState.Ready) {
            vm.onPlay(exoPlayer.currentPosition)
        }
    }

    // 재생속도 ≠ 1.0 → Handy도 영상과 동기 유지하도록 주기적으로 startTime 재전송.
    // (Handy HSSP는 playbackRate API가 없어 자체 1x 속도로 흐르므로 빗나감을 보정)
    LaunchedEffect(sessionSpeed, syncState) {
        if (sessionSpeed == 1.0f) return@LaunchedEffect
        if (syncState !is HandySyncState.Ready) return@LaunchedEffect
        while (true) {
            delay(500)
            if (exoPlayer.isPlaying) {
                vm.onPlay(exoPlayer.currentPosition)
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5000)
            if (exoPlayer.isPlaying) {
                vm.savePosition(exoPlayer.currentPosition)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) vm.onPlay(exoPlayer.currentPosition)
                else vm.onPause()
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                ) {
                    vm.onSeek(newPosition.positionMs, exoPlayer.isPlaying)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) vm.resetPosition()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val pos = exoPlayer.currentPosition
            if (pos > 0 && pos < v.durationMs - 5000) vm.savePosition(pos)
            else if (pos >= v.durationMs - 5000) vm.resetPosition()
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.release() }
        }
    }

    DisposableEffect(lifecycleOwner, backgroundPlayback) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (backgroundPlayback) PlaybackService.start(context)
                    else exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> PlaybackService.stop(context)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            PlaybackService.stop(context)
        }
    }

    KeepScreenOnEffect(enabled = keepOn)

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var positionMs by remember { mutableStateOf(exoPlayer.currentPosition) }
    var seekingPositionMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(controlsVisible, isPlaying, scriptPopupOpen, speedMenuOpen) {
        if (controlsVisible && isPlaying && !scriptPopupOpen && !speedMenuOpen) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            if (seekingPositionMs == null) positionMs = exoPlayer.currentPosition
            delay(200)
        }
    }

    val subtitleFraction = subtitleScale(subtitleSize)
    val hasSubtitle = v.subtitleUri != null && subtitleAuto

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val pv = LayoutInflater.from(ctx).inflate(R.layout.player_view, null, false) as PlayerView
                pv.player = exoPlayer
                pv.useController = false
                pv.setShowSubtitleButton(false)
                pv.resizeMode = aspectToResize(aspect)
                pv.subtitleView?.setFractionalTextSize(subtitleFraction)
                pv
            },
            update = {
                it.player = exoPlayer
                it.resizeMode = aspectToResize(aspect)
                it.subtitleView?.setFractionalTextSize(subtitleFraction)
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                },
        )

        GestureOverlay(
            seekSec = seekSec,
            enableBrightness = gBrightness,
            enableVolume = gVolume,
            enableZoom = gZoom,
            onToggleControls = { controlsVisible = !controlsVisible },
            onSeekRelative = { delta ->
                val newPos = (exoPlayer.currentPosition + delta).coerceIn(0L, v.durationMs)
                exoPlayer.seekTo(newPos)
                positionMs = newPos
                controlsVisible = true
            },
            onZoom = { delta ->
                zoomScale = (zoomScale * delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
                zoomVersion++
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (zoomIndicatorVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        "${(zoomScale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            TopBar(
                title = v.title,
                video = v,
                syncState = syncState,
                onRetrySync = { vm.retrySync() },
                speedLabel = formatSpeed(sessionSpeed),
                speedMenuOpen = speedMenuOpen,
                currentSpeed = sessionSpeed,
                onSpeedClick = { speedMenuOpen = !speedMenuOpen; controlsVisible = true },
                onSpeedDismiss = { speedMenuOpen = false },
                onSpeedSelect = { sessionSpeed = it; speedMenuOpen = false },
                scriptIconVisible = v.funscriptUri != null,
                scriptPopupOpen = scriptPopupOpen,
                onScriptClick = { scriptPopupOpen = !scriptPopupOpen; controlsVisible = true },
                onScriptDismiss = { scriptPopupOpen = false },
                offsetMs = syncOffsetMs,
                strokeMin = strokeMin,
                strokeMax = strokeMax,
                onOffsetChange = { vm.setSyncOffsetMs(it) },
                onStrokeRangeChange = { lo, hi -> vm.setStrokeRange(lo, hi) },
                onRotateClick = {
                    orientationLock = when (orientationLock) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    controlsVisible = true
                },
                onBack = onBack,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            BottomBar(
                positionMs = seekingPositionMs ?: positionMs,
                durationMs = v.durationMs,
                isPlaying = isPlaying,
                loopEnabled = loop,
                hasSubtitle = hasSubtitle,
                subtitleEnabled = subtitleEnabled,
                hasPrev = prevUri != null,
                hasNext = nextUri != null,
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    controlsVisible = true
                },
                onPrev = { prevUri?.let { onPlayOther(it) } },
                onNext = { nextUri?.let { onPlayOther(it) } },
                onLoopToggle = {
                    vm.setLoop(!loop)
                    controlsVisible = true
                },
                onSubtitleToggle = {
                    subtitleEnabled = !subtitleEnabled
                    controlsVisible = true
                },
                onSeekChange = { seekingPositionMs = it.toLong() },
                onSeekEnd = {
                    seekingPositionMs?.let { exoPlayer.seekTo(it) }
                    seekingPositionMs = null
                    controlsVisible = true
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildMediaItem(v: VideoEntity, subtitleAuto: Boolean): MediaItem {
    val builder = MediaItem.Builder().setUri(android.net.Uri.parse(v.uri))
    val sub = v.subtitleUri
    if (subtitleAuto && sub != null) {
        val mime = when {
            sub.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
            sub.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            sub.endsWith(".ass", true) || sub.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub))
                    .setMimeType(mime)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
            ),
        )
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
private fun aspectToResize(aspect: String): Int = when (aspect) {
    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    "16:9", "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun subtitleScale(size: String): Float = when (size) {
    "small" -> 0.04f
    "large" -> 0.075f
    else -> 0.0533f
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun TopBar(
    title: String,
    video: VideoEntity,
    syncState: HandySyncState,
    onRetrySync: () -> Unit,
    speedLabel: String,
    speedMenuOpen: Boolean,
    currentSpeed: Float,
    onSpeedClick: () -> Unit,
    onSpeedDismiss: () -> Unit,
    onSpeedSelect: (Float) -> Unit,
    scriptIconVisible: Boolean,
    scriptPopupOpen: Boolean,
    onScriptClick: () -> Unit,
    onScriptDismiss: () -> Unit,
    offsetMs: Int,
    strokeMin: Int,
    strokeMax: Int,
    onOffsetChange: (Int) -> Unit,
    onStrokeRangeChange: (Int, Int) -> Unit,
    onRotateClick: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent),
                ),
            )
            .padding(start = 4.dp, end = 8.dp, top = 20.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(video.durationMs),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
                formatResolution(video.width, video.height)?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                if (video.funscriptUri != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("★ Sync", color = Color(0xFF9C7CFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onSpeedClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    speedLabel,
                    color = if (speedMenuOpen) Color(0xFF9C7CFF) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            DropdownMenu(
                expanded = speedMenuOpen,
                onDismissRequest = onSpeedDismiss,
            ) {
                SpeedOptions.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatSpeed(s),
                                fontWeight = if (s == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                                color = if (s == currentSpeed) Color(0xFF9C7CFF)
                                else Color.Unspecified,
                            )
                        },
                        onClick = { onSpeedSelect(s) },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        if (scriptIconVisible) {
            Box {
                IconButton(onClick = onScriptClick) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "스크립트 퀵슬롯",
                        tint = if (scriptPopupOpen) Color(0xFF9C7CFF) else Color.White,
                    )
                }
                if (scriptPopupOpen) {
                    ScriptQuickslotPopup(
                        offsetMs = offsetMs,
                        strokeMin = strokeMin,
                        strokeMax = strokeMax,
                        onOffsetChange = onOffsetChange,
                        onStrokeRangeChange = onStrokeRangeChange,
                        onDismiss = onScriptDismiss,
                    )
                }
            }
        }
        IconButton(onClick = onRotateClick) {
            Icon(
                Icons.Outlined.ScreenRotation,
                contentDescription = "화면 회전 토글",
                tint = Color.White,
            )
        }
        if (video.funscriptUri != null) {
            SyncStateBadge(syncState, onRetry = onRetrySync)
        }
    }
}

@Composable
private fun ScriptQuickslotPopup(
    offsetMs: Int,
    strokeMin: Int,
    strokeMax: Int,
    onOffsetChange: (Int) -> Unit,
    onStrokeRangeChange: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 56),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.88f),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "스크립트 오프셋",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${if (offsetMs > 0) "+" else ""}$offsetMs ms",
                    color = Color(0xFF9C7CFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = offsetMs.toFloat(),
                    onValueChange = { onOffsetChange(it.toInt()) },
                    valueRange = -200f..200f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF9C7CFF),
                        thumbColor = Color(0xFF9C7CFF),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "스트로크 범위",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$strokeMin ~ $strokeMax",
                    color = Color(0xFF5EDFC1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                RangeSlider(
                    value = strokeMin.toFloat()..strokeMax.toFloat(),
                    onValueChange = { onStrokeRangeChange(it.start.toInt(), it.endInclusive.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF5EDFC1),
                        thumbColor = Color(0xFF5EDFC1),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    loopEnabled: Boolean,
    hasSubtitle: Boolean,
    subtitleEnabled: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLoopToggle: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    var savedVolume by remember { mutableStateOf(if (volume > 0f) volume else 0.5f) }
    val isMuted = volume <= 0.001f

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val sys = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
            if (kotlin.math.abs(sys - volume) > 0.001f) volume = sys
        }
    }

    fun setVol(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        volume = clamped
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (clamped * maxVol).toInt(), 0)
    }
    fun toggleMute() {
        if (isMuted) {
            setVol(savedVolume.coerceAtLeast(0.05f))
        } else {
            savedVolume = volume
            setVol(0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                ),
            )
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val safeDur = durationMs.coerceAtLeast(1L).toFloat()
        Slider(
            value = positionMs.coerceIn(0, durationMs).toFloat(),
            onValueChange = { onSeekChange(it) },
            onValueChangeFinished = onSeekEnd,
            valueRange = 0f..safeDur,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(0xFF9C7CFF),
                thumbColor = Color(0xFF9C7CFF),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(positionMs), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            // 재생 그룹 (좌측)
            IconButton(onClick = onPrev, enabled = hasPrev) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = "이전 영상",
                    tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Outlined.SkipNext,
                    contentDescription = "다음 영상",
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
            Spacer(Modifier.width(4.dp))
            // 소리 그룹 (좌측에 붙음)
            IconButton(onClick = { toggleMute() }) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff
                    else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = if (isMuted) "음소거 해제" else "음소거",
                    tint = Color.White,
                )
            }
            Slider(
                value = volume,
                onValueChange = { setVol(it) },
                valueRange = 0f..1f,
                modifier = Modifier.width(96.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    thumbColor = Color.White,
                ),
            )
            // 가운데 빈 공간
            Spacer(Modifier.weight(1f))
            // CC + 루프 (우측에 붙음)
            IconButton(onClick = onSubtitleToggle, enabled = hasSubtitle) {
                Icon(
                    imageVector = if (subtitleEnabled && hasSubtitle) Icons.Outlined.ClosedCaption
                    else Icons.Outlined.ClosedCaptionDisabled,
                    contentDescription = if (subtitleEnabled) "자막 끄기" else "자막 켜기",
                    tint = when {
                        !hasSubtitle -> Color.White.copy(alpha = 0.3f)
                        subtitleEnabled -> Color(0xFF9C7CFF)
                        else -> Color.White
                    },
                )
            }
            IconButton(onClick = onLoopToggle) {
                Icon(
                    imageVector = Icons.Outlined.Loop,
                    contentDescription = if (loopEnabled) "루프 끄기" else "루프 켜기",
                    tint = if (loopEnabled) Color(0xFF9C7CFF) else Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(durationMs), color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SyncStateBadge(state: HandySyncState, onRetry: () -> Unit) {
    val info: Triple<String, Color, Boolean>? = when (state) {
        HandySyncState.Idle -> null
        HandySyncState.Uploading,
        HandySyncState.Preparing,
        -> Triple("Sync 준비 중", Color(0xFFFBC02D), false)
        HandySyncState.Ready -> Triple("Handy ●", Color(0xFF66BB6A), false)
        is HandySyncState.Error -> Triple("Handy 오류 · 재시도", Color(0xFFEF5350), true)
    }
    if (info == null) return
    val (label, tint, clickable) = info
    Surface(
        color = tint.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp),
        modifier = if (clickable) Modifier.clickable(onClick = onRetry) else Modifier,
    ) {
        Text(
            label,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
