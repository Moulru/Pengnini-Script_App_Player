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
import kotlinx.coroutines.flow.combine
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
    private val smb: SmbManager,
    private val context: Context,
) {
    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()
    val videos: Flow<List<VideoEntity>> = videoDao.observeAll()

    /** 하위 폴더까지 나눈 폴더 분류. 홈 필터와 폴더 탭이 같은 목록을 쓴다. */
    val folderBuckets: Flow<List<FolderBucket>> =
        combine(videos, folders) { vids, roots -> buildFolderBuckets(vids, roots) }

    /** 폴더 하나에 든 영상(제목순). */
    fun videosInBucket(bucketKey: String): Flow<List<VideoEntity>> =
        videoDao.observeByBucket(bucketKey)

    private val metadataStore = MetadataStore(context)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushJobs = ConcurrentHashMap<String, Job>()

    /** 폴더를 등록하고 스캔한다. 반환값은 찾은 영상 수. */
    suspend fun addFolder(
        uri: Uri,
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): Int {
        runCatching {
            // 사이드카 기록을 위해 쓰기 권한도 함께 영구화 (트리 선택은 보통 쓰기 포함)
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // 이미 등록한 폴더를 다시 고르면(URI 표기가 달라도) 새로 등록하지 않고 그 폴더를 재스캔한다.
        findRegistered(uri.toString())?.let { return scanFolder(it.uri, matchScripts, multiExt, onProgress) }
        val displayName = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()
        folderDao.insert(FolderEntity(uri.toString(), displayName))
        return scanFolder(uri.toString(), matchScripts, multiExt, onProgress)
    }

    private suspend fun findRegistered(uriStr: String): FolderEntity? {
        val id = folderIdentity(uriStr)
        return folderDao.getAll().firstOrNull { folderIdentity(it.uri) == id }
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
        onProgress: (Int) -> Unit = {},
    ): Int {
        smbCredentials.save(host, SmbCredential(username, password, domain))
        val built = SmbManager.buildUri(host, share, path)
        val folderUri = findRegistered(built)?.uri ?: built.also {
            val displayName = listOf(host, share, path.trim('/'))
                .filter { it.isNotBlank() }.joinToString("/")
            folderDao.insert(FolderEntity(it, displayName))
        }
        scanFolder(folderUri, matchScripts, multiExt, onProgress)
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
                if (!stillUsed) {
                    smbCredentials.remove(host)
                    // 열린 연결·세션이 앱이 끝날 때까지 남지 않게 정리
                    withContext(Dispatchers.IO) { runCatching { smb.releaseHost(host) } }
                }
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

    /**
     * 폴더를 스캔해 DB에 반영하고, 찾은 영상 수를 반환한다.
     *
     * - 같은 파일이 다른 등록 폴더에도 잡히면(상위·하위 폴더를 둘 다 등록, 같은 폴더를 다른 경로로 등록 등)
     *   먼저 등록한 폴더 쪽 한 줄만 남긴다.
     * - 끝까지 읽은 스캔이면 이 폴더에서 더 이상 보이지 않는 영상(삭제·이동·이름 변경)을 목록에서 뺀다.
     *   사용자 데이터(video_user_data)는 남겨 두므로 파일이 돌아오면 평점·태그도 돌아온다.
     */
    suspend fun scanFolder(
        uriStr: String,
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): Int {
        val result = if (SmbManager.isSmb(uriStr)) {
            scanner.scanSmb(uriStr, multiExt, onProgress)
        } else {
            scanner.scan(Uri.parse(uriStr), matchScripts, multiExt, onProgress)
        }
        // 결과가 비어도 "언제 확인했는지"는 남겨야 폴더 목록이 계속 '스캔 안 함'으로 보이지 않는다.
        folderDao.setLastScannedAt(uriStr, System.currentTimeMillis())
        // 하나도 못 읽었으면(권한 해제·네트워크 끊김) 기존 목록을 건드리지 않는다.
        if (result.videos.isEmpty() && !result.complete) return 0

        // 등록 순서. 같은 파일이 여러 폴더에 걸치면 먼저 등록한 폴더가 갖는다.
        val order = folderDao.getAll().sortedBy { it.addedAt }.map { it.uri }
        fun rankOf(folderUri: String) = order.indexOf(folderUri).let { if (it < 0) Int.MAX_VALUE else it }
        val myRank = rankOf(uriStr)
        val elsewhere = videoDao.getOutsideFolder(uriStr).groupBy { fileIdentity(it.uri) }
        val seen = HashSet<String>()
        val evicted = ArrayList<String>() // 이 폴더가 우선이라 지울 다른 폴더의 중복 행
        val items = result.videos.filter { fresh ->
            val key = fileIdentity(fresh.uri)
            if (!seen.add(key)) return@filter false
            val others = elsewhere[key].orEmpty()
            if (others.any { rankOf(it.folderUri) < myRank }) return@filter false
            others.mapTo(evicted) { it.uri }
            true
        }

        // 사용자 데이터 복원: 우선 DB(user_data, uri 정확 매칭) → 없으면 사이드카(title 매칭, 재설치·이식 대비)
        val userData = userDataDao.getAll().associateBy { it.uri }
        val sidecar = metadataStore.read(uriStr)
        val existing = videoDao.getByFolder(uriStr).associateBy { it.uri }
        val keptUris = items.mapTo(HashSet()) { it.uri }
        val stale = if (result.complete) existing.keys.filter { it !in keptUris } else emptyList()
        val merged = items.map { fresh ->
            // 기존 행이 있으면 addedAt(정렬 기준)을 보존
            val prior = existing[fresh.uri]
            val ud = userData[fresh.uri]
            val base = fresh.copy(
                // 수동 연결(user_data)이 자동 매칭보다 우선 — 재스캔이 사용자가 고른 스크립트를 덮지 않는다.
                // 기존 행의 값은 user_data에 백업되기 전(구버전)의 수동 연결일 때만 이어받는다.
                // 이 폴더에서 자동 매칭됐던 값이면 이번 스캔 결과가 정답이므로 버린다(지워진 스크립트가 남지 않게).
                funscriptUri = ud?.funscriptUri
                    ?: fresh.funscriptUri
                    ?: prior?.funscriptUri?.takeUnless { isScannedUnder(it, uriStr) },
                addedAt = prior?.addedAt ?: fresh.addedAt,
                // 스캔은 길이·해상도를 채우지 않으므로(파일을 열어야 함)
                // 썸네일 생성·재생 때 확보해 둔 기존 값을 덮어쓰지 않는다.
                durationMs = prior?.durationMs?.takeIf { it > 0 } ?: fresh.durationMs,
                width = prior?.width?.takeIf { it > 0 } ?: fresh.width,
                height = prior?.height?.takeIf { it > 0 } ?: fresh.height,
            )
            if (ud != null) {
                base.copy(
                    rating = ud.rating,
                    favorite = ud.favorite,
                    tags = ud.tags,
                    lastPositionMs = ud.lastPositionMs,
                    customTitle = ud.customTitle,
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
        val removed = stale + evicted
        db.withTransaction {
            // SQLite 바인딩 변수 한도(999) 아래로 나눠 지운다.
            removed.chunked(500).forEach { videoDao.deleteAll(it) }
            videoDao.upsertAll(merged)
        }
        removed.forEach { ThumbnailCache.remove(context, it) }
        return merged.size
    }

    suspend fun rescanAll(
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): Int {
        var total = 0
        folderDao.getAll().forEach { folder ->
            // 진행률은 폴더별 개수가 아니라 전체 누적으로 보여준다.
            val base = total
            total += scanFolder(folder.uri, matchScripts, multiExt) { onProgress(base + it) }
        }
        return total
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
        val previous = videoDao.get(uri)?.funscriptUri
        // videos(표시용) + user_data(영속 백업)에 함께 기록 → 폴더 재등록 시 수동 연결 복원
        db.withTransaction {
            videoDao.setFunscript(uri, scriptUri)
            userDataDao.ensure(VideoUserDataEntity(uri))
            userDataDao.updateFunscript(uri, scriptUri)
        }
        if (previous != null && previous != scriptUri) releaseScriptPermission(previous)
    }

    /**
     * 더 이상 어느 영상도 쓰지 않는 수동 연결 스크립트의 영구 읽기 권한을 반납한다.
     * 영구 권한은 앱당 개수 한도(구버전 안드로이드 128개)가 있어, 연결을 바꿀 때마다 쌓이면
     * 결국 새로 연결한 스크립트가 권한을 못 받아 앱 재시작 후 읽히지 않게 된다.
     * 폴더 트리로 얻은 권한(자동 매칭 스크립트)은 목록에 개별로 없으므로 건드리지 않는다.
     */
    private suspend fun releaseScriptPermission(scriptUri: String) {
        if (SmbManager.isSmb(scriptUri)) return
        if (videoDao.countByFunscript(scriptUri) > 0 || userDataDao.countByFunscript(scriptUri) > 0) return
        val u = Uri.parse(scriptUri)
        val resolver = context.contentResolver
        if (resolver.persistedUriPermissions.none { it.uri == u }) return
        runCatching { resolver.releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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

    /** 라이브러리 기본 순서에서 바로 앞/뒤 영상 URI (이전, 다음). */
    suspend fun getAdjacentUris(video: VideoEntity): Pair<String?, String?> =
        videoDao.getPrevUri(video.addedAt, video.title, video.uri) to
            videoDao.getNextUri(video.addedAt, video.title, video.uri)

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
        // 등록된 폴더가 모두 사라졌으니 열려 있던 SMB 연결도 닫는다.
        runCatching { smb.release() }
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
