package com.pengnini.app.ui.folder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pengnini.app.Container
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.library.FolderBucket
import com.pengnini.app.ui.library.VideoActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 폴더 탭(목록 + 추가·제거·재스캔). 영상 조회는 [FolderVideosViewModel]. */
class FolderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Container.libraryRepo
    private val prefs = Container.prefs

    val buckets: StateFlow<List<FolderBucket>> = repo.folderBuckets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 등록한 최상위 폴더 — 추가·제거 대상. 목록에 보이는 분류(buckets)와는 단위가 다르다. */
    val roots: StateFlow<List<FolderEntity>> = repo.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = MutableStateFlow(false)

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress

    private var scanJob: Job? = null

    fun addFolder(uri: Uri, onResult: (Int) -> Unit = {}) {
        launchScan(onResult) { autoMatch, multiExt ->
            repo.addFolder(uri, autoMatch, multiExt) { _scanProgress.value = it }
        }
    }

    fun addSmbFolder(
        host: String,
        share: String,
        path: String,
        username: String,
        password: String,
        onResult: (Int) -> Unit,
    ) {
        launchScan(onResult) { autoMatch, multiExt ->
            repo.addSmbFolder(host, share, path, username, password, "", autoMatch, multiExt) {
                _scanProgress.value = it
            }
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch { repo.removeFolder(uri) }
    }

    fun rescanAll(onResult: (Int) -> Unit = {}) {
        launchScan(onResult) { autoMatch, multiExt ->
            repo.rescanAll(autoMatch, multiExt) { _scanProgress.value = it }
        }
    }

    /** 폴더 하나만 다시 스캔(목록의 ⋮ → 재스캔). 대상은 등록한 최상위 폴더. */
    fun rescanRoot(rootUri: String, onResult: (Int) -> Unit = {}) {
        launchScan(onResult) { autoMatch, multiExt ->
            repo.scanFolder(rootUri, autoMatch, multiExt) { _scanProgress.value = it }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    private fun launchScan(
        onResult: (Int) -> Unit,
        block: suspend (autoMatch: Boolean, multiExt: Boolean) -> Int,
    ) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val autoMatch = prefs.scriptAutoMatch.first()
            val multiExt = prefs.scriptMultiExt.first()
            _scanProgress.value = 0
            isScanning.value = true
            val count = try {
                block(autoMatch, multiExt)
            } finally {
                isScanning.value = false
                _scanProgress.value = 0
            }
            onResult(count)
        }
    }
}

/**
  * 폴더 하나에 든 영상 목록. 라우트 인자로 받은 bucketKey에 묶인다.
  * 영상 조작은 홈과 완전히 같은 시트를 쓰도록 [VideoActions]를 구현한다.
  */
class FolderVideosViewModel(app: Application, handle: SavedStateHandle) :
    AndroidViewModel(app), VideoActions {
    private val repo = Container.libraryRepo

    val bucketKey: String = handle.get<String>("bucketKey") ?: ""

    val videos: StateFlow<List<VideoEntity>> = repo.videosInBucket(bucketKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 화면 제목. 폴더 분류 목록은 "영상이 있는 폴더"만 담으므로, 마지막 영상을 지우면
     * 이 폴더가 목록에서 빠져 라벨이 비게 된다. 그때 제목이 사라지지 않도록 마지막 값을 유지한다.
     */
    val label: StateFlow<String> = repo.folderBuckets
        .map { list -> list.firstOrNull { it.key == bucketKey }?.label ?: "" }
        .runningFold("") { prev, cur -> cur.ifBlank { prev } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** 태그 편집 다이얼로그의 추천 목록 — 라이브러리 전체 태그를 쓴다. */
    val allTags: StateFlow<List<String>> = repo.videos
        .map { vids ->
            vids.flatMap { v -> v.tags.split(',').map { it.trim() } }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun saveMediaInfo(uri: String, durationMs: Long, width: Int, height: Int) {
        if (durationMs <= 0) return
        viewModelScope.launch { repo.updateMediaInfo(uri, durationMs, width, height) }
    }
}
