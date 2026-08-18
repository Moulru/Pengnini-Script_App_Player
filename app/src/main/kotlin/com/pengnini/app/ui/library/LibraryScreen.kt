package com.pengnini.app.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.library.LibraryViewMode
import com.pengnini.app.ui.common.VideoGridSkeleton
import com.pengnini.app.ui.theme.Space
import com.pengnini.app.ui.theme.TextSize
import kotlinx.coroutines.delay

/**
 * 홈(라이브러리). 전체 영상을 훑는 화면 — 폴더별로 보는 건 폴더 탭이 맡는다.
 * 상단은 검색(돋보기 토글) · 보기 전환 · 필터·정렬 세 개만 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlayer: (String) -> Unit = {},
    vm: LibraryViewModel = viewModel(),
) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val folderBuckets by vm.folderBuckets.collectAsStateWithLifecycle()
    val hasFolders by vm.hasFolders.collectAsStateWithLifecycle()
    val initialLoaded by vm.initialLoaded.collectAsStateWithLifecycle()

    var searchMode by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<VideoEntity?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    // 검색 모드 진입 시 자동 포커스 + 키보드 표시
    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val activeFilterCount = countActiveFilters(filter)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { vm.setSearch(it) },
                            placeholder = { Text(stringResource(R.string.library_search_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    } else {
                        // 좌상단 표기는 도메인 규칙상 영문 고정. 보라색은 선택·진행 표시로 돌려주고
                        // 여기서는 크기와 굵기로만 존재감을 준다.
                        Text(
                            "Pengnini",
                            fontSize = TextSize.screenTitle,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                navigationIcon = {
                    if (searchMode) {
                        IconButton(onClick = {
                            searchMode = false
                            vm.setSearch("")
                        }) { Icon(Icons.Outlined.Close, contentDescription = null) }
                    }
                },
                actions = {
                    if (!searchMode) {
                        IconButton(onClick = { searchMode = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.library_search),
                            )
                        }
                    }
                    IconButton(onClick = { vm.toggleViewMode() }) {
                        val (icon, label) = when (viewMode) {
                            LibraryViewMode.GRID -> Icons.Outlined.GridView to R.string.library_view_grid
                            LibraryViewMode.LIST ->
                                Icons.AutoMirrored.Outlined.ViewList to R.string.library_view_list
                        }
                        Icon(icon, contentDescription = stringResource(label))
                    }
                    BadgedBox(
                        badge = { if (activeFilterCount > 0) Badge { Text("$activeFilterCount") } },
                    ) {
                        IconButton(onClick = { sheetOpen = true }) {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.library_filter_sort),
                                // 걸린 조건이 있을 때만 강조색 — 평소엔 다른 아이콘과 같은 무게
                                tint = if (activeFilterCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (initialLoaded && hasFolders && !searchMode) {
                Text(
                    text = stringResource(R.string.library_video_count, videos.size),
                    fontSize = TextSize.support,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.lg, bottom = Space.sm),
                )
            }
            if (initialLoaded && hasFolders) {
                QuickFilterRow(
                    filter = filter,
                    onAll = { vm.clearQuickFilters() },
                    onScript = { vm.toggleQuickScript() },
                    onFavorite = { vm.toggleQuickFavorite() },
                    onRating4 = { vm.toggleQuickRating4() },
                )
            }
            when {
                // 빈 배경 대신 자리 표시 — 실제 속도는 그대로지만 체감이 다르다.
                !initialLoaded -> VideoGridSkeleton(modifier = Modifier.fillMaxSize())
                videos.isEmpty() && !hasFolders ->
                    EmptyLibrary(modifier = Modifier.fillMaxSize())
                videos.isEmpty() ->
                    EmptyResult(modifier = Modifier.fillMaxSize())
                viewMode == LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    items(videos, key = { it.uri }) { v ->
                        VideoGridCard(
                            video = v,
                            onClick = { onOpenPlayer(v.uri) },
                            onLongClick = { actionTarget = v },
                            onMoreClick = { actionTarget = v },
                            onMediaInfo = { d, w, h -> vm.saveMediaInfo(v.uri, d, w, h) },
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    items(videos, key = { it.uri }) { v ->
                        VideoListRow(
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

    if (sheetOpen) {
        FilterSortSheet(
            currentFilter = filter,
            currentSort = sort,
            allTags = allTags,
            buckets = folderBuckets,
            onApply = { f, m ->
                vm.applySheet(f, m)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }

    // 별점·즐겨찾기를 시트에서 바꾸면 목록이 갱신되므로 uri로 최신 항목을 다시 찾는다.
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

/** 가장 자주 쓰는 조건만 시트를 열지 않고 한 번에. 나머지는 상단 필터 버튼으로. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFilterRow(
    filter: LibraryFilter,
    onAll: () -> Unit,
    onScript: () -> Unit,
    onFavorite: () -> Unit,
    onRating4: () -> Unit,
) {
    val noneActive = countActiveFilters(filter) == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        FilterChip(
            selected = noneActive,
            onClick = onAll,
            label = { Text(stringResource(R.string.quick_all)) },
        )
        FilterChip(
            selected = filter.onlyWithScript,
            onClick = onScript,
            leadingIcon = { Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringResource(R.string.quick_sync)) },
        )
        FilterChip(
            selected = filter.onlyFavorite,
            onClick = onFavorite,
            leadingIcon = { Icon(Icons.Outlined.Favorite, null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringResource(R.string.quick_favorite)) },
        )
        FilterChip(
            selected = filter.minRating >= 4,
            onClick = onRating4,
            leadingIcon = { Icon(Icons.Outlined.Star, null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringResource(R.string.quick_rating4)) },
        )
    }
}

/** 등록된 폴더가 아예 없을 때. 추가는 폴더 탭에서 한다. */
@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.library_empty_go_folders),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 폴더는 있는데 검색·필터 결과가 비었을 때. */
@Composable
private fun EmptyResult(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.library_no_result),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.library_no_result_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
