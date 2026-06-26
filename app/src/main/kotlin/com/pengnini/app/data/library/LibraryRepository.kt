package com.pengnini.app.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.pengnini.app.data.db.FolderDao
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.PengniniDatabase
import com.pengnini.app.data.db.VideoDao
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.db.VideoUserDataDao
import com.pengnini.app.data.db.VideoUserDataEntity
import com.pengnini.app.data.media.ThumbnailCache
import com.pengnini.app.data.secure.SmbCredential
import com.pengnini.app.data.secure.SmbCredentialStore
import com.pengnini.app.data.smb.SmbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class LibraryViewMode { GRID, LIST }

class LibraryRepository(
    private val db: PengniniDatabase,
    private val folderDao: FolderDao,
    private val videoDao: VideoDao,
    private val userDataDao: VideoUserDataDao,
    private val scanner: MediaScanner,
    private val smbCredentials: SmbCredentialStore,
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

    suspend fun addSmbFolder(
        host: String,
        share: String,
        path: String,
        username: String,
        password: String,
        domain: String,
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
    ): Int {
        smbCredentials.save(host, SmbCredential(username, password, domain))
        val folderUri = SmbManager.buildUri(host, share, path)
        val displayName = listOf(host, share, path.trim('/'))
            .filter { it.isNotBlank() }.joinToString("/")
        folderDao.insert(FolderEntity(folderUri, displayName))
        scanFolder(folderUri, matchScripts, multiExt)
        return videoDao.getByFolder(folderUri).size
    }

    suspend fun removeFolder(uriStr: String) {
        if (SmbManager.isSmb(uriStr)) {
            folderDao.delete(uriStr)
            videoDao.deleteByFolder(uriStr)
            // 같은 host의 다른 폴더가 남아있지 않으면 자격증명 제거
            val host = runCatching { SmbManager.parse(uriStr).host }.getOrNull()
            if (host != null) {
                val stillUsed = folderDao.getAll().any {
                    SmbManager.isSmb(it.uri) &&
                        runCatching { SmbManager.parse(it.uri).host }.getOrNull() == host
                }
                if (!stillUsed) smbCredentials.remove(host)
            }
            return
        }
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
        val items = if (SmbManager.isSmb(uriStr)) {
            scanner.scanSmb(uriStr, multiExt)
        } else {
            scanner.scan(Uri.parse(uriStr), matchScripts, multiExt)
        }
        if (items.isEmpty()) return
        // 사용자 데이터 복원: 우선 DB(user_data, uri 정확 매칭) → 없으면 사이드카(title 매칭, 재설치·이식 대비)
        val userData = userDataDao.getAll().associateBy { it.uri }
        val sidecar = metadataStore.read(uriStr)
        val existing = videoDao.getByFolder(uriStr).associateBy { it.uri }
        val merged = items.map { fresh ->
            // 기존 행이 있으면 addedAt(정렬 기준)과 수동 연결 스크립트를 보존
            val prior = existing[fresh.uri]
            val base = fresh.copy(
                funscriptUri = fresh.funscriptUri ?: prior?.funscriptUri,
                addedAt = prior?.addedAt ?: fresh.addedAt,
            )
            val ud = userData[base.uri]
            if (ud != null) {
                base.copy(
                    rating = ud.rating,
                    favorite = ud.favorite,
                    tags = ud.tags,
                    lastPositionMs = ud.lastPositionMs,
                    customTitle = ud.customTitle,
                    // 자동 매칭(fresh)·기존 행(prior)이 없으면 영속 백업한 수동 연결을 복원
                    funscriptUri = base.funscriptUri ?: ud.funscriptUri,
                )
            } else {
                val side = sidecar[base.title]
                if (side != null) {
                    // 사이드카에서 복원하고 DB(user_data)에도 시드
                    userDataDao.ensure(
                        VideoUserDataEntity(
                            base.uri, side.rating, side.favorite, side.tags, 0L, side.customTitle,
                        ),
                    )
                    base.copy(
                        rating = side.rating,
                        favorite = side.favorite,
                        tags = side.tags,
                        customTitle = side.customTitle,
                    )
                } else {
                    base
                }
            }
        }
        videoDao.upsertAll(merged)
    }

    suspend fun rescanAll(matchScripts: Boolean = true, multiExt: Boolean = false) {
        folderDao.getAll().forEach { scanFolder(it.uri, matchScripts, multiExt) }
    }

    suspend fun setRating(uri: String, rating: Int) {
        db.withTransaction {
            videoDao.setRating(uri, rating)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateRating(uri, rating)
        }
        scheduleSidecarFlush(uri)
    }

    suspend fun setFavorite(uri: String, favorite: Boolean) {
        db.withTransaction {
            videoDao.setFavorite(uri, favorite)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateFavorite(uri, favorite)
        }
        scheduleSidecarFlush(uri)
    }

    suspend fun setTags(uri: String, tags: String) {
        db.withTransaction {
            videoDao.setTags(uri, tags)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateTags(uri, tags)
        }
        scheduleSidecarFlush(uri)
    }

    suspend fun setLastPosition(uri: String, positionMs: Long) {
        db.withTransaction {
            videoDao.setLastPosition(uri, positionMs)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateLastPosition(uri, positionMs)
        }
        // 위치는 재생 중 5초마다 갱신되어 사이드카 플러시는 생략(DB에만 보존)
    }

    /** 앱 내 표시이름 변경(실제 파일은 그대로). 빈 문자열이면 null로 저장해 원래 파일명 사용. */
    suspend fun setCustomTitle(uri: String, title: String?) {
        val v = title?.trim()?.takeIf { it.isNotBlank() }
        db.withTransaction {
            videoDao.setCustomTitle(uri, v)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateCustomTitle(uri, v)
        }
        scheduleSidecarFlush(uri)
    }

    /** 스크립트 수동 연결/해제. scriptUri는 SAF로 영구 read 권한을 미리 확보해 둔다. */
    suspend fun setFunscript(uri: String, scriptUri: String?) {
        // videos(표시용) + user_data(영속 백업)에 함께 기록 → 폴더 재등록 시 수동 연결 복원
        db.withTransaction {
            videoDao.setFunscript(uri, scriptUri)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateFunscript(uri, scriptUri)
        }
    }

    /** 재생 중 확보된 길이·해상도를 DB에 기록(스캔 시 못 구한 SMB 영상 보완). */
    suspend fun updateMediaInfo(uri: String, durationMs: Long, width: Int, height: Int) {
        videoDao.updateMediaInfo(uri, durationMs, width, height)
    }

    /**
     * 영상 파일과 연결된 스크립트 파일을 저장소에서 삭제 후 DB/메타데이터 정리.
     * 파일 삭제에 실패하면(권한 없음 등) DB를 건드리지 않고 false 반환 — 재스캔 시 되살아나는 것 방지.
     */
    suspend fun deleteVideo(uri: String): Boolean {
        val video = videoDao.get(uri)
        val deleted = withContext(Dispatchers.IO) {
            runCatching {
                DocumentFile.fromSingleUri(context, Uri.parse(uri))?.delete() ?: false
            }.getOrDefault(false)
        }
        if (!deleted) return false
        video?.funscriptUri?.let { scriptUri ->
            withContext(Dispatchers.IO) {
                runCatching { DocumentFile.fromSingleUri(context, Uri.parse(scriptUri))?.delete() }
            }
        }
        videoDao.delete(uri)
        userDataDao.delete(uri)
        ThumbnailCache.remove(context, uri)
        return true
    }

    suspend fun getVideo(uri: String): VideoEntity? = videoDao.get(uri)
    suspend fun getAllVideoUrisOrdered(): List<String> = videoDao.getAllUrisOrdered()

    /** 라이브러리 완전 초기화: 폴더·영상 목록 + 평가(별점·태그) + 사이드카 백업까지 모두 제거. */
    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        // 디바운스 대기 중인 사이드카 기록을 먼저 취소(안 그러면 삭제 직후 되살아날 수 있음)
        flushJobs.values.forEach { it.cancel() }
        flushJobs.clear()
        // 사이드카(평가 백업)를 먼저 지워야 같은 폴더 재등록 시 평가가 되살아나지 않음
        folderDao.getAll().forEach { runCatching { metadataStore.delete(it.uri) } }
        videoDao.clear()
        folderDao.clear()
        userDataDao.clear()
    }

    /** rating/favorite/tags 변경 후 해당 폴더 사이드카를 디바운스 기록(백그라운드, best-effort). */
    private fun scheduleSidecarFlush(uri: String) {
        flushJobs.remove(uri)?.cancel()
        val job = ioScope.launch {
            delay(SIDECAR_FLUSH_DELAY_MS)
            val folderUri = videoDao.get(uri)?.folderUri ?: return@launch
            flushSidecar(folderUri)
        }
        flushJobs[uri] = job
        // 완료 시 자기 자신일 때만 제거 → 디바운스 재등록된 새 job을 지우지 않도록
        job.invokeOnCompletion { flushJobs.remove(uri, job) }
    }

    /** 한 폴더의 모든 영상 사용자 데이터를 사이드카에 기록(기본값만 있는 영상은 제외). */
    private suspend fun flushSidecar(folderUri: String) {
        val vids = videoDao.getByFolder(folderUri)
        if (vids.isEmpty()) return
        val userData = userDataDao.getAll().associateBy { it.uri }
        val entries = HashMap<String, MetadataStore.Entry>()
        vids.forEach { v ->
            val ud = userData[v.uri] ?: return@forEach
            if (ud.rating != 0 || ud.favorite || ud.tags.isNotBlank() || ud.customTitle != null) {
                entries[v.title] = MetadataStore.Entry(ud.rating, ud.favorite, ud.tags, ud.customTitle)
            }
        }
        metadataStore.write(folderUri, entries)
    }

    private companion object {
        const val SIDECAR_FLUSH_DELAY_MS = 1500L
    }
}
