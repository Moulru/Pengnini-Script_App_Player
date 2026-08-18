package com.pengnini.app.ui.splash

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pengnini.app.Container
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 스플래시는 고정 시간이 아니라 준비가 끝나는 대로 넘어간다.
 * 다만 아래위로 경계를 둔다 — 너무 짧으면 로고가 깜빡이는 결함처럼 보이고,
 * 너무 길면(라이브러리가 크거나 기기가 느릴 때) 기다림이 된다.
 */
private const val MIN_SPLASH_MS = 700L
private const val MAX_SPLASH_MS = 2500L

/** 로고 페이드아웃 길이. */
private const val FADE_OUT_MS = 300L

private fun elapsedSince(startMs: Long): Long = SystemClock.elapsedRealtime() - startMs

private val SplashBg = Color(0xFF0F0F14)
private val LogoBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF9C7CFF), Color(0xFF5EDFC1)),
)

@Composable
fun SplashScreen(
    libraryRoute: String,
    lockRoute: String,
    onNavigate: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val startedAt = SystemClock.elapsedRealtime()
        // 로고를 띄우는 동안 Room DB를 열고 라이브러리 첫 조회를 끝내 둔다.
        // (DB 열기 + 마이그레이션 확인 + 첫 쿼리를 Library 진입 후가 아니라 여기서 치른다)
        val preload = launch {
            runCatching { Container.libraryRepo.videos.first() }
        }
        // 패턴 판정 로직 변경(중간점 자동 포함)으로 이전 패턴이 안 맞을 수 있어 1회 초기화 후 잠금 해제.
        // 실패 시 done=true로 간주해 반복 초기화를 막는다.
        if (!runCatching { Container.prefs.patternResetDone.first() }.getOrDefault(true)) {
            runCatching { Container.lockStore.clear() }
            runCatching { Container.prefs.setAppLockEnabled(false) }
            runCatching { Container.prefs.setPatternResetDone(true) }
        }
        val enabled = runCatching { Container.prefs.appLockEnabled.first() }.getOrDefault(false)
        val hasPattern = runCatching { Container.lockStore.hasPattern() }.getOrDefault(false)
        val next = if (enabled && hasPattern) lockRoute else libraryRoute

        // 준비가 끝나는 대로 넘어간다. 단 상한을 두어 느린 기기에서 붙잡히지 않게 한다.
        val budget = (MAX_SPLASH_MS - elapsedSince(startedAt)).coerceAtLeast(0L)
        withTimeoutOrNull(budget) { preload.join() }
        // 최소 노출 시간이 남았으면 그만큼만 더 보여준다(로고가 깜빡이는 것처럼 보이지 않게).
        val remaining = MIN_SPLASH_MS - elapsedSince(startedAt)
        if (remaining > 0) delay(remaining)

        visible = false
        delay(FADE_OUT_MS)
        onNavigate(next)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pengnini",
                    style = TextStyle(
                        brush = LogoBrush,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Script App Player",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
