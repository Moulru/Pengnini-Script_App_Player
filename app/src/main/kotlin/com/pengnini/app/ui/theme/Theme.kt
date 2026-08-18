package com.pengnini.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * 색 역할을 좁게 쓴다.
 * - 보라(primary): 선택 상태와 재생 진행에만. 로고·배지·아이콘에 두루 쓰면 강조 기능을 잃는다.
 * - 청록(secondary): 스크립트·네트워크 의미 전용. 이 색이 보이면 "스크립트 관련"으로 읽히게.
 * - 나머지는 전부 무채색 단계로 처리.
 */
private val PurplePrimary = Color(0xFF9C7CFF)
private val PurpleOnPrimary = Color(0xFF1B1033)
private val PurpleContainer = Color(0xFF3F2D7A)
private val CyanSecondary = Color(0xFF5EDFC1)
private val CyanOnSecondary = Color(0xFF003830)

// 배경을 한 단계 더 내려 표면과 대비를 벌린다 → 테두리 없이도 카드가 떠 보인다.
private val DarkBg = Color(0xFF0A0A0E)
private val DarkSurface = Color(0xFF101016)
private val DarkSurfaceVariant = Color(0xFF15151C)

// 1차 텍스트는 더 밝게, 2차는 더 어둡게 — 두 단계가 확실히 갈리도록.
private val OnSurface = Color(0xFFF2F0F6)
private val OnSurfaceVariant = Color(0xFF6E6A7A)

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    background = DarkBg,
    onBackground = OnSurface,
    surface = DarkSurface,
    onSurface = OnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Color(0xFF2A2833),
    outlineVariant = Color(0xFF1C1B24),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4DD2),
    onPrimary = Color.White,
    secondary = Color(0xFF1DA98D),
    onSecondary = Color.White,
)

@Composable
fun PengniniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
