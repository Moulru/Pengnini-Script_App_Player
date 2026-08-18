package com.pengnini.app.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.displayTitle
import com.pengnini.app.ui.common.formatDuration
import com.pengnini.app.ui.library.ThumbScrim
import com.pengnini.app.ui.library.VideoActionsHost
import com.pengnini.app.ui.library.VideoMetaRow
import com.pengnini.app.ui.library.VideoThumbnail
import com.pengnini.app.ui.theme.CardMetrics
import com.pengnini.app.ui.theme.Radius
import com.pengnini.app.ui.theme.Space
import com.pengnini.app.ui.theme.TextSize

/**
 * 폴더 하나에 든 영상 목록. 격자가 아닌 행 구조로, 한 줄에 길이·해상도·용량·스크립트 유무를 담는다.
 * 하위 폴더로 더 파고들지 않는다 — 폴더 목록이 이미 모든 폴더를 평면으로 보여주기 때문.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideosScreen(
    onBack: () -> Unit = {},
    onOpenPlayer: (String) -> Unit = {},
    vm: FolderVideosViewModel = viewModel(),
) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val label by vm.label.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()

    var actionTarget by remember { mutableStateOf<VideoEntity?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { FolderPathTitle(label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.folder_video_count, videos.size),
                fontSize = TextSize.support,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.lg, bottom = Space.sm),
            )
            if (videos.isEmpty()) {
                EmptyFolderVideos(modifier = Modifier.fillMaxSize())
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(videos, key = { it.uri }) { v ->
                    VideoFileRow(
                        video = v,
                        onClick = { onOpenPlayer(v.uri) },
                        onLongClick = { actionTarget = v },
                        onMoreClick = { actionTarget = v },
                        onMediaInfo = { d, w, h -> vm.saveMediaInfo(v.uri, d, w, h) },
                    )
                }
            }
            }
        }
    }

    // 홈과 같은 액션 시트 — 진입 지점만 다르고 동작은 동일하다.
    val liveTarget = actionTarget?.let { t -> videos.firstOrNull { it.uri == t.uri } ?: t }
    VideoActionsHost(
        target = liveTarget,
        allTags = allTags,
        actions = vm,
        snackbarHost = snackbarHost,
        onDismiss = { actionTarget = null },
        onPlay = onOpenPlayer,
    )
}

/** 폴더 안 영상이 모두 사라졌을 때(삭제·이동). 화면이 그냥 비어 보이지 않게 알려 준다. */
@Composable
private fun EmptyFolderVideos(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.folder_videos_empty),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.folder_videos_empty_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `Movies / 여행` — 앞쪽 경로는 흐리게, 현재 폴더만 진하게. */
@Composable
private fun FolderPathTitle(label: String) {
    val trimmed = label.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', "")
    val leaf = trimmed.substringAfterLast('/')
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (parent.isNotBlank()) {
            Text(
                text = parent + " / ",
                fontSize = TextSize.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = leaf.ifBlank { label },
            fontSize = TextSize.screenTitle,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 폴더 안 한 줄. 홈 목록보다 썸네일만 작고, 제목·메타 규칙은 [VideoMetaRow]로 공유한다 —
 * 같은 영상이 화면마다 다른 순서로 보이면 읽는 법을 두 번 익혀야 한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoFileRow(
    video: VideoEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaInfo: (Long, Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Space.md, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
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
