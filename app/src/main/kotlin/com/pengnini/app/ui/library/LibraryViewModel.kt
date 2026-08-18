package com.pengnini.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pengnini.app.Container
import com.pengnini.app.R
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.bucketKey
import com.pengnini.app.data.db.displayTitle
import com.pengnini.app.data.library.FolderBucket
import com.pengnini.app.data.library.LibraryViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 정렬 기준. 방향(오름/내림)은 따로 고른다 — 둘을 합치면 목록이 두 배가 된다.
 * 스크립트 유무는 정렬이 아니라 필터로만 다룬다(있음/없음 두 값뿐이라 정렬 기준으로는 의미가 없다).
 */
enum class SortField(val labelRes: Int) {
    ADDED(R.string.sort_field_added),
    TITLE(R.string.sort_field_title),
    DURATION(R.string.sort_field_duration),
    RATING(R.string.sort_field_rating),
    RESOLUTION(R.string.sort_field_resolution),
    SIZE(R.string.sort_field_size),
}

/**
 * 저장되는 정렬 값. 기존 키(`added_desc` 등)를 그대로 유지해 업데이트 후에도 설정이 살아남는다.
 * UI에서는 [SortField] + 방향으로 보여주고 여기서 합친다.
 */
enum class SortMode(val key: String, val field: SortField, val descending: Boolean) {
    ADDED_DESC("added_desc", SortField.ADDED, true),
    ADDED_ASC("added_asc", SortField.ADDED, false),
    TITLE_ASC("title_asc", SortField.TITLE, false),
    TITLE_DESC("title_desc", SortField.TITLE, true),
    DURATION_DESC("duration_desc", SortField.DURATION, true),
    DURATION_ASC("duration_asc", SortField.DURATION, false),
    RATING_DESC("rating_desc", SortField.RATING, true),
    RATING_ASC("rating_asc", SortField.RATING, false),
    RESOLUTION_DESC("resolution_desc", SortField.RESOLUTION, true),
    RESOLUTION_ASC("resolution_asc", SortField.RESOLUTION, false),
    SIZE_DESC("size_desc", SortField.SIZE, true),
    SIZE_ASC("size_asc", SortField.SIZE, false);

    companion object {
        fun fromKey(k: String?): SortMode = entries.firstOrNull { it.key == k } ?: ADDED_DESC
        fun of(field: SortField, descending: Boolean): SortMode =
            entries.first { it.field == field && it.descending == descending }
    }
}

data class LibraryFilter(
    val onlyWithScript: Boolean = false,
    val onlyFavorite: Boolean = false,
    val minRating: Int = 0,
    val selectedTags: Set<String> = emptySet(),
    val selectedFolders: Set<String> = emptySet(),
)

class LibraryViewModel(app: Application) : AndroidViewModel(app), VideoActions {
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

    /** 폴더가 하나라도 등록돼 있는지 — 빈 화면 분기용. */
    val hasFolders: StateFlow<Boolean> = repo.folders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // videos·allTags·buckets가 각자 구독하면 DB 쿼리가 여러 번 돈다. 하나로 공유한다.
    private val allVideos: StateFlow<List<VideoEntity>> = repo.videos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 타이핑 한 글자마다 전체 목록을 다시 거르지 않도록 디바운스.
    @OptIn(FlowPreview::class)
    private val debouncedSearch: Flow<String> =
        _search.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val videos: StateFlow<List<VideoEntity>> = combine(
        allVideos,
        debouncedSearch,
        sortMode,
        _filter,
    ) { vids, search, sort, filter ->
        applyFilters(vids, search, filter).let { applySort(it, sort) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderBuckets: StateFlow<List<FolderBucket>> = repo.folderBuckets
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<String>> = allVideos.map { vids ->
        vids.flatMap { v -> v.tags.split(',').map { it.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Room Flow가 실제 첫 emit을 줄 때까지는 false → LibraryScreen이 빈 화면 깜빡임 없이 배경 유지
    private val _initialLoaded = MutableStateFlow(false)
    val initialLoaded: StateFlow<Boolean> = _initialLoaded

    init {
        viewModelScope.launch {
            repo.videos.first()
            _initialLoaded.value = true
        }
        // 실험실: Handy 미리 연결. 켜져 있을 때만, 라이브러리를 열 때 한 번.
        viewModelScope.launch {
            if (prefs.labHandyPrewarm.first()) {
                runCatching { Container.handyRepo.prewarm() }
            }
        }
    }

    fun setSearch(s: String) { _search.value = s }
    fun setFilter(f: LibraryFilter) { _filter.value = f }

    fun setSortMode(m: SortMode) {
        viewModelScope.launch { prefs.setSortMode(m.key) }
    }

    fun applySheet(f: LibraryFilter, m: SortMode) {
        _filter.value = f
        setSortMode(m)
    }

    /** 상단 빠른 필터 칩. 시트를 열지 않고 가장 흔한 조건만 바로 토글한다. */
    fun toggleQuickScript() {
        _filter.value = _filter.value.copy(onlyWithScript = !_filter.value.onlyWithScript)
    }

    fun toggleQuickFavorite() {
        _filter.value = _filter.value.copy(onlyFavorite = !_filter.value.onlyFavorite)
    }

    fun toggleQuickRating4() {
        val cur = _filter.value.minRating
        _filter.value = _filter.value.copy(minRating = if (cur >= 4) 0 else 4)
    }

    /** '전체' 칩 — 필터만 해제한다. 검색어는 검색창의 X로만 지운다(별개 조작). */
    fun clearQuickFilters() {
        _filter.value = LibraryFilter()
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val next = if (viewMode.value == LibraryViewMode.GRID) {
                LibraryViewMode.LIST
            } else {
                LibraryViewMode.GRID
            }
            prefs.setViewMode(next)
        }
    }

    // ───── VideoActions ─────

    override fun setRating(uri: String, r: Int) {
        viewModelScope.launch { repo.setRating(uri, r) }
    }

    override fun setFavorite(uri: String, fav: Boolean) {
        viewModelScope.launch { repo.setFavorite(uri, fav) }
    }

    override fun setTags(uri: String, tags: List<String>) {
        viewModelScope.launch {
            repo.setTags(uri, tags.joinToString(",") { it.trim() }.trim(','))
        }
    }

    override fun setCustomTitle(uri: String, title: String?) {
        viewModelScope.launch { repo.setCustomTitle(uri, title) }
    }

    override fun setFunscript(uri: String, scriptUri: String?) {
        viewModelScope.launch { repo.setFunscript(uri, scriptUri) }
    }

    override fun deleteVideo(uri: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteVideo(uri)) }
    }

    /** 썸네일을 만들면서 함께 얻은 길이·해상도를 DB에 채운다(스캔은 파일을 열지 않으므로 비어 있다). */
    fun saveMediaInfo(uri: String, durationMs: Long, width: Int, height: Int) {
        if (durationMs <= 0) return
        viewModelScope.launch { repo.updateMediaInfo(uri, durationMs, width, height) }
    }

    // ───── 목록 가공 ─────

    private fun applyFilters(
        vids: List<VideoEntity>,
        search: String,
        filter: LibraryFilter,
    ): List<VideoEntity> {
        val s = search.trim().lowercase()
        return vids.filter { v ->
            val matchesSearch = s.isBlank() ||
                v.displayTitle.lowercase().contains(s) ||
                v.title.lowercase().contains(s) ||
                v.tags.lowercase().contains(s)
            matchesSearch &&
                (!filter.onlyWithScript || v.funscriptUri != null) &&
                (!filter.onlyFavorite || v.favorite) &&
                (v.rating >= filter.minRating) &&
                (filter.selectedFolders.isEmpty() || v.bucketKey in filter.selectedFolders) &&
                (
                    filter.selectedTags.isEmpty() || run {
                        val tagList = v.tags.split(',').map { it.trim() }
                        filter.selectedTags.all { tag -> tagList.any { it.equals(tag, ignoreCase = true) } }
                    }
                    )
        }
    }

    private fun applySort(vids: List<VideoEntity>, sort: SortMode): List<VideoEntity> {
        // 방향은 1차 기준에만 적용한다. 동점 처리까지 뒤집으면
        // 플레이어의 이전/다음 순서 SQL(addedAt DESC, title ASC, uri ASC)과 어긋난다.
        fun <T : Comparable<T>> primary(selector: (VideoEntity) -> T): Comparator<VideoEntity> =
            if (sort.descending) compareByDescending(selector) else compareBy(selector)

        // 제목까지 같은 영상(하위 폴더의 동명 파일)이 있어도 순서가 확정되도록 uri로 마무리한다.
        val byTitle = compareBy<VideoEntity> { it.title.lowercase() }.thenBy { it.uri }

        // 길이·해상도는 스캔이 아니라 썸네일을 만들 때 채워지므로 아직 0인 항목이 섞인다.
        // 이런 '아직 모름'은 방향과 무관하게 항상 뒤로 보낸다 — 오름차순 맨 앞이 전부
        // 0짜리로 채워지면 정렬이 고장난 것처럼 보인다.
        fun unknownLast(isUnknown: (VideoEntity) -> Boolean): Comparator<VideoEntity> =
            compareBy<VideoEntity> { if (isUnknown(it)) 1 else 0 }

        val comparator: Comparator<VideoEntity> = when (sort.field) {
            SortField.ADDED -> primary { it.addedAt }.then(byTitle)
            SortField.TITLE -> primary { it.title.lowercase() }.thenBy { it.uri }
            SortField.DURATION ->
                unknownLast { it.durationMs <= 0 }.then(primary { it.durationMs }).then(byTitle)
            SortField.RATING -> primary { it.rating }.then(byTitle)
            SortField.RESOLUTION ->
                unknownLast { it.width <= 0 || it.height <= 0 }
                    .then(primary { it.width.toLong() * it.height.toLong() })
                    .then(byTitle)
            SortField.SIZE -> primary { it.sizeBytes }.then(byTitle)
        }
        return vids.sortedWith(comparator)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
