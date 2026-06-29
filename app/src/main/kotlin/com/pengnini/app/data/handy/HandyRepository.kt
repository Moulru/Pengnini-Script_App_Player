package com.pengnini.app.data.handy

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pengnini.app.data.secure.HandyKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HandyRepository(private val keyStore: HandyKeyStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val contentType = "application/json".toMediaType()

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("X-Connection-Key") // 로그 레벨을 올려도 키가 평문 노출되지 않게
            },
        )
        .addInterceptor { chain ->
            val key = keyStore.read()
            val req = if (!key.isNullOrBlank()) {
                chain.request().newBuilder().header("X-Connection-Key", key).build()
            } else chain.request()
            chain.proceed(req)
        }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: HandyApi = Retrofit.Builder()
        .baseUrl("https://www.handyfeeling.com/api/handy/v2/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(HandyApi::class.java)

    private val scriptApi: ScriptUploadApi = Retrofit.Builder()
        .baseUrl("https://scripts01.handyfeeling.com/api/script/v0/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(ScriptUploadApi::class.java)

    val hasKey: Boolean get() = !keyStore.read().isNullOrBlank()

    // 세션 캐시 — 느린 인터넷·반복 재생에서 노란 sync 시간 단축
    private val offsetTtlMs = 5 * 60 * 1000L
    @Volatile private var cachedServerOffset: Long? = null
    @Volatile private var cachedOffsetAt: Long = 0L
    @Volatile private var lastUpload: Pair<String, String>? = null  // sha256 → url

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    suspend fun fetchStatus(): Result<HandyStatus> = runCatching {
        val connected = api.getConnected().connected
        val battery = runCatching { api.getBattery().level }.getOrNull()
        val info = runCatching { api.getInfo() }.getOrNull()
        HandyStatus(
            connected = connected,
            battery = battery,
            firmware = info?.fwVersion,
            model = info?.model,
        )
    }

    suspend fun uploadScript(name: String, bytes: ByteArray, sha256: String): Result<String> = runCatching {
        // 같은 스크립트를 이 세션에서 이미 업로드했으면 재업로드 생략
        lastUpload?.let { (sha, url) -> if (sha == sha256) return Result.success(url) }
        val mediaType = "application/json".toMediaType()
        val safeName = if (name.endsWith(".funscript", ignoreCase = true)) name else "$name.funscript"
        val body = MultipartBody.Part.createFormData(
            "syncScript",
            safeName,
            bytes.toRequestBody(mediaType),
        )
        val url = scriptApi.upload(body).url.ifBlank { error("업로드 응답이 비어있습니다") }
        lastUpload = sha256 to url
        url
    }

    // Retrofit은 Response<T> 반환 시 4xx/5xx에 예외를 던지지 않으므로 직접 검사해야 한다.
    private fun Response<Unit>.orThrow() {
        if (!isSuccessful) error("Handy 응답 오류 HTTP ${code()}")
    }

    suspend fun setupHssp(url: String, sha256: String? = null): Result<Unit> = runCatching {
        // 동기 모드(HSSP) 전환 실패를 무시하면 스크립트가 동작하지 않으므로 응답 검사.
        api.setMode(ModeBody(1)).orThrow()
        // sha256 전달 시 디바이스 캐시에 있으면 재다운로드 생략
        api.hsspSetup(HsspSetupBody(url, sha256)).orThrow()
        Unit
    }

    suspend fun play(estimatedServerTime: Long, startTimeMs: Long): Result<Unit> = runCatching {
        api.hsspPlay(HsspPlayBody(estimatedServerTime, startTimeMs)).orThrow()
        Unit
    }

    suspend fun stop(): Result<Unit> = runCatching {
        api.hsspStop().orThrow()
        Unit
    }

    suspend fun setStrokeRange(min: Int, max: Int): Result<Unit> = runCatching {
        api.setSlide(SlideBody(min, max)).orThrow()
        Unit
    }

    suspend fun syncOffset(samples: Int = 12): Long {
        // 서버 클럭 오차는 몇 분간 안정적 → 세션 내 재측정 생략(영상마다 10회 왕복 제거)
        val now = System.currentTimeMillis()
        cachedServerOffset?.let { if (now - cachedOffsetAt < offsetTtlMs) return it }
        val sampled = withContext(Dispatchers.IO) {
            val offsets = mutableListOf<Long>()
            repeat(samples) {
                try {
                    val tSend = System.currentTimeMillis()
                    val serverTime = api.getServerTime().serverTime
                    val tRecv = System.currentTimeMillis()
                    val rtt = tRecv - tSend
                    val estimated = serverTime + rtt / 2
                    offsets += estimated - tRecv
                } catch (_: Exception) {
                    // skip failed sample
                }
            }
            if (offsets.isEmpty()) {
                null
            } else {
                val sorted = offsets.sorted()
                val trimmed = if (sorted.size > 4) sorted.drop(1).dropLast(1) else sorted
                trimmed.average().toLong()
            }
        }
        // 측정 실패(네트워크 없음)는 캐시하지 않아 다음 시도에 재측정
        return if (sampled != null) {
            cachedServerOffset = sampled
            cachedOffsetAt = now
            sampled
        } else {
            0L
        }
    }
}

data class HandyStatus(
    val connected: Boolean,
    val battery: Int?,
    val firmware: String?,
    val model: String?,
)
