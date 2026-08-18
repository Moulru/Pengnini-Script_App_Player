package com.pengnini.app.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.displayTitle
import kotlinx.coroutines.launch

/**
 * 영상 하나에 가할 수 있는 조작. 홈과 폴더 화면이 같은 액션 시트를 쓰기 위한 공통 계약.
 * 구현은 각 화면의 ViewModel이 한다.
 */
interface VideoActions {
    fun setRating(uri: String, r: Int)
    fun setFavorite(uri: String, fav: Boolean)
    fun setTags(uri: String, tags: List<String>)
    fun setCustomTitle(uri: String, title: String?)
    fun setFunscript(uri: String, scriptUri: String?)
    fun deleteVideo(uri: String, onResult: (Boolean) -> Unit)
}

/**
 * 액션 시트와 거기서 파생되는 다이얼로그(태그·이름 변경·삭제·스크립트 연결)를 한 곳에서 처리한다.
 * 홈(격자)과 폴더(행 목록)가 동일하게 동작하도록 이 컴포저블을 공유한다.
 *
 * @param target 조작 대상. null이면 시트를 닫는다.
 */
@Composable
fun VideoActionsHost(
    target: VideoEntity?,
    allTags: List<String>,
    actions: VideoActions,
    snackbarHost: SnackbarHostState,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
) {
    // 시트가 닫힌 뒤에도 다이얼로그는 살아 있어야 해서 대상 스냅샷을 따로 들고 있는다.
    var tagEditTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var renameTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoEntity?>(null) }
    var linkTargetUri by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scriptLinkedMsg = stringResource(R.string.snackbar_script_linked)
    val scriptUnlinkedMsg = stringResource(R.string.snackbar_script_unlinked)
    val deletedMsg = stringResource(R.string.snackbar_deleted)
    val deleteFailedMsg = stringResource(R.string.snackbar_delete_failed)

    val pickScript = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val t = linkTargetUri
        if (uri != null && t != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            actions.setFunscript(t, uri.toString())
            scope.launch {
                snackbarHost.showSnackbar(scriptLinkedMsg, duration = SnackbarDuration.Short)
            }
        }
        linkTargetUri = null
    }

    if (target != null) {
        VideoActionSheet(
            video = target,
            onDismiss = onDismiss,
            onPlay = {
                val uri = target.uri
                onDismiss()
                onPlay(uri)
            },
            onSetRating = { actions.setRating(target.uri, it) },
            onSetFavorite = { actions.setFavorite(target.uri, it) },
            onEditTags = {
                tagEditTarget = target
                onDismiss()
            },
            onRename = {
                renameTarget = target
                onDismiss()
            },
            onLinkScript = {
                linkTargetUri = target.uri
                onDismiss()
                pickScript.launch(arrayOf("*/*"))
            },
            onUnlinkScript = {
                actions.setFunscript(target.uri, null)
                onDismiss()
                scope.launch {
                    snackbarHost.showSnackbar(scriptUnlinkedMsg, duration = SnackbarDuration.Short)
                }
            },
            onDelete = {
                deleteTarget = target
                onDismiss()
            },
        )
    }

    tagEditTarget?.let { video ->
        TagEditorDialog(
            initialTags = video.tags.split(',').map { it.trim() }.filter { it.isNotBlank() },
            existingTags = allTags,
            onDismiss = { tagEditTarget = null },
            onConfirm = {
                actions.setTags(video.uri, it)
                tagEditTarget = null
            },
        )
    }

    renameTarget?.let { video ->
        RenameDialog(
            initial = video.displayTitle,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                actions.setCustomTitle(video.uri, newName)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { video ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteVideo(video.uri) { ok ->
                        scope.launch {
                            snackbarHost.showSnackbar(
                                if (ok) deletedMsg else deleteFailedMsg,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun RenameDialog(
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
