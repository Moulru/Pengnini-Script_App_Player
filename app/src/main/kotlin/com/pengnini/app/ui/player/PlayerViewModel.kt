package com.pengnini.app.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pengnini.app.Container
import com.pengnini.app.data.db.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class HandySyncState {
    data object Idle : HandySyncState()
    data object Uploading : HandySyncState()
    data object Preparing : HandySyncState()
    data object Ready : HandySyncState()
    data class Error(val message: String) : HandySyncState()
}

class PlayerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = Container.libraryRepo
    private val handyRepo = Container.handyRepo
    private val prefs = Container.prefs

    val videoUri: String = handle.get<String>("videoUri") ?: ""

    private val _video = MutableStateFlow<VideoEntity?>(null)
    val video: StateFlow<VideoEntity?> = _video

    private val _prevUri = MutableStateFlow<String?>(null)
    val prevUri: StateFlow<String?> = _prevUri
    private val _nextUri = MutableStateFlow<String?>(null)
    val nextUri: StateFlow<String?> = _nextUri

    val loopEnabled: StateFlow<Boolean> = prefs.loopEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val keepScreenOn: StateFlow<Boolean> = prefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val backgroundPlayback: StateFlow<Boolean> = prefs.backgroundPlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val playbackSpeedX10: StateFlow<Int> = prefs.playbackSpeedX10
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val playbackAspect: StateFlow<String> = prefs.playbackAspect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fit")
    val subtitleAuto: StateFlow<Boolean> = prefs.subtitleAuto
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val subtitleSize: StateFlow<String> = prefs.subtitleSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "medium")
    val gestureSeekSec: StateFlow<Int> = prefs.gestureSeekSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val gestureBrightness: StateFlow<Boolean> = prefs.gestureBrightness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gestureVolume: StateFlow<Boolean> = prefs.gestureVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gestureZoom: StateFlow<Boolean> = prefs.gestureZoom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncOffsetMs: StateFlow<Int> = prefs.syncOffsetMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val strokeMin: StateFlow<Int> = prefs.strokeMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val strokeMax: StateFlow<Int> = prefs.strokeMax
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    private val _syncState = MutableStateFlow<HandySyncState>(HandySyncState.Idle)
    val syncState: StateFlow<HandySyncState> = _syncState

    @Volatile private var serverOffset: Long = 0L
    @Volatile private var syncInitialized: Boolean = false

    init {
        viewModelScope.launch {
            _video.value = repo.getVideo(videoUri)
            val list = runCatching { repo.getAllVideoUrisOrdered() }.getOrDefault(emptyList())
            val idx = list.indexOf(videoUri)
            if (idx >= 0) {
                _prevUri.value = list.getOrNull(idx - 1)
                _nextUri.value = list.getOrNull(idx + 1)
            }
        }
        // syncState가 Ready로 전환되면 현재 stroke 범위를 Handy에 동기화
        viewModelScope.launch {
            syncState.collect { state ->
                if (state is HandySyncState.Ready) {
                    runCatching { handyRepo.setStrokeRange(strokeMin.value, strokeMax.value) }
                }
            }
        }
    }

    fun startSync() {
        if (syncInitialized) return
        val v = _video.value ?: return
        val scriptUri = v.funscriptUri ?: return
        if (!handyRepo.hasKey) return
        viewModelScope.launch {
            _syncState.value = HandySyncState.Uploading
            try {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        getApplication<Application>().contentResolver
                            .openInputStream(Uri.parse(scriptUri))?.use { it.readBytes() }
                    }.getOrNull()
                } ?: run {
                    _syncState.value = HandySyncState.Error("스크립트 읽기 실패")
                    return@launch
                }
                val url = handyRepo.uploadScript(v.title, bytes).getOrElse {
                    _syncState.value = HandySyncState.Error("업로드 실패: ${it.message ?: "알 수 없음"}")
                    return@launch
                }
                _syncState.value = HandySyncState.Preparing
                handyRepo.setupHssp(url).getOrElse {
                    _syncState.value = HandySyncState.Error("setup 실패")
                    return@launch
                }
                serverOffset = handyRepo.syncOffset(samples = 10)
                syncInitialized = true
                _syncState.value = HandySyncState.Ready
            } catch (e: Exception) {
                _syncState.value = HandySyncState.Error(e.message ?: "오류")
            }
        }
    }

    fun onPlay(positionMs: Long) {
        if (_syncState.value !is HandySyncState.Ready) return
        val offset = syncOffsetMs.value
        val adjustedPos = (positionMs + offset).coerceAtLeast(0)
        viewModelScope.launch {
            handyRepo.play(System.currentTimeMillis() + serverOffset, adjustedPos)
        }
    }

    fun onPause() {
        if (_syncState.value !is HandySyncState.Ready) return
        viewModelScope.launch { handyRepo.stop() }
    }

    fun onSeek(newPositionMs: Long, wasPlaying: Boolean) {
        if (_syncState.value !is HandySyncState.Ready) return
        if (wasPlaying) {
            val offset = syncOffsetMs.value
            val adjustedPos = (newPositionMs + offset).coerceAtLeast(0)
            viewModelScope.launch {
                handyRepo.play(System.currentTimeMillis() + serverOffset, adjustedPos)
            }
        }
    }

    fun setLoop(enabled: Boolean) {
        viewModelScope.launch { prefs.setLoop(enabled) }
    }

    fun setSyncOffsetMs(v: Int) {
        viewModelScope.launch { prefs.setSyncOffsetMs(v) }
    }

    /** 사용자가 "Handy 오류" 배지를 눌러 재연결 시도. */
    fun retrySync() {
        if (_syncState.value is HandySyncState.Ready) return
        syncInitialized = false
        _syncState.value = HandySyncState.Idle
        startSync()
    }

    /** PrefsStore에 저장하고, Handy 연결 상태면 즉시 stroke 범위 적용 (`PUT /slide`). */
    fun setStrokeRange(min: Int, max: Int) {
        viewModelScope.launch {
            prefs.setStrokeRange(min, max)
            if (_syncState.value is HandySyncState.Ready) {
                runCatching { handyRepo.setStrokeRange(min, max) }
            }
        }
    }

    fun savePosition(positionMs: Long) {
        val v = _video.value ?: return
        if (positionMs <= 0) return
        viewModelScope.launch { repo.setLastPosition(v.uri, positionMs) }
    }

    fun resetPosition() {
        val v = _video.value ?: return
        viewModelScope.launch { repo.setLastPosition(v.uri, 0) }
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { handyRepo.stop() }
        }
    }
}
