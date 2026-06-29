package com.pengnini.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pengnini.app.BuildConfig
import com.pengnini.app.Container
import com.pengnini.app.data.handy.HandyStatus
import com.pengnini.app.data.media.ThumbnailCache
import com.pengnini.app.data.secure.HandyKeyStore
import com.pengnini.app.data.secure.LockPattern
import com.pengnini.app.ui.lock.PatternLock
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Categories
// ─────────────────────────────────────────────────────────────

private data class Category(
    val key: String,
    val title: String,
    val icon: ImageVector,
)

private val Categories = listOf(
    Category("handy", "Handy Connect", Icons.Outlined.Link),
    Category("script", "스크립트", Icons.Outlined.Bolt),
    Category("playback", "재생", Icons.Outlined.PlayCircle),
    Category("gesture", "제스처", Icons.Outlined.Gesture),
    Category("subtitle", "자막", Icons.Outlined.ClosedCaption),
    Category("security", "보안", Icons.Outlined.Security),
    Category("system", "시스템", Icons.Outlined.Build),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Categories.forEach { cat ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(cat.title) },
                        leadingContent = { Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                        },
                        modifier = Modifier.clickableRow { onOpenCategory(cat.key) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: String,
    onBack: () -> Unit = {},
) {
    val title = Categories.firstOrNull { it.key == category }?.title ?: "설정"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (category) {
                "handy" -> HandyConnectContent()
                "script" -> ScriptContent()
                "playback" -> PlaybackContent()
                "gesture" -> GestureContent()
                "subtitle" -> SubtitleContent()
                "security" -> SecurityContent()
                "system" -> SystemContent()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 기기 연결
// ─────────────────────────────────────────────────────────────

@Composable
private fun HandyConnectContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Container.prefs
    val keyStore = remember { HandyKeyStore(context) }
    val handyRepo = remember { Container.handyRepo }

    var savedKey by remember { mutableStateOf(keyStore.read()) }
    var dialogOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<HandyStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val offsetMs by prefs.syncOffsetMs.collectAsStateWithLifecycle(initialValue = 0)
    val strokeMin by prefs.strokeMin.collectAsStateWithLifecycle(initialValue = 0)
    val strokeMax by prefs.strokeMax.collectAsStateWithLifecycle(initialValue = 100)
    var offsetDialogOpen by remember { mutableStateOf(false) }
    var strokeDialogOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Connection Key") },
            supportingContent = {
                Text(if (savedKey != null) mask(savedKey!!) else "등록되지 않음")
            },
            leadingContent = { Icon(Icons.Outlined.Key, null) },
            trailingContent = {
                TextButton(onClick = { dialogOpen = true }) {
                    Text(if (savedKey != null) "수정" else "등록")
                }
            },
        )
    }

    if (savedKey != null) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("연결 테스트") },
                supportingContent = {
                    val s = status
                    val message = statusMessage
                    when {
                        checking -> Text("확인 중…")
                        message != null -> Text(message, color = MaterialTheme.colorScheme.error)
                        s == null -> Text("테스트 버튼으로 디바이스 상태 확인")
                        s.connected -> Text(
                            buildString {
                                append("연결됨")
                                s.battery?.let { append(" · ${it}%") }
                                s.firmware?.let { append(" · FW $it") }
                            },
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Text("디바이스 오프라인", color = MaterialTheme.colorScheme.error)
                    }
                },
                leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch {
                            checking = true
                            statusMessage = null
                            handyRepo.fetchStatus()
                                .onSuccess { status = it }
                                .onFailure {
                                    status = null
                                    statusMessage = "오류: " + (it.message ?: "알 수 없음")
                                }
                            checking = false
                        }
                    }) { Text("테스트") }
                },
            )
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SelectableRow(
                icon = Icons.Outlined.Sync,
                title = "기본 오프셋",
                value = "${if (offsetMs > 0) "+" else ""}$offsetMs ms",
                onClick = { offsetDialogOpen = true },
            )
            SelectableRow(
                icon = Icons.Outlined.Sync,
                title = "스트로크 범위",
                value = "$strokeMin ~ $strokeMax",
                onClick = { strokeDialogOpen = true },
            )
        }
    }

    if (dialogOpen) {
        HandyKeyDialog(
            initial = savedKey.orEmpty(),
            onDismiss = { dialogOpen = false },
            onConfirm = { newKey ->
                val trimmed = newKey.trim()
                keyStore.write(trimmed)
                savedKey = trimmed
                dialogOpen = false
                status = null
                statusMessage = null
            },
            onClear = {
                keyStore.clear()
                savedKey = null
                status = null
                statusMessage = null
                dialogOpen = false
            },
        )
    }

    if (offsetDialogOpen) {
        SliderDialog(
            title = "기본 오프셋",
            initial = offsetMs,
            valueRange = -200..200,
            formatter = { "${if (it > 0) "+" else ""}$it ms" },
            onDismiss = { offsetDialogOpen = false },
            onConfirm = {
                scope.launch { prefs.setSyncOffsetMs(it) }
                offsetDialogOpen = false
            },
        )
    }
    if (strokeDialogOpen) {
        RangeSliderDialog(
            title = "스트로크 범위",
            initialMin = strokeMin,
            initialMax = strokeMax,
            valueRange = 0..100,
            formatter = { lo, hi -> "$lo ~ $hi" },
            onDismiss = { strokeDialogOpen = false },
            onConfirm = { lo, hi ->
                scope.launch { prefs.setStrokeRange(lo, hi) }
                strokeDialogOpen = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Script
// ─────────────────────────────────────────────────────────────

@Composable
private fun ScriptContent() {
    val prefs = Container.prefs
    val scope = rememberCoroutineScope()
    val autoMatch by prefs.scriptAutoMatch.collectAsStateWithLifecycle(initialValue = true)
    val multiExt by prefs.scriptMultiExt.collectAsStateWithLifecycle(initialValue = false)
    val invert by prefs.scriptInvert.collectAsStateWithLifecycle(initialValue = false)
    val defaultEnabled by prefs.defaultScriptEnabled.collectAsStateWithLifecycle(initialValue = false)
    val defaultCpm by prefs.defaultScriptCpm.collectAsStateWithLifecycle(initialValue = 60)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            BoundSwitchRow(
                icon = Icons.Outlined.Bolt,
                title = "자동 매칭",
                subtitle = "영상과 같은 이름의 .funscript 파일 자동 로드",
                checked = autoMatch,
                onCheckedChange = { scope.launch { prefs.setScriptAutoMatch(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Bolt,
                title = "확장자 다중 지원",
                subtitle = ".funscript 외 .json 도 스크립트로 인식",
                checked = multiExt,
                onCheckedChange = { scope.launch { prefs.setScriptMultiExt(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Bolt,
                title = "기본 반전",
                subtitle = "stroke 방향을 반대로 적용 (pos = 99 − pos)",
                checked = invert,
                onCheckedChange = { scope.launch { prefs.setScriptInvert(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Bolt,
                title = "기본 스크립트",
                subtitle = "스크립트 없는 영상에 단순 왕복 패턴 자동 재생",
                checked = defaultEnabled,
                onCheckedChange = { scope.launch { prefs.setDefaultScriptEnabled(it) } },
            )
            if (defaultEnabled) {
                DefaultScriptSpeedRow(
                    cpm = defaultCpm,
                    onChange = { scope.launch { prefs.setDefaultScriptCpm(it) } },
                )
            }
        }
    }
}

@Composable
private fun DefaultScriptSpeedRow(cpm: Int, onChange: (Int) -> Unit) {
    var local by remember(cpm) { mutableStateOf(cpm.toFloat()) }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Text(
            "왕복 속도: ${local.roundToInt()}회/분",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = 30f..200f,
            steps = 16, // 10단위 스냅 (30, 40, …, 200)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Playback
// ─────────────────────────────────────────────────────────────

private val AspectOptions = listOf(
    "fit" to "맞춤 (Fit)",
    "fill" to "채움 (Fill)",
    "stretch" to "늘림 (Stretch)",
    "16:9" to "16:9",
    "4:3" to "4:3",
)

@Composable
private fun PlaybackContent() {
    val prefs = Container.prefs
    val scope = rememberCoroutineScope()
    val speedX10 by prefs.playbackSpeedX10.collectAsStateWithLifecycle(initialValue = 10)
    val aspect by prefs.playbackAspect.collectAsStateWithLifecycle(initialValue = "fit")
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = false)
    val keepScreenOn by prefs.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    val loop by prefs.loopEnabled.collectAsStateWithLifecycle(initialValue = false)
    val alwaysFromStart by prefs.alwaysStartFromBeginning.collectAsStateWithLifecycle(initialValue = false)

    var speedDialogOpen by remember { mutableStateOf(false) }
    var aspectDialogOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SelectableRow(
                icon = Icons.Outlined.Speed,
                title = "기본 재생 속도",
                value = String.format("%.2f×", speedX10 / 10.0),
                onClick = { speedDialogOpen = true },
            )
            SelectableRow(
                icon = Icons.Outlined.PlayCircle,
                title = "기본 화면비",
                value = AspectOptions.firstOrNull { it.first == aspect }?.second ?: aspect,
                onClick = { aspectDialogOpen = true },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.PlayCircle,
                title = "백그라운드 재생",
                subtitle = "앱이 백그라운드로 가도 계속 재생",
                checked = background,
                onCheckedChange = { scope.launch { prefs.setBackground(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.PlayCircle,
                title = "화면 자동 꺼짐 방지",
                subtitle = "재생 중 화면을 깬 상태로 유지",
                checked = keepScreenOn,
                onCheckedChange = { scope.launch { prefs.setKeepScreenOn(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.PlayCircle,
                title = "항상 처음부터 재생",
                subtitle = "이어보기를 끄고 영상을 열 때마다 처음부터 시작",
                checked = alwaysFromStart,
                onCheckedChange = { scope.launch { prefs.setAlwaysStartFromBeginning(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Loop,
                title = "항상 영상 무한루프",
                subtitle = "영상이 끝나면 처음부터 다시 재생",
                checked = loop,
                onCheckedChange = { scope.launch { prefs.setLoop(it) } },
            )
        }
    }

    if (speedDialogOpen) {
        SliderDialog(
            title = "기본 재생 속도",
            initial = speedX10,
            valueRange = 5..20,
            formatter = { String.format("%.2f×", it / 10.0) },
            onDismiss = { speedDialogOpen = false },
            onConfirm = {
                scope.launch { prefs.setPlaybackSpeedX10(it) }
                speedDialogOpen = false
            },
        )
    }
    if (aspectDialogOpen) {
        RadioDialog(
            title = "기본 화면비",
            options = AspectOptions,
            selected = aspect,
            onDismiss = { aspectDialogOpen = false },
            onSelect = {
                scope.launch { prefs.setPlaybackAspect(it) }
                aspectDialogOpen = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Gesture
// ─────────────────────────────────────────────────────────────

private val DoubleTapOptions = listOf(
    5 to "5초",
    10 to "10초",
    20 to "20초",
    30 to "30초",
)

@Composable
private fun GestureContent() {
    val prefs = Container.prefs
    val scope = rememberCoroutineScope()
    val seekSec by prefs.gestureSeekSec.collectAsStateWithLifecycle(initialValue = 10)
    val brightness by prefs.gestureBrightness.collectAsStateWithLifecycle(initialValue = true)
    val volume by prefs.gestureVolume.collectAsStateWithLifecycle(initialValue = true)
    val zoom by prefs.gestureZoom.collectAsStateWithLifecycle(initialValue = true)

    var dtDialogOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SelectableRow(
                icon = Icons.Outlined.Gesture,
                title = "더블탭 탐색",
                value = "${seekSec}초",
                onClick = { dtDialogOpen = true },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Gesture,
                title = "좌측 세로 스와이프로 밝기 조절",
                checked = brightness,
                onCheckedChange = { scope.launch { prefs.setGestureBrightness(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Gesture,
                title = "우측 세로 스와이프로 볼륨 조절",
                checked = volume,
                onCheckedChange = { scope.launch { prefs.setGestureVolume(it) } },
            )
            BoundSwitchRow(
                icon = Icons.Outlined.Gesture,
                title = "화면 줌",
                subtitle = "두 손가락 핀치로 원본/fit/fill 순환",
                checked = zoom,
                onCheckedChange = { scope.launch { prefs.setGestureZoom(it) } },
            )
        }
    }

    if (dtDialogOpen) {
        RadioDialog(
            title = "더블탭 탐색 시간",
            options = DoubleTapOptions,
            selected = seekSec,
            onDismiss = { dtDialogOpen = false },
            onSelect = {
                scope.launch { prefs.setGestureSeekSec(it) }
                dtDialogOpen = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Subtitle
// ─────────────────────────────────────────────────────────────

private val SubtitleSizeOptions = listOf(
    "small" to "작게",
    "medium" to "중간",
    "large" to "크게",
)

@Composable
private fun SubtitleContent() {
    val prefs = Container.prefs
    val scope = rememberCoroutineScope()
    val auto by prefs.subtitleAuto.collectAsStateWithLifecycle(initialValue = true)
    val size by prefs.subtitleSize.collectAsStateWithLifecycle(initialValue = "medium")

    var sizeDialogOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            BoundSwitchRow(
                icon = Icons.Outlined.ClosedCaption,
                title = "자막 자동 매칭",
                subtitle = ".srt · .ass 사이드카 자동 로드",
                checked = auto,
                onCheckedChange = { scope.launch { prefs.setSubtitleAuto(it) } },
            )
            SelectableRow(
                icon = Icons.Outlined.ClosedCaption,
                title = "자막 크기",
                value = SubtitleSizeOptions.firstOrNull { it.first == size }?.second ?: size,
                onClick = { sizeDialogOpen = true },
            )
        }
    }

    if (sizeDialogOpen) {
        RadioDialog(
            title = "자막 크기",
            options = SubtitleSizeOptions,
            selected = size,
            onDismiss = { sizeDialogOpen = false },
            onSelect = {
                scope.launch { prefs.setSubtitleSize(it) }
                sizeDialogOpen = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Security (잠금화면)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SecurityContent() {
    val prefs = Container.prefs
    val store = remember { Container.lockStore }
    val scope = rememberCoroutineScope()
    val enabled by prefs.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    var hasPattern by remember { mutableStateOf(store.hasPattern()) }

    var registerOpen by remember { mutableStateOf(false) }
    var changeStep by remember { mutableStateOf(0) } // 0=닫힘, 1=현재 확인, 2=새 입력
    var deleteOpen by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        BoundSwitchRow(
            icon = Icons.Outlined.Lock,
            title = "앱 시작화면 잠금",
            subtitle = "앱 실행 시 패턴 잠금 화면 표시",
            checked = enabled,
            onCheckedChange = { newVal ->
                if (newVal && !hasPattern) {
                    pendingEnable = true
                    registerOpen = true
                } else {
                    scope.launch { prefs.setAppLockEnabled(newVal) }
                }
            },
        )
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text("잠금패턴 설정") },
                supportingContent = if (hasPattern) ({ Text("등록됨") }) else null,
                leadingContent = { Icon(Icons.Outlined.Pattern, null) },
                trailingContent = {
                    TextButton(onClick = { registerOpen = true }) {
                        Text(if (hasPattern) "재설정" else "등록")
                    }
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        "잠금패턴 변경",
                        color = if (enabled && hasPattern) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Edit, null) },
                trailingContent = {
                    TextButton(
                        onClick = { changeStep = 1 },
                        enabled = enabled && hasPattern,
                    ) { Text("변경") }
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        "잠금패턴 삭제",
                        color = if (enabled && hasPattern) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.DeleteForever, null) },
                trailingContent = {
                    TextButton(
                        onClick = { deleteOpen = true },
                        enabled = enabled && hasPattern,
                    ) { Text("삭제") }
                },
            )
        }
    }

    if (registerOpen) {
        PatternRegisterDialog(
            onDismiss = {
                registerOpen = false
                pendingEnable = false
            },
            onConfirm = { p ->
                store.write(p)
                hasPattern = true
                registerOpen = false
                if (pendingEnable) {
                    scope.launch { prefs.setAppLockEnabled(true) }
                    pendingEnable = false
                }
            },
        )
    }
    if (changeStep == 1) {
        PatternConfirmDialog(
            title = "현재 패턴 확인",
            onDismiss = { changeStep = 0 },
            onConfirmed = { changeStep = 2 },
        )
    }
    if (changeStep == 2) {
        PatternRegisterDialog(
            onDismiss = { changeStep = 0 },
            onConfirm = { p ->
                store.write(p)
                hasPattern = true
                changeStep = 0
            },
        )
    }
    if (deleteOpen) {
        PatternConfirmDialog(
            title = "패턴 확인 후 삭제",
            onDismiss = { deleteOpen = false },
            onConfirmed = {
                store.clear()
                hasPattern = false
                deleteOpen = false
                scope.launch { prefs.setAppLockEnabled(false) }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// System
// ─────────────────────────────────────────────────────────────

private val LanguageOptions = listOf(
    "system" to "시스템 따라가기",
    "ko" to "한국어",
    "en" to "English",
)

@Composable
private fun SystemContent() {
    val context = LocalContext.current
    val prefs = Container.prefs
    val scope = rememberCoroutineScope()
    val hw by prefs.hwAccel.collectAsStateWithLifecycle(initialValue = true)
    val lang by prefs.language.collectAsStateWithLifecycle(initialValue = "system")
    var langDialogOpen by remember { mutableStateOf(false) }
    var clearedMessage by remember { mutableStateOf<String?>(null) }
    var resetDialogOpen by remember { mutableStateOf(false) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            BoundSwitchRow(
                icon = Icons.Outlined.Videocam,
                title = "비디오 하드웨어 가속",
                subtitle = "디바이스 하드웨어 디코더 우선 사용",
                checked = hw,
                onCheckedChange = { scope.launch { prefs.setHwAccel(it) } },
            )
            SelectableRow(
                icon = Icons.Outlined.Language,
                title = "언어",
                value = LanguageOptions.firstOrNull { it.first == lang }?.second ?: lang,
                onClick = { langDialogOpen = true },
            )
            ListItem(
                headlineContent = { Text("라이브러리 초기화") },
                supportingContent = {
                    Text(clearedMessage ?: "폴더·영상·별점·태그를 모두 초기화 · 실제 파일은 안전")
                },
                leadingContent = { Icon(Icons.Outlined.DeleteForever, null) },
                trailingContent = {
                    TextButton(onClick = { resetDialogOpen = true }) { Text("초기화") }
                },
            )
            ListItem(
                headlineContent = { Text("앱 버전") },
                supportingContent = { Text("Pengnini / ${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
            )
        }
    }

    if (langDialogOpen) {
        RadioDialog(
            title = "언어",
            options = LanguageOptions,
            selected = lang,
            onDismiss = { langDialogOpen = false },
            onSelect = {
                scope.launch { prefs.setLanguage(it) }
                com.pengnini.app.applyAppLocale(it)
                langDialogOpen = false
            },
        )
    }

    if (resetDialogOpen) {
        AlertDialog(
            onDismissRequest = { resetDialogOpen = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text("라이브러리를 초기화할까요?") },
            text = {
                Text("폴더·영상 목록 및 별점·즐겨찾기·태그까지 모두 삭제됩니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    resetDialogOpen = false
                    scope.launch {
                        runCatching {
                            Container.libraryRepo.clearLibrary()
                            val loader = coil.Coil.imageLoader(context)
                            loader.memoryCache?.clear()
                            loader.diskCache?.clear()
                            ThumbnailCache.clearAll(context)
                        }
                        clearedMessage = "초기화됨"
                    }
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { resetDialogOpen = false }) { Text("취소") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Reusable rows
// ─────────────────────────────────────────────────────────────

@Composable
private fun BoundSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickableRow { onCheckedChange(!checked) },
    )
}

@Composable
private fun SelectableRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        },
        modifier = Modifier.clickableRow(onClick),
    )
}

// ─────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────

@Composable
private fun SliderDialog(
    title: String,
    initial: Int,
    valueRange: IntRange,
    formatter: (Int) -> String = { it.toString() },
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initial.toFloat()) }
    val steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    formatter(value.toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    steps = steps,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatter(valueRange.first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatter(valueRange.last),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toInt()) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun RangeSliderDialog(
    title: String,
    initialMin: Int,
    initialMax: Int,
    valueRange: IntRange,
    formatter: (Int, Int) -> String = { a, b -> "$a ~ $b" },
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var range by remember {
        mutableStateOf(initialMin.toFloat()..initialMax.toFloat())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    formatter(range.start.toInt(), range.endInclusive.toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${valueRange.first}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${valueRange.last}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(range.start.toInt(), range.endInclusive.toInt())
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun <T> RadioDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableRow { onSelect(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun PatternRegisterDialog(
    onDismiss: () -> Unit,
    onConfirm: (LockPattern) -> Unit,
) {
    var first by remember { mutableStateOf<LockPattern?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var resetTrigger by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (first == null) "새 잠금패턴" else "확인을 위해 다시 입력") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                PatternLock(
                    size = 220.dp,
                    resetTrigger = resetTrigger,
                    onComplete = { points ->
                        val p = LockPattern.tryOf(points)
                        when {
                            p == null -> {
                                error = "최소 4점이 필요합니다"
                                resetTrigger++
                            }
                            first == null -> {
                                first = p
                                error = null
                                resetTrigger++
                            }
                            first!!.points == p.points -> onConfirm(p)
                            else -> {
                                error = "패턴이 일치하지 않습니다. 처음부터 다시 입력하세요."
                                first = null
                                resetTrigger++
                            }
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PatternConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val store = remember { Container.lockStore }
    var error by remember { mutableStateOf(false) }
    var resetTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(error) {
        if (error) {
            delay(500)
            error = false
            resetTrigger++
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (error) "패턴이 일치하지 않습니다" else "현재 패턴을 그려주세요",
                    color = if (error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                PatternLock(
                    size = 220.dp,
                    errorFlash = error,
                    resetTrigger = resetTrigger,
                    onComplete = { points ->
                        val input = LockPattern.tryOf(points)
                        val saved = store.read()
                        if (input != null && saved != null && input.points == saved.points) {
                            onConfirmed()
                        } else {
                            error = true
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun HandyKeyDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Key") },
        text = {
            Column {
                Text(
                    "handyfeeling.com 온보딩에서 발급받은 키",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    label = { Text("Connection Key") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().length in 4..32,
            ) { Text("저장") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("삭제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

private fun mask(key: String): String {
    if (key.length <= 4) return "****"
    return key.take(4) + "****" + key.takeLast(4)
}
