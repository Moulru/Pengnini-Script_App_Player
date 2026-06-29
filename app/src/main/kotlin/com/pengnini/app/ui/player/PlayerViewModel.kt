package com.pengnini.app.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pengnini.app.Container
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.smb.SmbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed class HandySyncState {
    data object Idle : HandySyncState()
    data object Uploading : HandySyncState()
    data object Preparing : HandySyncState()
    data object Ready : HandySyncState()
    data class Error(val message: String) : HandySyncState()
}

/** 기기로 보낼 재생 명령. 단일 통로로 직렬화해 순서 꼬임을 막는다. */
private sealed interface DeviceCommand {
    data class Play(val serverTime: Long, val startTimeMs: Long) : DeviceCommand
    data object Stop : DeviceCommand
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
    @Volatile private var playbackSpeed: Float = 1.0f

    // 기기 명령 단일 통로(CONFLATED) — 빠른 시크/버퍼링으로 명령이 몰려도
    // 항상 마지막 의도만 순서대로 전달돼 정지/재생이 뒤섞이지 않는다.
    private val deviceCommands = Channel<DeviceCommand>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (cmd in deviceCommands) {
                when (cmd) {
                    // 일시적 네트워크 블립은 짧게 재시도해 자가회복, 계속 실패할 때만 오류 표시.
                    is DeviceCommand.Play ->
                        if (retryCommand { handyRepo.play(cmd.serverTime, cmd.startTimeMs) }) {
                            // 일시 실패 후 다시 성공하면 오류 배지 자동 해제
                            if (syncInitialized && _syncState.value is HandySyncState.Error) {
                                _syncState.value = HandySyncState.Ready
                            }
                        } else {
                            _syncState.value = HandySyncState.Error("기기 전송 실패")
                        }
                    // 정지는 조용히 재시도(실패해도 오류 배지는 띄우지 않음).
                    DeviceCommand.Stop -> retryCommand { handyRepo.stop() }
                }
            }
        }
        viewModelScope.launch {
            // alwaysStartFromBeginning ON이면 lastPositionMs를 0으로 덮어 ExoPlayer 초기 seekTo가 일어나지 않게 함.
            // DB의 실제 값은 손대지 않음(다음 정지 시 다시 갱신됨).
            val v = repo.getVideo(videoUri)
            val fromStart = prefs.alwaysStartFromBeginning.first()
            _video.value = if (v != null && fromStart) v.copy(lastPositionMs = 0L) else v
            val list = runCatching { repo.getAllVideoUrisOrdered() }.getOrDefault(emptyList())
            val idx = list.indexOf(videoUri)
            if (idx >= 0) {
                _prevUri.value = list.getOrNull(idx - 1)
                _nextUri.value = list.getOrNull(idx + 1)
            }
        }
        // syncState가 Ready로 전환되면 현재 세기 범위를 기기에 동기화
        viewModelScope.launch {
            syncState.collect { state ->
                if (state is HandySyncState.Ready) {
                    runCatching { handyRepo.setStrokeRange(strokeMin.value, strokeMax.value) }
                }
            }
        }
        // 반전 설정 변경 시 스크립트 재업로드.
        // prefs.scriptInvert를 직접 구독 — 첫 emit(저장된 현재 값)은 drop(1)으로 무시하고
        // 이후 사용자 토글에 의한 변경만 resync. (StateFlow는 stateIn 초기값 emit이 섞여 race 발생)
        viewModelScope.launch {
            prefs.scriptInvert.drop(1).collect { resync() }
        }
    }

    fun startSync(speed: Float = playbackSpeed) {
        if (syncInitialized) return
        val v = _video.value ?: return
        if (!handyRepo.hasKey) return
        playbackSpeed = speed
        viewModelScope.launch {
            val scriptUri = v.funscriptUri
            val useDefault = scriptUri == null
            // 스크립트 없고 기본 스크립트도 꺼져 있으면 동기화하지 않음
            if (useDefault && !prefs.defaultScriptEnabled.first()) return@launch
            val invert = prefs.scriptInvert.first()
            _syncState.value = HandySyncState.Uploading
            try {
                val bytes = if (useDefault) {
                    val cpm = prefs.defaultScriptCpm.first()
                    val durMs = v.durationMs.takeIf { it > 0 } ?: 7_200_000L // 길이 미상(SMB 등) 시 2시간
                    // 0↔99 위치 반복 + 영상 배속 반영. 반전은 대칭이라 적용 불필요.
                    transformFunscript(generateDefaultScript(durMs, cpm), false, speed)
                } else {
                    val rawBytes = withContext(Dispatchers.IO) {
                        runCatching {
                            if (SmbManager.isSmb(scriptUri!!)) {
                                Container.smbManager.readBytes(scriptUri)
                            } else {
                                getApplication<Application>().contentResolver
                                    .openInputStream(Uri.parse(scriptUri))?.use { it.readBytes() }
                            }
                        }.getOrNull()
                    } ?: run {
                        _syncState.value = HandySyncState.Error("스크립트 읽기 실패")
                        return@launch
                    }
                    transformFunscript(rawBytes, invert, speed)
                }
                val sha = handyRepo.sha256(bytes)
                val name = if (useDefault) "default" else v.title
                val url = handyRepo.uploadScript(name, bytes, sha).getOrElse {
                    _syncState.value = HandySyncState.Error("업로드 실패: ${it.message ?: "알 수 없음"}")
                    return@launch
                }
                _syncState.value = HandySyncState.Preparing
                handyRepo.setupHssp(url, sha).getOrElse {
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

    /** 스크립트 없는 영상용 기본 패턴: 0↔99 위치 반복(cpm=분당 반복 수). 세기 범위는 기기의 /slide가 적용. */
    private fun generateDefaultScript(durationMs: Long, cpm: Int): ByteArray {
        val halfMs = (60000L / cpm.coerceIn(30, 200)) / 2 // 0→99 또는 99→0 한 구간
        val sb = StringBuilder("{\"actions\":[")
        var t = 0L
        var pos = 0
        var first = true
        var count = 0
        // 액션 수 상한 — 매우 빠른 속도 + 긴 영상에서 JSON 과대·업로드 부담 방지
        while (t <= durationMs && count < 60_000) {
            if (!first) sb.append(',')
            sb.append("{\"at\":").append(t).append(",\"pos\":").append(pos).append('}')
            first = false
            pos = if (pos == 0) 99 else 0
            t += halfMs
            count++
        }
        sb.append("]}")
        return sb.toString().toByteArray()
    }

    /** 반전 설정 변경 시 호출. stop → reset → startSync 흐름으로 스크립트 재업로드.
     *  PlayerScreen의 LaunchedEffect(syncState)가 Ready 감지 시 현재 위치에서 onPlay를 다시 트리거한다. */
    private fun resync() {
        // 스크립트 유무와 무관 — 기본 스크립트도 배속 변경 시 재업로드해야 한다(판단은 startSync 내부).
        if (_video.value == null || !handyRepo.hasKey) return
        viewModelScope.launch {
            runCatching { handyRepo.stop() }
            syncInitialized = false
            _syncState.value = HandySyncState.Idle
            startSync()
        }
    }

    /** 재생 속도 변경 시 호출. 배속을 스크립트에 반영하기 위해, 이미 업로드된 상태면 재업로드(resync). */
    fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 4f)
        if (s == playbackSpeed) return
        playbackSpeed = s
        if (syncInitialized) resync()
    }

    /** 스크립트 JSON 변환:
     *  - invert: pos → (99 - pos)
     *  - speed≠1: at → (at / speed)로 스케일 → 기기가 1x로 재생해도 영상과 동기 유지(주기 재전송 불필요).
     *  파싱 실패 시 원본 반환. */
    private fun transformFunscript(bytes: ByteArray, invert: Boolean, speed: Float): ByteArray = try {
        if (!invert && speed == 1.0f) {
            bytes
        } else {
            val root = Json.parseToJsonElement(String(bytes)).jsonObject
            val actions = root["actions"]?.jsonArray
            if (actions == null) bytes
            else {
                val transformed = actions.map { el ->
                    val obj = el.jsonObject.toMutableMap()
                    if (invert) {
                        val pos = obj["pos"]?.jsonPrimitive?.intOrNull
                        if (pos != null) obj["pos"] = JsonPrimitive(99 - pos)
                    }
                    if (speed != 1.0f) {
                        val at = obj["at"]?.jsonPrimitive?.longOrNull
                        if (at != null) obj["at"] = JsonPrimitive((at / speed).toLong())
                    }
                    JsonObject(obj)
                }
                val out = root.toMutableMap()
                out["actions"] = JsonArray(transformed)
                JsonObject(out).toString().toByteArray()
            }
        }
    } catch (_: Exception) {
        bytes
    }

    // 스크립트 설정이 끝난 뒤(syncInitialized)면 Ready/Error 무관하게 명령을 보낸다.
    // → 일시 실패로 Error가 돼도 다음 재생/시크에서 자동 재시도되어 스스로 회복.
    fun onPlay(positionMs: Long) {
        if (!syncInitialized) return
        deviceCommands.trySend(playCommand(positionMs))
    }

    fun onPause() {
        if (!syncInitialized) return
        deviceCommands.trySend(DeviceCommand.Stop)
    }

    fun onSeek(newPositionMs: Long, wasPlaying: Boolean) {
        if (!syncInitialized) return
        if (wasPlaying) deviceCommands.trySend(playCommand(newPositionMs))
    }

    /** 현재 시점 기준 재생 명령 생성. 위치와 서버시각을 같은 시점에 함께 캡처해 어긋남을 막는다. */
    private fun playCommand(positionMs: Long): DeviceCommand.Play {
        val offset = syncOffsetMs.value
        // 스크립트에 배속을 미리 반영해 두었으므로 보내는 위치도 1/speed로 환산
        val adjustedPos = ((positionMs + offset).coerceAtLeast(0) / playbackSpeed).toLong()
        val startServerTime = System.currentTimeMillis() + serverOffset
        return DeviceCommand.Play(startServerTime, adjustedPos)
    }

    /** 명령을 짧게 재시도. 한 번이라도 성공하면 true, 모두 실패하면 false. */
    private suspend fun retryCommand(block: suspend () -> Result<Unit>): Boolean {
        repeat(CMD_MAX_ATTEMPTS) { attempt ->
            if (block().isSuccess) return true
            if (attempt < CMD_MAX_ATTEMPTS - 1) delay(CMD_RETRY_DELAY_MS)
        }
        return false
    }

    fun setLoop(enabled: Boolean) {
        viewModelScope.launch { prefs.setLoop(enabled) }
    }

    fun setSyncOffsetMs(v: Int) {
        viewModelScope.launch { prefs.setSyncOffsetMs(v) }
    }

    /** 사용자가 연결 오류 배지를 눌러 재연결 시도. */
    fun retrySync() {
        if (_syncState.value is HandySyncState.Ready) return
        syncInitialized = false
        _syncState.value = HandySyncState.Idle
        startSync()
    }

    /** PrefsStore에 저장하고, 기기 연결 상태면 즉시 세기 범위 적용 (`PUT /slide`). */
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

    /** 첫 재생 때 확보된 길이·해상도를 DB에 1회 기록(스캔 시 못 구한 SMB 영상). */
    fun saveMediaInfo(uri: String, durationMs: Long, width: Int, height: Int) {
        if (durationMs <= 0) return
        // 메모리 _video도 갱신 — 같은 세션 동안 시크바/마커/제스처가 durationMs=0으로 깨지지 않게
        _video.value?.takeIf { it.uri == uri }?.let {
            _video.value = it.copy(durationMs = durationMs, width = width, height = height)
        }
        viewModelScope.launch { repo.updateMediaInfo(uri, durationMs, width, height) }
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

    private companion object {
        const val CMD_MAX_ATTEMPTS = 3      // 최초 1회 + 재시도 2회
        const val CMD_RETRY_DELAY_MS = 300L
    }
}
