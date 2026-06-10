package com.pengnini.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.displayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onSetRating: (Int) -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onEditTags: () -> Unit,
    onRename: () -> Unit,
    onLinkScript: () -> Unit,
    onUnlinkScript: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                video.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )

            // 별점
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.action_rating), style = MaterialTheme.typography.labelLarge)
                Row {
                    (1..5).forEach { i ->
                        IconButton(onClick = {
                            onSetRating(if (video.rating == i) 0 else i)
                        }) {
                            Icon(
                                imageVector = if (i <= video.rating) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            // 즐겨찾기
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { onSetFavorite(!video.favorite) },
                    label = {
                        Text(
                            if (video.favorite) stringResource(R.string.action_unfavorite)
                            else stringResource(R.string.action_favorite),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (video.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onEditTags,
                    label = { Text(stringResource(R.string.action_edit_tags)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                )
            }

            // 메타데이터
            video.tags.takeIf { it.isNotBlank() }?.let { tags ->
                Text(
                    text = tags.split(',').joinToString(" · ") { "#${it.trim()}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            ActionRow(Icons.Outlined.Edit, stringResource(R.string.action_rename), onClick = onRename)
            ActionRow(Icons.Outlined.Link, stringResource(R.string.action_link_script), onClick = onLinkScript)
            if (video.funscriptUri != null) {
                ActionRow(
                    Icons.Outlined.LinkOff,
                    stringResource(R.string.action_unlink_script),
                    onClick = onUnlinkScript,
                )
            }
            ActionRow(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    initialTags: List<String>,
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(initialTags.joinToString(", ")) }
    val current = remember(input) {
        input.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit_tags)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.tag_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.tag_dialog_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default,
                )
                if (existingTags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.tag_dialog_existing), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        existingTags.take(20).forEach { tag ->
                            val selected = current.any { it.equals(tag, ignoreCase = true) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val parts = input.split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                    if (selected) parts.removeAll { it.equals(tag, ignoreCase = true) }
                                    else parts.add(tag)
                                    input = parts.joinToString(", ")
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
