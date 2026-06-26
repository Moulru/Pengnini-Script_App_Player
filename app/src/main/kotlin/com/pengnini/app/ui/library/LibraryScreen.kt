package com.pengnini.app.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pengnini.app.data.smb.SmbManager
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pengnini.app.R
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.displayTitle
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
    var renameUri by remember { mutableStateOf<String?>(null) }
    var deleteUri by remember { mutableStateOf<String?>(null) }
    var linkTargetUri by remember { mutableStateOf<String?>(null) }
    var smbDialogOpen by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val folderAddedMsg = stringResource(R.string.snackbar_folder_added)
    val scriptLinkedMsg = stringResource(R.string.snackbar_script_linked)
    val scriptUnlinkedMsg = stringResource(R.string.snackbar_script_unlinked)
    val deletedMsg = stringResource(R.string.snackbar_deleted)
    val deleteFailedMsg = stringResource(R.string.snackbar_delete_failed)

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

    val pickScript = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = linkTargetUri
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setFunscript(target, uri.toString())
            scope.launch {
                snackbarHost.showSnackbar(scriptLinkedMsg, duration = SnackbarDuration.Short)
            }
        }
        linkTargetUri = null
    }

    // 검색 모드 진입 시 자동 포커스 + 키보드 표시
    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val activeFilterCount = countActiveFilters(filter)
    // 탭0 = 로컬, 탭1 = 네트워크(SMB). 현재 탭에 해당하는 영상/폴더만 보여준다.
    val isNetworkTab = selectedTab == 1
    val displayedVideos = remember(videos, isNetworkTab) {
        videos.filter { SmbManager.isSmb(it.uri) == isNetworkTab }
    }
    val hasFoldersForTab = folders.any { SmbManager.isSmb(it.uri) == isNetworkTab }

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_local)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_network)) },
                )
            }
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = {
                    if (folders.isNotEmpty()) vm.rescanAll()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                !initialLoaded -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                displayedVideos.isEmpty() && isNetworkTab ->
                    EmptyNetworkTab(modifier = Modifier.fillMaxSize(), onAdd = { smbDialogOpen = true })
                displayedVideos.isEmpty() && !hasFoldersForTab ->
                    EmptyLibrary(modifier = Modifier.fillMaxSize(), onAdd = { folderDialogOpen = true })
                displayedVideos.isEmpty() -> EmptyAfterFolder(modifier = Modifier.fillMaxSize())
                viewMode == LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayedVideos, key = { it.uri }) { v ->
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
                    items(displayedVideos, key = { it.uri }) { v ->
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
    }

    if (folderDialogOpen) {
        FolderManagerDialog(
            isNetwork = selectedTab == 1,
            folders = folders.filter { SmbManager.isSmb(it.uri) == (selectedTab == 1) },
            onAddLocal = {
                folderDialogOpen = false
                pickFolder.launch(null)
            },
            onAddSmb = {
                folderDialogOpen = false
                smbDialogOpen = true
            },
            onRemove = { vm.removeFolder(it) },
            onDismiss = { folderDialogOpen = false },
        )
    }

    if (smbDialogOpen) {
        SmbFolderAddDialog(
            onDismiss = { smbDialogOpen = false },
            onAdd = { host, share, path, user, pass ->
                smbDialogOpen = false
                vm.addSmbFolder(host, share, path, user, pass) { count ->
                    scope.launch {
                        snackbarHost.showSnackbar(
                            if (count > 0) context.getString(R.string.smb_added, count)
                            else context.getString(R.string.smb_empty),
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
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
                onRename = {
                    renameUri = uri
                    actionVideoUri = null
                },
                onLinkScript = {
                    linkTargetUri = uri
                    actionVideoUri = null
                    pickScript.launch(arrayOf("*/*"))
                },
                onUnlinkScript = {
                    vm.setFunscript(uri, null)
                    actionVideoUri = null
                    scope.launch {
                        snackbarHost.showSnackbar(scriptUnlinkedMsg, duration = SnackbarDuration.Short)
                    }
                },
                onDelete = {
                    deleteUri = uri
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

    renameUri?.let { uri ->
        val video = videos.firstOrNull { it.uri == uri }
        if (video != null) {
            RenameDialog(
                initial = video.displayTitle,
                onDismiss = { renameUri = null },
                onConfirm = { newName ->
                    vm.setCustomTitle(uri, newName)
                    renameUri = null
                },
            )
        }
    }

    deleteUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { deleteUri = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVideo(uri) { ok ->
                        scope.launch {
                            snackbarHost.showSnackbar(
                                if (ok) deletedMsg else deleteFailedMsg,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                    deleteUri = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteUri = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.rename_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_dialog_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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
private fun EmptyNetworkTab(modifier: Modifier = Modifier, onAdd: () -> Unit) {
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
                    Icons.Outlined.Lan,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.network_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.network_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Lan, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_dialog_add_network))
                }
            }
        }
    }
}

@Composable
private fun FolderManagerDialog(
    folders: List<FolderEntity>,
    isNetwork: Boolean,
    onAddLocal: () -> Unit,
    onAddSmb: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 폴더 목록만 내부 스크롤 — 추가 버튼은 아래 고정(목록이 길어도 항상 보이게)
                Column(
                    modifier = Modifier
                        .heightIn(min = 60.dp, max = 340.dp)
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
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = if (isNetwork) onAddSmb else onAddLocal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (isNetwork) Icons.Outlined.Lan else Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (isNetwork) R.string.folder_dialog_add_network else R.string.folder_dialog_add,
                        ),
                    )
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
private fun SmbFolderAddDialog(
    onDismiss: () -> Unit,
    onAdd: (host: String, share: String, path: String, username: String, password: String) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smb_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.smb_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text(stringResource(R.string.smb_share)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.smb_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.smb_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.smb_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() && share.isNotBlank(),
                onClick = { onAdd(host.trim(), share.trim(), path.trim(), username.trim(), password) },
            ) { Text(stringResource(R.string.smb_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
