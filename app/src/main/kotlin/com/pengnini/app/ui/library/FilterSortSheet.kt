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
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pengnini.app.R
import com.pengnini.app.data.library.FolderBucket

/**
 * 정렬과 필터를 한 시트에서 처리한다. 둘 다 "목록 좁히기"라 따로 둘 이유가 없다.
 * 두 탭은 서로 섞이지 않는다 — 정렬 탭에는 정렬만, 필터 탭에는 필터만.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(
    currentFilter: LibraryFilter,
    currentSort: SortMode,
    allTags: List<String>,
    buckets: List<FolderBucket>,
    onApply: (LibraryFilter, SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(currentFilter) }
    var sortField by remember { mutableStateOf(currentSort.field) }
    var descending by remember { mutableStateOf(currentSort.descending) }
    var tab by remember { mutableIntStateOf(0) }

    val activeFilters = countActiveFilters(draft)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.sort_tab)) }
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(
                        if (activeFilters > 0) {
                            stringResource(R.string.filter_tab_count, activeFilters)
                        } else {
                            stringResource(R.string.filter_tab)
                        },
                    )
                }
            }

            if (tab == 0) {
                SortTab(
                    field = sortField,
                    descending = descending,
                    onFieldChange = { sortField = it },
                    onDirectionChange = { descending = it },
                )
            } else {
                FilterTab(
                    draft = draft,
                    allTags = allTags,
                    buckets = buckets,
                    onChange = { draft = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    draft = LibraryFilter()
                    sortField = SortField.ADDED
                    descending = true
                }) { Text(stringResource(R.string.filter_reset)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    onApply(draft, SortMode.of(sortField, descending))
                }) { Text(stringResource(R.string.action_apply)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SortTab(
    field: SortField,
    descending: Boolean,
    onFieldChange: (SortField) -> Unit,
    onDirectionChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sort_field_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SortField.entries.forEach { f ->
                FilterChip(
                    selected = f == field,
                    onClick = { onFieldChange(f) },
                    label = { Text(stringResource(f.labelRes)) },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sort_direction_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            // 화살표는 icon 슬롯에 넣는다. label은 단일 배치 슬롯이라
            // 아이콘·텍스트를 함께 넣으면 Row가 아니라 겹쳐서 그려진다.
            SegmentedButton(
                selected = descending,
                onClick = { onDirectionChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = {
                    Icon(Icons.Outlined.ArrowDownward, null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.sort_direction_desc)) },
            )
            SegmentedButton(
                selected = !descending,
                onClick = { onDirectionChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = {
                    Icon(Icons.Outlined.ArrowUpward, null, modifier = Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.sort_direction_asc)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTab(
    draft: LibraryFilter,
    allTags: List<String>,
    buckets: List<FolderBucket>,
    onChange: (LibraryFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(
            label = stringResource(R.string.filter_only_with_script),
            checked = draft.onlyWithScript,
            onCheckedChange = { onChange(draft.copy(onlyWithScript = it)) },
        )
        ToggleRow(
            label = stringResource(R.string.filter_only_favorite),
            checked = draft.onlyFavorite,
            onCheckedChange = { onChange(draft.copy(onlyFavorite = it)) },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.filter_min_rating, draft.minRating),
            style = MaterialTheme.typography.labelLarge,
        )
        Row {
            (0..5).forEach { i ->
                IconButton(onClick = { onChange(draft.copy(minRating = i)) }) {
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

    if (buckets.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.filter_folders), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                buckets.forEach { b ->
                    val selected = b.key in draft.selectedFolders
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = draft.selectedFolders.toMutableSet()
                            if (selected) next.remove(b.key) else next.add(b.key)
                            onChange(draft.copy(selectedFolders = next))
                        },
                        label = { Text("${b.label} ${b.count}") },
                    )
                }
            }
        }
    }

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
                            val next = draft.selectedTags.toMutableSet()
                            if (selected) next.removeAll { it.equals(tag, ignoreCase = true) } else next.add(tag)
                            onChange(draft.copy(selectedTags = next))
                        },
                        label = { Text("#$tag") },
                    )
                }
            }
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

/** 걸려 있는 필터 개수. 상단 배지와 시트 탭 라벨이 같은 값을 쓴다. */
fun countActiveFilters(f: LibraryFilter): Int {
    var n = 0
    if (f.onlyWithScript) n++
    if (f.onlyFavorite) n++
    if (f.minRating > 0) n++
    if (f.selectedTags.isNotEmpty()) n++
    if (f.selectedFolders.isNotEmpty()) n++
    return n
}
