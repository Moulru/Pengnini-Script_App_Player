package com.pengnini.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pengnini.app.ui.theme.CardMetrics
import com.pengnini.app.ui.theme.Radius
import com.pengnini.app.ui.theme.Space

/**
 * 로딩 중 자리 표시. 같은 시간이라도 자리가 잡혀 있으면 "빠르다"고 느낀다 —
 * 실제 속도를 바꾸지 않고 체감만 바꾸는 가장 싼 방법이다.
 * 정지된 회색 상자는 고장으로 보이므로 천천히 흐르는 하이라이트를 준다.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = Color(0xFF15151C)
    val highlight = Color(0xFF1E1E28)
    return background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(progress * 600f - 300f, 0f),
            end = Offset(progress * 600f + 300f, 0f),
        ),
    )
}

@Composable
private fun SkeletonBox(modifier: Modifier) {
    Box(modifier = modifier.shimmer())
}

/** 라이브러리 격자 자리 표시. 실제 카드와 같은 비율·간격을 쓴다. */
@Composable
fun VideoGridSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        modifier = modifier,
        contentPadding = PaddingValues(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        userScrollEnabled = false,
    ) {
        items(count = itemCount) { index ->
            Column {
                SkeletonBox(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CardMetrics.THUMB_ASPECT)
                        .clip(RoundedCornerShape(Radius.card)),
                )
                Box(Modifier.height(Space.sm))
                SkeletonBox(
                    Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.78f else 0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(Radius.small)),
                )
                Box(Modifier.height(Space.xs))
                SkeletonBox(
                    Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.45f else 0.36f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(Radius.small)),
                )
            }
        }
    }
}
