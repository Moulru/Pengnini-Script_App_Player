package com.pengnini.app.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pengnini.app.Container
import com.pengnini.app.R
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.library.LibraryViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val key: String, val labelRes: Int) {
    ADDED_DESC("added_desc", R.string.sort_added_desc),
    ADDED_ASC("added_asc", R.string.sort_added_asc),
    TITLE_ASC("title_asc", R.string.sort_title_asc),
    TITLE_DESC("title_desc", R.string.sort_title_desc),
    DURATION_DESC("duration_desc", R.string.sort_duration_desc),
    DURATION_ASC("duration_asc", R.string.sort_duration_asc),
    RATING_DESC("rating_desc", R.string.sort_rating_desc),
    RESOLUTION_DESC("resolution_desc", R.string.sort_resolution_desc),
    SIZE_DESC("size_desc", R.string.sort_size_desc),
    HAS_SCRIPT("has_script", R.string.sort_has_script);

    companion object {
        fun fromKey(k: String?): SortMode = entries.firstOrNull { it.key == k } ?: ADDED_DESC
    }
}

data class LibraryFilter(
    val onlyWithScript: Boolean = false,
    val onlyFavorite: Boolean = false,
    val minRating: Int = 0,
    val selectedTags: Set<String> = emptySet(),
    val selectedFolders: Set<String> = emptySet(),
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Container.libraryRepo
    private val prefs = Container.prefs

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter

    val sortMode: StateFlow<SortMode> = prefs.sortMode
        .map { SortMode.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortMode.ADDED_DESC)

    val viewMode: StateFlow<LibraryViewMode> = prefs.viewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryViewMode.GRID)

    val folders: StateFlow<List<FolderEntity>> = repo.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videos: StateFlow<List<VideoEntity>> = combine(
        repo.videos,
        _search,
        sortMode,
        _filter,
    ) { vids, search, sort, filter ->
        applyFilters(vids, search, filter).let { applySort(it, sort) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<String>> = repo.videos.map { vids ->
        vids.flatMap { v -> v.tags.split(',').map { it.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = MutableStateFlow(false)

    // Room Flow가 실제 첫 emit을 줄 때까지는 false → LibraryScreen이 EmptyLibrary 깜빡임 없이 검은 배경 유지
    private val _initialLoaded = MutableStateFlow(false)
    val initialLoaded: StateFlow<Boolean> = _initialLoaded

    init {
        viewModelScope.launch {
            repo.videos.first()
            _initialLoaded.value = true
        }
    }

    fun setSearch(s: String) { _search.value = s }
    fun setFilter(f: LibraryFilter) { _filter.value = f }
    fun setSortMode(m: SortMode) {
        viewModelScope.launch { prefs.setSortMode(m.key) }
    }
    fun toggleViewMode() {
        viewModelScope.launch {
            val next = if (viewMode.value == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
            prefs.setViewMode(next)
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            val autoMatch = prefs.scriptAutoMatch.first()
            val multiExt = prefs.scriptMultiExt.first()
            isScanning.value = true
            try { repo.addFolder(uri, autoMatch, multiExt) } finally { isScanning.value = false }
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch { repo.removeFolder(uri) }
    }

    fun rescanAll() {
        viewModelScope.launch {
            val autoMatch = prefs.scriptAutoMatch.first()
            val multiExt = prefs.scriptMultiExt.first()
            isScanning.value = true
            try { repo.rescanAll(autoMatch, multiExt) } finally { isScanning.value = false }
        }
    }

    fun setRating(uri: String, r: Int) {
        viewModelScope.launch { repo.setRating(uri, r) }
    }

    fun setFavorite(uri: String, fav: Boolean) {
        viewModelScope.launch { repo.setFavorite(uri, fav) }
    }

    fun setTags(uri: String, tags: List<String>) {
        viewModelScope.launch {
            repo.setTags(uri, tags.joinToString(",") { it.trim() }.trim(','))
        }
    }

    fun clearLibrary() {
        viewModelScope.launch { repo.clearLibrary() }
    }

    private fun applyFilters(
        vids: List<VideoEntity>,
        search: String,
        filter: LibraryFilter,
    ): List<VideoEntity> {
        val s = search.trim().lowercase()
        return vids.filter { v ->
            val matchesSearch = s.isBlank() ||
                v.title.lowercase().contains(s) ||
                v.tags.lowercase().contains(s)
            matchesSearch &&
                (!filter.onlyWithScript || v.funscriptUri != null) &&
                (!filter.onlyFavorite || v.favorite) &&
                (v.rating >= filter.minRating) &&
                (filter.selectedFolders.isEmpty() || v.folderUri in filter.selectedFolders) &&
                (filter.selectedTags.isEmpty() || filter.selectedTags.all { tag ->
                    v.tags.split(',').map { it.trim() }.any { it.equals(tag, ignoreCase = true) }
                })
        }
    }

    private fun applySort(vids: List<VideoEntity>, sort: SortMode): List<VideoEntity> = when (sort) {
        SortMode.ADDED_DESC -> vids.sortedByDescending { it.addedAt }
        SortMode.ADDED_ASC -> vids.sortedBy { it.addedAt }
        SortMode.TITLE_ASC -> vids.sortedBy { it.title.lowercase() }
        SortMode.TITLE_DESC -> vids.sortedByDescending { it.title.lowercase() }
        SortMode.DURATION_DESC -> vids.sortedByDescending { it.durationMs }
        SortMode.DURATION_ASC -> vids.sortedBy { it.durationMs }
        SortMode.RATING_DESC -> vids.sortedByDescending { it.rating }
        SortMode.RESOLUTION_DESC -> vids.sortedByDescending { it.width.toLong() * it.height.toLong() }
        SortMode.SIZE_DESC -> vids.sortedByDescending { it.sizeBytes }
        SortMode.HAS_SCRIPT -> vids.sortedWith(
            compareByDescending<VideoEntity> { it.funscriptUri != null }
                .thenByDescending { it.addedAt },
        )
    }
}
