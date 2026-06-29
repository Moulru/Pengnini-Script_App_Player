package com.pengnini.app.ui.library

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pengnini.app.R
import com.pengnini.app.data.db.FolderEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    current: LibraryFilter,
    allTags: List<String>,
    folders: List<FolderEntity>,
    onApply: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(current) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    label = stringResource(R.string.filter_only_with_script),
                    checked = draft.onlyWithScript,
                    onCheckedChange = { draft = draft.copy(onlyWithScript = it) },
                )
                ToggleRow(
                    label = stringResource(R.string.filter_only_favorite),
                    checked = draft.onlyFavorite,
                    onCheckedChange = { draft = draft.copy(onlyFavorite = it) },
                )
            }

            // Rating
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.filter_min_rating, draft.minRating),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row {
                    (0..5).forEach { i ->
                        IconButton(onClick = { draft = draft.copy(minRating = i) }) {
                            Icon(
                                imageVector = if (i in 1..draft.minRating) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(if (i == 0) 18.dp else 26.dp),
                            )
                        }
                    }
                }
            }

            // Folders
            if (folders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.filter_folders), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        folders.forEach { f ->
                            val name = f.displayName ?: shortFolderName(f.uri)
                            val selected = f.uri in draft.selectedFolders
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val newSet = draft.selectedFolders.toMutableSet()
                                    if (selected) newSet.remove(f.uri) else newSet.add(f.uri)
                                    draft = draft.copy(selectedFolders = newSet)
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }

            // Tags
            if (allTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        allTags.forEach { tag ->
                            val selected = draft.selectedTags.any { it.equals(tag, ignoreCase = true) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val newSet = draft.selectedTags.toMutableSet()
                                    if (selected) newSet.removeAll { it.equals(tag, ignoreCase = true) } else newSet.add(tag)
                                    draft = draft.copy(selectedTags = newSet)
                                },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    draft = LibraryFilter()
                    onApply(LibraryFilter())
                }) { Text(stringResource(R.string.filter_reset)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.action_apply)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SortMenu(
    expanded: Boolean,
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(mode.labelRes),
                        fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}

private fun shortFolderName(uri: String): String {
    val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
    return decoded.substringAfterLast(':').substringAfterLast('/').ifBlank { decoded.takeLast(20) }
}
