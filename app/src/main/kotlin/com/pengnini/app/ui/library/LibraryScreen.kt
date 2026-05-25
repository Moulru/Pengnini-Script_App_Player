package com.pengnini.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.library.LibraryViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit = {},
    onOpenPlayer: (String) -> Unit = {},
    vm: LibraryViewModel = viewModel(),
) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val initialLoaded by vm.initialLoaded.collectAsStateWithLifecycle()

    var searchMode by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var actionVideoUri by remember { mutableStateOf<String?>(null) }
    var tagEditUri by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val folderAddedMsg = stringResource(R.string.snackbar_folder_added)

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            vm.addFolder(uri)
            scope.launch {
                snackbarHost.showSnackbar(folderAddedMsg, duration = SnackbarDuration.Short)
            }
        }
    }

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
                        Text(
                            "Pengnini",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
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
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.library_search))
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.library_sort))
                        }
                        SortMenu(
                            expanded = sortMenuOpen,
                            current = sort,
                            onSelect = {
                                vm.setSortMode(it)
                                sortMenuOpen = false
                            },
                            onDismiss = { sortMenuOpen = false },
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) Badge { Text("$activeFilterCount") }
                        },
                    ) {
                        IconButton(onClick = { filterOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.library_filter))
                        }
                    }
                    IconButton(onClick = { vm.toggleViewMode() }) {
                        val (icon, label) = when (viewMode) {
                            LibraryViewMode.GRID -> Icons.Outlined.GridView to R.string.library_view_grid
                            LibraryViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList to R.string.library_view_list
                        }
                        Icon(icon, contentDescription = stringResource(label))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (folders.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { folderDialogOpen = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = {
                if (folders.isNotEmpty()) vm.rescanAll()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !initialLoaded -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                folders.isEmpty() && videos.isEmpty() -> EmptyLibrary(
                    modifier = Modifier.fillMaxSize(),
                    onAdd = { folderDialogOpen = true },
                )
                videos.isEmpty() -> EmptyAfterFolder(modifier = Modifier.fillMaxSize())
                viewMode == LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(videos, key = { it.uri }) { v ->
                        VideoGridCard(
                            video = v,
                            onClick = { onOpenPlayer(v.uri) },
                            onLongClick = { actionVideoUri = v.uri },
                            onFavoriteToggle = { vm.setFavorite(v.uri, !v.favorite) },
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(videos, key = { it.uri }) { v ->
                        VideoListRow(
                            video = v,
                            onClick = { onOpenPlayer(v.uri) },
                            onLongClick = { actionVideoUri = v.uri },
                            onFavoriteToggle = { vm.setFavorite(v.uri, !v.favorite) },
                        )
                    }
                }
            }
        }
    }

    if (folderDialogOpen) {
        FolderManagerDialog(
            folders = folders,
            onAdd = { pickFolder.launch(null) },
            onRemove = { vm.removeFolder(it) },
            onDismiss = { folderDialogOpen = false },
        )
    }

    if (filterOpen) {
        FilterSheet(
            current = filter,
            allTags = allTags,
            folders = folders,
            onApply = {
                vm.setFilter(it)
                filterOpen = false
            },
            onDismiss = { filterOpen = false },
        )
    }

    actionVideoUri?.let { uri ->
        val video = videos.firstOrNull { it.uri == uri }
        if (video != null) {
            VideoActionSheet(
                video = video,
                onDismiss = { actionVideoUri = null },
                onSetRating = { vm.setRating(uri, it) },
                onSetFavorite = { vm.setFavorite(uri, it) },
                onEditTags = {
                    tagEditUri = uri
                    actionVideoUri = null
                },
            )
        }
    }

    tagEditUri?.let { uri ->
        val video = videos.firstOrNull { it.uri == uri }
        if (video != null) {
            TagEditorDialog(
                initialTags = video.tags.split(',').map { it.trim() }.filter { it.isNotBlank() },
                existingTags = allTags,
                onDismiss = { tagEditUri = null },
                onConfirm = {
                    vm.setTags(uri, it)
                    tagEditUri = null
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
) {
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
                stringResource(R.string.library_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_add_folder))
            }
        }
    }
}

@Composable
private fun EmptyAfterFolder(modifier: Modifier = Modifier) {
    // PullToRefreshBox 안에서 NestedScroll 인식되도록 LazyColumn 1 item 사용
    LazyColumn(modifier = modifier) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.library_no_videos_in_folder),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.library_pull_to_refresh),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderManagerDialog(
    folders: List<FolderEntity>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        stringResource(R.string.folder_dialog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    folders.forEach { f ->
                        FolderRow(folder = f, onRemove = { onRemove(f.uri) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_dialog_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_dialog_close))
            }
        },
    )
}

@Composable
private fun FolderRow(folder: FolderEntity, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = folder.displayName ?: shortPath(folder.uri),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = true),
            maxLines = 2,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = null)
        }
    }
}

private fun shortPath(uri: String): String {
    val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
    return decoded.substringAfterLast(':').ifBlank { decoded.takeLast(40) }
}

private fun countActiveFilters(f: LibraryFilter): Int {
    var n = 0
    if (f.onlyWithScript) n++
    if (f.onlyFavorite) n++
    if (f.minRating > 0) n++
    if (f.selectedTags.isNotEmpty()) n++
    if (f.selectedFolders.isNotEmpty()) n++
    return n
}
