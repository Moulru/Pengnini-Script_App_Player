package com.pengnini.app.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pengnini.app.data.db.FolderDao
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.VideoDao
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.VideoUserDataDao
import com.pengnini.app.data.db.VideoUserDataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class LibraryViewMode { GRID, LIST }

class LibraryRepository(
    private val folderDao: FolderDao,
    private val videoDao: VideoDao,
    private val userDataDao: VideoUserDataDao,
    private val scanner: MediaScanner,
    private val context: Context,
) {
    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()
    val videos: Flow<List<VideoEntity>> = videoDao.observeAll()

    private val metadataStore = MetadataStore(context)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushJobs = ConcurrentHashMap<String, Job>()

    suspend fun addFolder(uri: Uri, matchScripts: Boolean = true, multiExt: Boolean = false) {
        runCatching {
            // 사이드카 기록을 위해 쓰기 권한도 함께 영구화 (트리 선택은 보통 쓰기 포함)
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val displayName = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()
        folderDao.insert(FolderEntity(uri.toString(), displayName))
        scanFolder(uri.toString(), matchScripts, multiExt)
    }

    suspend fun removeFolder(uriStr: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriStr),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        folderDao.delete(uriStr)
        videoDao.deleteByFolder(uriStr)
        // video_user_data는 의도적으로 보존 → 같은 폴더 재등록 시 평점/태그 복원
    }

    suspend fun scanFolder(uriStr: String, matchScripts: Boolean = true, multiExt: Boolean = false) {
        val uri = Uri.parse(uriStr)
        val items = scanner.scan(uri, matchScripts, multiExt)
        if (items.isEmpty()) return
        // 사용자 데이터 복원: 우선 DB(user_data, uri 정확 매칭) → 없으면 사이드카(title 매칭, 재설치·이식 대비)
        val userData = userDataDao.getAll().associateBy { it.uri }
        val sidecar = metadataStore.read(uriStr)
        val merged = items.map { fresh ->
            val ud = userData[fresh.uri]
            if (ud != null) {
                fresh.copy(
                    rating = ud.rating,
                    favorite = ud.favorite,
                    tags = ud.tags,
                    lastPositionMs = ud.lastPositionMs,
                )
            } else {
                val side = sidecar[fresh.title]
                if (side != null) {
                    // 사이드카에서 복원하고 DB(user_data)에도 시드
                    userDataDao.ensure(
                        VideoUserDataEntity(fresh.uri, side.rating, side.favorite, side.tags, 0L),
                    )
                    fresh.copy(rating = side.rating, favorite = side.favorite, tags = side.tags)
                } else {
                    fresh
                }
            }
        }
        videoDao.upsertAll(merged)
    }

    suspend fun rescanAll(matchScripts: Boolean = true, multiExt: Boolean = false) {
        folderDao.getAll().forEach { scanFolder(it.uri, matchScripts, multiExt) }
    }

    suspend fun setRating(uri: String, rating: Int) {
        videoDao.setRating(uri, rating)
        userDataDao.ensure(VideoUserDataEntity(uri))
        userDataDao.updateRating(uri, rating)
        scheduleSidecarFlush(uri)
    }

    suspend fun setFavorite(uri: String, favorite: Boolean) {
        videoDao.setFavorite(uri, favorite)
        userDataDao.ensure(VideoUserDataEntity(uri))
        userDataDao.updateFavorite(uri, favorite)
        scheduleSidecarFlush(uri)
    }

    suspend fun setTags(uri: String, tags: String) {
        videoDao.setTags(uri, tags)
        userDataDao.ensure(VideoUserDataEntity(uri))
        userDataDao.updateTags(uri, tags)
        scheduleSidecarFlush(uri)
    }

    suspend fun setLastPosition(uri: String, positionMs: Long) {
        videoDao.setLastPosition(uri, positionMs)
        userDataDao.ensure(VideoUserDataEntity(uri))
        userDataDao.updateLastPosition(uri, positionMs)
        // 위치는 재생 중 5초마다 갱신되어 사이드카 플러시는 생략(DB에만 보존)
    }

    suspend fun getVideo(uri: String): VideoEntity? = videoDao.get(uri)
    suspend fun getAllVideoUrisOrdered(): List<String> = videoDao.getAllUrisOrdered()

    suspend fun clearLibrary() {
        videoDao.clear()
        folderDao.clear()
    }

    /** rating/favorite/tags 변경 후 해당 폴더 사이드카를 디바운스 기록(백그라운드, best-effort). */
    private fun scheduleSidecarFlush(uri: String) {
        flushJobs.remove(uri)?.cancel()
        flushJobs[uri] = ioScope.launch {
            delay(SIDECAR_FLUSH_DELAY_MS)
            val folderUri = videoDao.get(uri)?.folderUri ?: return@launch
            flushSidecar(folderUri)
            flushJobs.remove(uri)
        }
    }

    /** 한 폴더의 모든 영상 사용자 데이터를 사이드카에 기록(기본값만 있는 영상은 제외). */
    private suspend fun flushSidecar(folderUri: String) {
        val vids = videoDao.getByFolder(folderUri)
        if (vids.isEmpty()) return
        val userData = userDataDao.getAll().associateBy { it.uri }
        val entries = HashMap<String, MetadataStore.Entry>()
        vids.forEach { v ->
            val ud = userData[v.uri] ?: return@forEach
            if (ud.rating != 0 || ud.favorite || ud.tags.isNotBlank()) {
                entries[v.title] = MetadataStore.Entry(ud.rating, ud.favorite, ud.tags)
            }
        }
        metadataStore.write(folderUri, entries)
    }

    private companion object {
        const val SIDECAR_FLUSH_DELAY_MS = 1500L
    }
}
