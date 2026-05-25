package com.pengnini.app.data.handy

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface HandyApi {
    @GET("connected")
    suspend fun getConnected(): ConnectedDto

    @GET("info")
    suspend fun getInfo(): InfoDto

    @GET("battery")
    suspend fun getBattery(): BatteryDto

    @PUT("mode")
    suspend fun setMode(@Body body: ModeBody): Response<Unit>

    @PUT("hssp/setup")
    suspend fun hsspSetup(@Body body: HsspSetupBody): Response<Unit>

    @PUT("hssp/play")
    suspend fun hsspPlay(@Body body: HsspPlayBody): Response<Unit>

    @PUT("hssp/stop")
    suspend fun hsspStop(): Response<Unit>

    @PUT("slide")
    suspend fun setSlide(@Body body: SlideBody): Response<Unit>

    @GET("servertime")
    suspend fun getServerTime(): ServerTimeDto
}

interface ScriptUploadApi {
    @Multipart
    @POST("temp/upload")
    suspend fun upload(@Part syncScript: MultipartBody.Part): UploadResponse
}

@Serializable data class ConnectedDto(val connected: Boolean = false)
@Serializable data class InfoDto(
    val fwVersion: String? = null,
    val hwVersion: String? = null,
    val model: String? = null,
)
@Serializable data class BatteryDto(val level: Int = 0)
@Serializable data class ModeBody(val mode: Int)
@Serializable data class HsspSetupBody(val url: String, val sha256: String? = null)
@Serializable data class HsspPlayBody(val estimatedServerTime: Long, val startTime: Long)
@Serializable data class SlideBody(val min: Int, val max: Int)
@Serializable data class ServerTimeDto(val serverTime: Long)
@Serializable data class UploadResponse(val url: String = "")
