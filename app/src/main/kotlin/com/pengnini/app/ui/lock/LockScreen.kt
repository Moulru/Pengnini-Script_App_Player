package com.pengnini.app.ui.lock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pengnini.app.Container
import com.pengnini.app.data.secure.LockPattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val ScreenBg = Color(0xFF0F0F14)
private val DotIdle = Color(0xFF3F2D7A)
private val DotActive = Color(0xFF9C7CFF)
private val DotError = Color(0xFFE0506C)

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val store = remember { Container.lockStore }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var attemptVersion by remember { mutableStateOf(0) }
    var failCount by remember { mutableStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        if (error) {
            delay(700)
            error = false
            attemptVersion++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pengnini", color = DotActive, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (error) "패턴이 일치하지 않습니다" else "패턴을 그려주세요",
            color = if (error) DotError else Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(40.dp))
        PatternLock(
            size = 280.dp,
            errorFlash = error,
            resetTrigger = attemptVersion,
            onComplete = { points ->
                val input = LockPattern.tryOf(points)
                val saved = store.read()
                if (input != null && saved != null && input.points == saved.points) {
                    onUnlocked()
                } else {
                    error = true
                    failCount++
                }
            },
        )
        // 5회 이상 실패 시에만 복구 선택지 노출(패턴 시도는 계속 가능).
        if (failCount >= 5) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { showResetConfirm = true }) {
                Text("패턴을 잊으셨나요? 라이브러리 초기화 후 해제", color = DotActive)
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("패턴 초기화") },
            text = {
                Text("라이브러리(폴더·평점·태그 등)를 모두 삭제하고 잠금을 해제합니다. 영상 파일 자체는 지워지지 않습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { Container.libraryRepo.clearLibrary() }
                        runCatching { store.clear() }
                        runCatching { Container.prefs.setAppLockEnabled(false) }
                        showResetConfirm = false
                        onUnlocked()
                    }
                }) { Text("초기화하고 해제", color = DotError) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            },
        )
    }
}

@Composable
fun PatternLock(
    size: Dp,
    errorFlash: Boolean = false,
    resetTrigger: Int = 0,
    onComplete: (List<Int>) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sizePx = with(density) { size.toPx() }
    val cell = sizePx / 3f
    val dotRadius = cell * 0.10f
    val hitRadius = cell * 0.32f

    val selected = remember { mutableStateListOf<Int>() }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(resetTrigger) {
        selected.clear()
        dragPos = null
    }

    fun pointPos(idx: Int): Offset {
        val row = idx / 3
        val col = idx % 3
        return Offset(cell * (col + 0.5f), cell * (row + 0.5f))
    }
    fun hit(offset: Offset): Int? {
        for (i in 0..8) {
            val c = pointPos(i)
            if (hypot((c.x - offset.x).toDouble(), (c.y - offset.y).toDouble()) <= hitRadius) {
                return i
            }
        }
        return null
    }
    // 두 점을 잇는 직선이 격자 위 점을 정확히 가로지르면 그 중간 점 인덱스, 아니면 null.
    fun midpoint(a: Int, b: Int): Int? {
        val ra = a / 3; val ca = a % 3
        val rb = b / 3; val cb = b % 3
        if ((ra + rb) % 2 != 0 || (ca + cb) % 2 != 0) return null
        val mid = ((ra + rb) / 2) * 3 + (ca + cb) / 2
        return if (mid != a && mid != b) mid else null
    }

    Box(
        modifier = Modifier
            .size(size)
            .clipToBounds()
            .pointerInput(resetTrigger) {
                detectDragGestures(
                    onDragStart = { off ->
                        selected.clear()
                        hit(off)?.let {
                            selected.add(it)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        dragPos = off
                    },
                    onDrag = { change, _ ->
                        dragPos = change.position
                        hit(change.position)?.let { idx ->
                            if (idx !in selected) {
                                // 직전 점과의 직선이 가로지르는 중간 점을 먼저 자동 포함(표준 패턴 동작)
                                if (selected.isNotEmpty()) {
                                    midpoint(selected.last(), idx)?.let { mid ->
                                        if (mid !in selected) {
                                            selected.add(mid)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                                selected.add(idx)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        val finalSel = selected.toList()
                        dragPos = null
                        if (finalSel.isNotEmpty()) {
                            onComplete(finalSel)
                        }
                    },
                    onDragCancel = {
                        dragPos = null
                        selected.clear()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mainColor = if (errorFlash) DotError else DotActive
            for (i in 0 until selected.size - 1) {
                val a = pointPos(selected[i])
                val b = pointPos(selected[i + 1])
                drawLine(mainColor.copy(alpha = 0.85f), a, b, strokeWidth = 10f)
            }
            dragPos?.let { dp ->
                if (selected.isNotEmpty()) {
                    val last = pointPos(selected.last())
                    drawLine(mainColor.copy(alpha = 0.5f), last, dp, strokeWidth = 8f)
                }
            }
            for (i in 0..8) {
                val isActive = i in selected
                val color = when {
                    errorFlash -> DotError
                    isActive -> DotActive
                    else -> DotIdle
                }
                drawCircle(color, radius = dotRadius * if (isActive) 1.3f else 1f, center = pointPos(i))
                if (isActive) {
                    drawCircle(
                        color = color.copy(alpha = 0.35f),
                        radius = dotRadius * 2.4f,
                        center = pointPos(i),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}
