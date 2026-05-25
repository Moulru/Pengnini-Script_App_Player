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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlin.math.hypot

private val ScreenBg = Color(0xFF0F0F14)
private val DotIdle = Color(0xFF3F2D7A)
private val DotActive = Color(0xFF9C7CFF)
private val DotError = Color(0xFFE0506C)

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val store = remember { Container.lockStore }
    var error by remember { mutableStateOf(false) }
    var attemptVersion by remember { mutableStateOf(0) }

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
                }
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
