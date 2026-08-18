package com.pengnini.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.displayTitle
import com.pengnini.app.data.media.ThumbnailCache
import com.pengnini.app.ui.common.formatDuration
import com.pengnini.app.ui.common.formatFileSize
import com.pengnini.app.ui.common.formatResolution
import com.pengnini.app.ui.theme.CardMetrics
import com.pengnini.app.ui.theme.Radius
import com.pengnini.app.ui.theme.Space
import com.pengnini.app.ui.theme.TextSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 썸네일 아래를 덮어 흰 글자가 어떤 그림 위에서도 읽히게 하는 그라디언트. */
val ThumbScrim = Brush.verticalGradient(
    0.55f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.72f),
)

/**
 * 격자 카드.
 *
 * 높이는 **썸네일 + 제목 1줄 + 메타 1줄**로 항상 같다. 제목이 길든 짧든, 별점·즐겨찾기가
 * 있든 없든 변하지 않는다 — 조건부로 나타나는 줄을 두면 같은 행 안에서도 바닥선이 어긋난다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: VideoEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaInfo: (Long, Int, Int) -> Unit = { _, _, _ -> },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardMetrics.THUMB_ASPECT)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            VideoThumbnail(
                uri = video.uri,
                contentDescription = video.displayTitle,
                onMediaInfo = onMediaInfo,
                modifier = Modifier.fillMaxSize(),
                needsMediaInfo = video.durationMs <= 0,
            )
            Box(modifier = Modifier.fillMaxSize().background(ThumbScrim))

            if (video.funscriptUri != null) {
                // 글자 배지는 썸네일을 크게 가린다 — 같은 뜻을 아이콘 하나로.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Space.sm)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                fontSize = TextSize.caption,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(CardMetrics.moreTouch),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            text = video.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = TextSize.cardTitle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        VideoMetaRow(video)
    }
}

/**
 * 메타 한 줄. 순서는 내용과 무관하게 **별점 → 즐겨찾기 → 해상도·용량**으로 고정한다.
 * 카드마다 순서가 바뀌면 눈이 매번 다시 찾아야 한다.
 * 넘칠 때는 아이콘을 줄이지 않고 뒤쪽 텍스트만 말줄임 — 별·하트는 한눈에 읽는 정보다.
 */
@Composable
fun VideoMetaRow(video: VideoEntity) {
    // 별점이 있으면 용량을 빼고 해상도만 — 아이콘과 합쳐도 넘치지 않게.
    val tech = if (video.rating > 0) {
        listOfNotNull(formatResolution(video.width, video.height))
    } else {
        listOfNotNull(
            formatResolution(video.width, video.height),
            formatFileSize(video.sizeBytes),
        )
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp), // 내용이 없어도 이 줄은 자리를 지킨다
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(video.rating) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(CardMetrics.metaIcon),
            )
        }
        if (video.favorite) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(CardMetrics.metaIcon),
            )
        }
        if (tech.isNotBlank()) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = tech,
                style = MaterialTheme.typography.labelSmall,
                fontSize = TextSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 목록(행) 형태. 격자와 같은 정보를 가로로 편다. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoListRow(
    video: VideoEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaInfo: (Long, Int, Int) -> Unit = { _, _, _ -> },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Space.sm, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(CardMetrics.THUMB_ASPECT)
                .clip(RoundedCornerShape(Radius.small))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            VideoThumbnail(
                uri = video.uri,
                contentDescription = video.displayTitle,
                onMediaInfo = onMediaInfo,
                modifier = Modifier.fillMaxSize(),
                needsMediaInfo = video.durationMs <= 0,
            )
            Box(modifier = Modifier.fillMaxSize().background(ThumbScrim))
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                fontSize = TextSize.caption,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = Space.xs, vertical = 2.dp),
            )
            if (video.funscriptUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Space.xs)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = TextSize.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            VideoMetaRow(video)
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 영상 썸네일. 1/3 지점 프레임을 cacheDir에 1회 생성·재사용(로컬·SMB 공통).
 * 준비 전엔 빈 상태(부모 배경이 플레이스홀더 역할).
 * 새로 만들 때는 길이·해상도도 함께 얻어지므로 [onMediaInfo]로 올려보낸다.
 */
@Composable
fun VideoThumbnail(
    uri: String,
    contentDescription: String?,
    onMediaInfo: (Long, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    needsMediaInfo: Boolean = false,
) {
    val context = LocalContext.current
    val reportMediaInfo by rememberUpdatedState(onMediaInfo)
    val wantMedia by rememberUpdatedState(needsMediaInfo)
    val thumb by produceState<File?>(null, uri) {
        val result = withContext(Dispatchers.IO) {
            runCatching { ThumbnailCache.get(context, uri, wantMedia) }.getOrNull()
        }
        result?.media?.let { reportMediaInfo(it.durationMs, it.width, it.height) }
        value = result?.file
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(thumb).crossfade(true).build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
