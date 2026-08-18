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
import java.util.concurrent.atomic.AtomicLong

/**
 * 영상 1/3 지점 프레임을 앱 내부 cacheDir에 JPEG로 1회 생성하고 재사용한다(로컬·SMB 공통).
 * - 내부 저장소(cacheDir)라 다른 앱·갤러리에 보이지 않고, 시스템이 공간 부족 시 정리한다.
 * - 추가로 총 용량 상한(LRU)을 둬 무한정 쌓이지 않게 한다.
 * - 프레임을 뽑을 때 길이·해상도도 같이 얻으므로, 스캔에서 뺀 메타데이터를 여기서 채운다.
 */
object ThumbnailCache {
    private const val TARGET_W = 480
    private const val TARGET_H = 270
    private const val DIR = "thumbs"
    private const val MAX_BYTES = 100L * 1024 * 1024 // 100MB 상한

    /** 새로 만든 썸네일이 이만큼 쌓였을 때만 LRU 정리 — 1장마다 디렉토리를 통째로 훑지 않게 한다. */
    private const val TRIM_INTERVAL_BYTES = 8L * 1024 * 1024
    private const val MEMO_MAX = 512

    private val decodeLimit = Semaphore(3) // 동시 디코드 제한(특히 SMB는 네트워크 비용)
    private val bytesSinceTrim = AtomicLong(0)

    /**
     * 메타데이터를 읽어 봤지만 길이가 없던 파일 — 스크롤할 때마다 다시 열지 않게 기억한다.
     * memo와 같은 상한을 둬서 읽을 수 없는 파일이 많아도 무한정 쌓이지 않게 한다.
     */
    private val probedWithoutMedia = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean =
            size > MEMO_MAX
    }

    /** uri → 완성된 썸네일 파일. 스크롤로 카드가 다시 보일 때 디스크 확인 자체를 건너뛴다. */
    private val memo = object : LinkedHashMap<String, File>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, File>): Boolean =
            size > MEMO_MAX
    }

    /** 프레임을 뽑으면서 함께 얻은 영상 정보. 캐시에서 바로 꺼낸 경우엔 null. */
    data class MediaInfo(val durationMs: Long, val width: Int, val height: Int)

    data class Result(val file: File, val media: MediaInfo?)

    /**
     * 캐시에 있으면 그 파일, 없으면 생성 후 반환. 실패 시 null. IO 스레드에서 호출.
     *
     * @param needMedia DB에 길이·해상도가 아직 비어 있는 영상이면 true.
     *   썸네일이 이미 캐시에 있어 프레임을 다시 뽑지 않는 경우에도 메타데이터만 따로 읽어 채운다.
     *   (스캔에서 이 값을 안 구하므로, 이 경로가 유일한 보충 수단이다.)
     */
    fun get(context: Context, videoUri: String, needMedia: Boolean = false): Result? {
        cachedFile(context, videoUri)?.let {
            return Result(it, mediaIfNeeded(context, videoUri, needMedia))
        }
        createThumbnail(context, videoUri)?.let { return it }
        // 만드는 동안 다른 카드가 먼저 완성했을 수 있다.
        return cachedFile(context, videoUri)?.let {
            Result(it, mediaIfNeeded(context, videoUri, needMedia))
        }
    }

    /** 메모리·디스크 캐시에 이미 있으면 그 파일. 세마포어를 잡지 않는다. */
    private fun cachedFile(context: Context, videoUri: String): File? {
        memoGet(videoUri)?.let { return it }
        val file = cacheFile(context, videoUri)
        return if (file.exists() && file.length() > 0) memoPut(videoUri, file) else null
    }

    /**
     * 프레임을 뽑아 저장한다. 무거운 구간이라 여기서만 동시 실행을 제한한다.
     * 대기하는 사이 다른 카드가 먼저 만들었거나 디코드에 실패하면 null.
     */
    private fun createThumbnail(context: Context, videoUri: String): Result? {
        val file = cacheFile(context, videoUri)
        decodeLimit.acquire()
        try {
            if (file.exists() && file.length() > 0) return null
            val decoded = decodeFrame(context, videoUri) ?: return null
            return try {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use {
                    decoded.bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
                Result(memoPut(videoUri, file), decoded.media)
            } catch (e: Exception) {
                null
            } finally {
                decoded.bitmap.recycle()
                trimIfNeeded(context, file.length())
            }
        } finally {
            decodeLimit.release()
        }
    }

    /** 영상 삭제 시 해당 썸네일도 정리(고아 파일 방지). */
    fun remove(context: Context, videoUri: String) {
        synchronized(memo) { memo.remove(videoUri) }
        runCatching { cacheFile(context, videoUri).delete() }
    }

    /** 전체 썸네일 캐시 삭제(설정의 "캐시 삭제"에서 호출). */
    fun clearAll(context: Context) {
        synchronized(memo) { memo.clear() }
        synchronized(probedWithoutMedia) { probedWithoutMedia.clear() }
        bytesSinceTrim.set(0)
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    private fun memoGet(videoUri: String): File? =
        synchronized(memo) { memo[videoUri] }?.takeIf { it.exists() }

    private fun memoPut(videoUri: String, file: File): File {
        synchronized(memo) { memo[videoUri] = file }
        return file
    }

    /** 새로 쓴 용량이 일정량 쌓였을 때만 총 용량을 확인하고 오래된 것부터(LRU) 삭제. */
    private fun trimIfNeeded(context: Context, writtenBytes: Long) {
        if (bytesSinceTrim.addAndGet(writtenBytes) < TRIM_INTERVAL_BYTES) return
        bytesSinceTrim.set(0)
        runCatching {
            val files = File(context.cacheDir, DIR).listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= MAX_BYTES) return@runCatching
                val len = f.length()
                if (f.delete()) {
                    total -= len
                    synchronized(memo) { memo.values.remove(f) }
                }
            }
        }
    }

    private fun cacheFile(context: Context, videoUri: String): File =
        File(context.cacheDir, "$DIR/${sha256(videoUri)}.jpg")

    /**
     * 프레임 디코드 없이 길이·해상도만 읽는다. 프레임을 뽑는 것보다 훨씬 싸다.
     * 화면에 보이는 항목에 대해서만, 그것도 값이 비어 있을 때만 일어난다.
     */
    private fun mediaIfNeeded(context: Context, videoUri: String, needMedia: Boolean): MediaInfo? {
        if (!needMedia) return null
        if (synchronized(probedWithoutMedia) { probedWithoutMedia.containsKey(videoUri) }) return null
        decodeLimit.acquire()
        try {
            val info = probeMedia(context, videoUri)
            // 길이를 못 구하는 파일(손상·비표준)은 기억해 두고 다시 열지 않는다.
            if (info == null || info.durationMs <= 0) {
                synchronized(probedWithoutMedia) { probedWithoutMedia[videoUri] = Unit }
            }
            return info
        } finally {
            decodeLimit.release()
        }
    }

    private fun probeMedia(context: Context, videoUri: String): MediaInfo? {
        val retriever = MediaMetadataRetriever()
        var smbSource: SmbMediaDataSource? = null
        return try {
            smbSource = openSource(retriever, context, videoUri)
            readMediaInfo(retriever)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { smbSource?.close() }
        }
    }

    /**
     * SMB면 [SmbMediaDataSource]를 만들어 물리고 그것을 돌려준다 — **호출자가 닫아야 한다.**
     * MediaMetadataRetriever.release()가 MediaDataSource까지 닫아 주는지는 보장되지 않는데,
     * 안 닫히면 SmbManager의 열린 핸들 수가 줄지 않아 host 연결 정리가 영영 막힌다.
     */
    private fun openSource(
        retriever: MediaMetadataRetriever,
        context: Context,
        videoUri: String,
    ): SmbMediaDataSource? {
        if (SmbManager.isSmb(videoUri)) {
            val source = SmbMediaDataSource(Container.smbManager, videoUri)
            retriever.setDataSource(source)
            return source
        }
        retriever.setDataSource(context, Uri.parse(videoUri))
        return null
    }

    private fun readMediaInfo(retriever: MediaMetadataRetriever) = MediaInfo(
        durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L,
        width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0,
        height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0,
    )

    private class Decoded(val bitmap: Bitmap, val media: MediaInfo)

    private fun decodeFrame(context: Context, videoUri: String): Decoded? {
        val retriever = MediaMetadataRetriever()
        var smbSource: SmbMediaDataSource? = null
        return try {
            smbSource = openSource(retriever, context, videoUri)
            val media = readMediaInfo(retriever)
            val durationMs = media.durationMs
            val frameUs = durationMs * 1000L / 3 // 1/3 지점(인트로 로고·검은 화면 회피)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, TARGET_W, TARGET_H,
                )
            } else {
                retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            bitmap?.let { Decoded(it, media) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { smbSource?.close() }
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
