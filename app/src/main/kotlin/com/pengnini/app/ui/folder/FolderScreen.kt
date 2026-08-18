package com.pengnini.app.ui.folder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pengnini.app.R
import com.pengnini.app.data.library.FolderBucket
import com.pengnini.app.data.library.shortFolderName
import androidx.compose.foundation.clickable
import com.pengnini.app.ui.common.ScanProgressRow
import com.pengnini.app.ui.common.formatRelativeTime
import com.pengnini.app.ui.theme.Radius
import com.pengnini.app.ui.theme.Space
import com.pengnini.app.ui.theme.TextSize
import kotlinx.coroutines.launch

/**
 * 폴더 탭. 등록한 폴더가 아니라 **영상이 실제로 들어 있는 하위 폴더**를 한 줄씩 보여준다.
 * 추가·제거는 등록한 최상위 폴더 단위(SAF 권한이 거기 묶여 있음).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onOpenBucket: (String) -> Unit = {},
    vm: FolderViewModel = viewModel(),
) {
    val buckets by vm.buckets.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()

    var addDialogOpen by remember { mutableStateOf(false) }
    var smbDialogOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<String?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanFoundMsg = stringResource(R.string.snackbar_scan_found)
    val scanEmptyMsg = stringResource(R.string.snackbar_scan_empty)

    fun reportScan(count: Int) {
        scope.launch {
            snackbarHost.showSnackbar(
                if (count > 0) scanFoundMsg.format(count) else scanEmptyMsg,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.addFolder(uri) { reportScan(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_folders),
                        fontSize = TextSize.screenTitle,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                actions = {
                    if (roots.isNotEmpty()) {
                        // 스캔 중에는 어차피 무시되므로 눌리지 않게 해 반응이 없는 것처럼 보이지 않게 한다.
                        IconButton(
                            enabled = !isScanning,
                            onClick = { vm.rescanAll { reportScan(it) } },
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.folder_rescan_all),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addDialogOpen = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.folder_dialog_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isScanning) {
                ScanProgressRow(count = scanProgress, onCancel = { vm.cancelScan() })
            }
            if (buckets.isEmpty() && !isScanning) {
                EmptyFolders(
                    modifier = Modifier.fillMaxSize(),
                    hasRoots = roots.isNotEmpty(),
                    onAdd = { addDialogOpen = true },
                )
            } else {
                // 로컬과 네트워크는 성격이 달라 섞이면 훑기 어렵다. 두 덩어리로 나누고
                // 각 덩어리를 하나의 표면으로 묶으면 테두리를 반복하지 않아도 경계가 읽힌다.
                val groups = listOf(
                    R.string.tab_local to buckets.filter { !it.isNetwork },
                    R.string.tab_network to buckets.filter { it.isNetwork },
                ).filter { it.second.isNotEmpty() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.md,
                        end = Space.md,
                        top = Space.sm,
                        bottom = Space.lg,
                    ),
                ) {
                    groups.forEach { (titleRes, group) ->
                        item(key = "header-" + titleRes) {
                            Text(
                                text = stringResource(titleRes),
                                fontSize = TextSize.support,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = Space.xs,
                                    top = Space.md,
                                    bottom = Space.sm,
                                ),
                            )
                        }
                        itemsIndexed(group, key = { _, b -> b.key }) { index, bucket ->
                            BucketRow(
                                bucket = bucket,
                                scanning = isScanning,
                                first = index == 0,
                                last = index == group.lastIndex,
                                onClick = { onOpenBucket(bucket.key) },
                                onRescan = { vm.rescanRoot(bucket.rootUri) { reportScan(it) } },
                                onRemoveRoot = { removeTarget = bucket.rootUri },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addDialogOpen) {
        AddFolderDialog(
            onAddLocal = {
                addDialogOpen = false
                pickFolder.launch(null)
            },
            onAddNetwork = {
                addDialogOpen = false
                smbDialogOpen = true
            },
            onDismiss = { addDialogOpen = false },
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

    removeTarget?.let { rootUri ->
        val name = roots.firstOrNull { it.uri == rootUri }?.displayName ?: shortFolderName(rootUri)
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.folder_remove_title)) },
            text = { Text(stringResource(R.string.folder_remove_msg, name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFolder(rootUri)
                    removeTarget = null
                }) { Text(stringResource(R.string.folder_menu_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * 묶음 표면의 한 칸. 위아래 끝만 둥글게 깎아 여러 행이 하나의 판처럼 보이게 한다.
 * 로컬/네트워크 배지는 그룹 헤더가 대신하므로 행에서는 뺐다.
 */
@Composable
private fun BucketRow(
    bucket: FolderBucket,
    scanning: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onRescan: () -> Unit,
    onRemoveRoot: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(
        topStart = if (first) Radius.card else 0.dp,
        topEnd = if (first) Radius.card else 0.dp,
        bottomStart = if (last) Radius.card else 0.dp,
        bottomEnd = if (last) Radius.card else 0.dp,
    )
    val accent = if (bucket.isNetwork) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = Space.md, top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.small))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (bucket.isNetwork) Icons.Outlined.Lan else Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bucket.label,
                    fontSize = TextSize.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.folder_video_count, bucket.count) +
                        " · " + formatRelativeTime(bucket.lastScannedAt),
                    fontSize = TextSize.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_menu_rescan)) },
                        enabled = !scanning,
                        onClick = {
                            menuOpen = false
                            onRescan()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_menu_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemoveRoot()
                        },
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Space.sm),
            )
        }
        // 행 사이만 가르는 실선. 마지막 행 아래에 그으면 판이 잘려 보인다.
        if (!last) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun AddFolderDialog(
    onAddLocal: () -> Unit,
    onAddNetwork: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onAddLocal, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_dialog_add))
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAddNetwork, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Lan, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_dialog_add_network))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_dialog_close)) }
        },
    )
}

@Composable
private fun EmptyFolders(
    modifier: Modifier = Modifier,
    hasRoots: Boolean,
    onAdd: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(
                    if (hasRoots) R.string.folder_empty_scanned else R.string.folder_empty_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (hasRoots) R.string.folder_empty_scanned_sub else R.string.folder_empty_subtitle,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.folder_dialog_add))
            }
        }
    }
}
