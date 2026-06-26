package com.pengnini.app.ui.splash

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
        delay(1500)
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
        visible = false
        delay(300)
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
