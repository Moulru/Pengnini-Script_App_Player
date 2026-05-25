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
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class HandyRepository(private val keyStore: HandyKeyStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val contentType = "application/json".toMediaType()

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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

    suspend fun checkConnected(): Result<Boolean> = runCatching {
        if (!hasKey) return Result.failure(IllegalStateException("Connection Key가 등록되지 않았습니다"))
        api.getConnected().connected
    }

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

    suspend fun uploadScript(name: String, bytes: ByteArray): Result<String> = runCatching {
        val mediaType = "application/json".toMediaType()
        val safeName = if (name.endsWith(".funscript", ignoreCase = true)) name else "$name.funscript"
        val body = MultipartBody.Part.createFormData(
            "syncScript",
            safeName,
            bytes.toRequestBody(mediaType),
        )
        scriptApi.upload(body).url.ifBlank { error("업로드 응답이 비어있습니다") }
    }

    suspend fun setupHssp(url: String): Result<Unit> = runCatching {
        runCatching { api.setMode(ModeBody(1)) }
        api.hsspSetup(HsspSetupBody(url))
        Unit
    }

    suspend fun play(estimatedServerTime: Long, startTimeMs: Long): Result<Unit> = runCatching {
        api.hsspPlay(HsspPlayBody(estimatedServerTime, startTimeMs))
        Unit
    }

    suspend fun stop(): Result<Unit> = runCatching {
        api.hsspStop()
        Unit
    }

    suspend fun setStrokeRange(min: Int, max: Int): Result<Unit> = runCatching {
        api.setSlide(SlideBody(min, max))
        Unit
    }

    suspend fun syncOffset(samples: Int = 12): Long = withContext(Dispatchers.IO) {
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
        if (offsets.isEmpty()) return@withContext 0L
        val sorted = offsets.sorted()
        val trimmed = if (sorted.size > 4) sorted.drop(1).dropLast(1) else sorted
        trimmed.average().toLong()
    }
}

data class HandyStatus(
    val connected: Boolean,
    val battery: Int?,
    val firmware: String?,
    val model: String?,
)
