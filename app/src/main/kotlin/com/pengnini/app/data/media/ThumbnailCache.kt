package com.pengnini.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.pengnini.app.Container
import com.pengnini.app.data.smb.SmbManager
import com.pengnini.app.data.smb.SmbMediaDataSource
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Semaphore

/**
 * 영상 1/3 지점 프레임을 앱 내부 cacheDir에 JPEG로 1회 생성하고 재사용한다(로컬·SMB 공통).
 * - 내부 저장소(cacheDir)라 다른 앱·갤러리에 보이지 않고, 시스템이 공간 부족 시 정리한다.
 * - 추가로 총 용량 상한(LRU)을 둬 무한정 쌓이지 않게 한다.
 */
object ThumbnailCache {
    private const val TARGET_W = 480
    private const val TARGET_H = 270
    private const val DIR = "thumbs"
    private const val MAX_BYTES = 100L * 1024 * 1024 // 100MB 상한

    private val decodeLimit = Semaphore(3) // 동시 디코드 제한(특히 SMB는 네트워크 비용)

    /** 캐시에 있으면 그 파일, 없으면 생성 후 반환. 실패 시 null. IO 스레드에서 호출. */
    fun get(context: Context, videoUri: String): File? {
        val file = cacheFile(context, videoUri)
        if (file.exists() && file.length() > 0) return file
        decodeLimit.acquire()
        try {
            val bitmap = decodeFrame(context, videoUri) ?: return null
            return runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                file
            }.getOrNull().also {
                bitmap.recycle()
                trimIfNeeded(context)
            }
        } finally {
            decodeLimit.release()
        }
    }

    /** 영상 삭제 시 해당 썸네일도 정리(고아 파일 방지). */
    fun remove(context: Context, videoUri: String) {
        runCatching { cacheFile(context, videoUri).delete() }
    }

    /** 전체 썸네일 캐시 삭제(설정의 "캐시 삭제"에서 호출). */
    fun clearAll(context: Context) {
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    /** 총 용량이 상한을 넘으면 오래된 것부터(LRU) 삭제. */
    private fun trimIfNeeded(context: Context) {
        runCatching {
            val files = File(context.cacheDir, DIR).listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= MAX_BYTES) return@runCatching
                val len = f.length()
                if (f.delete()) total -= len
            }
        }
    }

    private fun cacheFile(context: Context, videoUri: String): File =
        File(context.cacheDir, "$DIR/${sha256(videoUri)}.jpg")

    private fun decodeFrame(context: Context, videoUri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (SmbManager.isSmb(videoUri)) {
                retriever.setDataSource(SmbMediaDataSource(Container.smbManager, videoUri))
            } else {
                retriever.setDataSource(context, Uri.parse(videoUri))
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frameUs = durationMs * 1000L / 3 // 1/3 지점(인트로 로고·검은 화면 회피)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, TARGET_W, TARGET_H,
                )
            } else {
                retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
