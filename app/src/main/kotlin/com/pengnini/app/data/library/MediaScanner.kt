package com.pengnini.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.smb.SmbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 등록한 폴더를 재귀 순회해 영상·스크립트·자막을 수집한다.
 *
 * - 폴더 분류는 **영상이 실제로 들어 있는 디렉토리** 단위(`bucketUri`/`bucketPath`)다.
 *   최상위 폴더 하나만 등록해도 하위 폴더가 각각 별도 분류로 잡힌다.
 * - 스캔은 MediaStore를 쓰지 않고 SAF(DocumentsContract)로 직접 순회하므로
 *   **`.nomedia`가 있어도 그대로 스캔한다.** 숨김 파일·숨김 폴더도 건너뛰지 않는다.
 */
class MediaScanner(private val context: Context, private val smb: SmbManager) {

    /** 스캔 중 만난 파일 하나. 이름·크기·MIME을 커서에서 한 번에 읽어 온다. */
    private data class Entry(
        val name: String,
        val uri: String,
        val size: Long,
        val mimeType: String?,
        /** 이 파일이 들어 있는 디렉토리 URI */
        val dirUri: String,
        /** 등록한 폴더 기준 디렉토리 상대 경로. 루트 바로 아래면 "" */
        val dirPath: String,
    )

    /**
     * 같은 basename의 사이드카(스크립트·자막) 수집기.
     *
     * 1순위는 영상과 **같은 폴더**. 없으면 트리 전체에서 찾는다
     * (스크립트를 따로 모아 두는 사용자를 위한 기존 동작 호환).
     * 단 트리 안에 같은 이름이 둘 이상이면 어느 것이 맞는지 알 수 없으므로 폴백하지 않는다 —
     * 하위 폴더가 나뉘면서 `A/intro.mp4`가 `B/intro.funscript`를 잘못 물어 가는 걸 막는다.
     */
    private class SidecarIndex {
        private val sameDir = HashMap<Pair<String, String>, String>()
        private val anywhere = HashMap<String, String>()
        private val ambiguous = HashSet<String>()

        fun put(dirUri: String, base: String, uri: String) {
            sameDir[dirUri to base] = uri
            val prev = anywhere.put(base, uri)
            if (prev != null && prev != uri) ambiguous.add(base)
        }

        fun find(dirUri: String, base: String): String? =
            sameDir[dirUri to base] ?: anywhere[base]?.takeIf { base !in ambiguous }
    }

    suspend fun scan(
        treeUri: Uri,
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): List<VideoEntity> = withContext(Dispatchers.IO) {
        val ctx = currentCoroutineContext()
        val videos = mutableListOf<Entry>()
        val scripts = SidecarIndex()     // .funscript (우선)
        val altScripts = SidecarIndex()  // .json (multiExt 시 보조)
        val subs = SidecarIndex()

        walkTree(treeUri, ctx) { entry ->
            val name = entry.name
            // 사이드카 메타데이터 파일(.pengnini.json / .pengnini.tmp)은 스크립트로 오인 안 되게 제외
            if (name.startsWith(".pengnini", ignoreCase = true)) return@walkTree
            val lower = name.lowercase()
            val base = name.substringBeforeLast('.', name).lowercase()
            when {
                VIDEO_EXTS.any { lower.endsWith(it) } -> {
                    videos.add(entry)
                    onProgress(videos.size)
                }
                matchScripts && lower.endsWith(".funscript") -> scripts.put(entry.dirUri, base, entry.uri)
                matchScripts && multiExt && lower.endsWith(".json") -> altScripts.put(entry.dirUri, base, entry.uri)
                SUB_EXTS.any { lower.endsWith(it) } -> subs.put(entry.dirUri, base, entry.uri)
            }
        }

        val now = System.currentTimeMillis()
        // 발견 순서를 1ms씩 어긋나게 담는다. 전부 같은 값이면 '추가순' 정렬이
        // 동점 처리(제목순)로 떨어져 사실상 제목순과 구분이 안 된다.
        videos.mapIndexed { index, v ->
            val base = v.name.substringBeforeLast('.', v.name).lowercase()
            VideoEntity(
                uri = v.uri,
                folderUri = treeUri.toString(),
                title = v.name.substringBeforeLast('.', v.name),
                // 길이·해상도는 파일을 하나씩 열어야 해서 스캔에서 제외한다.
                // 썸네일 생성(ThumbnailCache) 또는 첫 재생 때 채워진다.
                durationMs = 0L,
                width = 0,
                height = 0,
                sizeBytes = v.size,
                addedAt = now + index,
                funscriptUri = scripts.find(v.dirUri, base) ?: altScripts.find(v.dirUri, base),
                subtitleUri = subs.find(v.dirUri, base),
                mimeType = v.mimeType,
                bucketUri = v.dirUri,
                bucketPath = v.dirPath,
            )
        }
    }

    /** SMB 네트워크 폴더 스캔. 길이·해상도는 네트워크 비용 때문에 0으로 두고 재생 시 채운다. */
    suspend fun scanSmb(
        folderUri: String,
        multiExt: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): List<VideoEntity> = withContext(Dispatchers.IO) {
        val ctx = currentCoroutineContext()
        val videos = mutableListOf<Pair<Entry, SmbManager.SmbEntry>>()
        val scripts = SidecarIndex()
        val altScripts = SidecarIndex()
        val subs = SidecarIndex()

        walkSmb(folderUri, folderUri, "") { childUri, dirUri, dirPath, entry ->
            ctx.ensureActive()
            val name = entry.name
            if (name.startsWith(".pengnini", ignoreCase = true)) return@walkSmb
            val lower = name.lowercase()
            val base = name.substringBeforeLast('.', name).lowercase()
            when {
                VIDEO_EXTS.any { lower.endsWith(it) } -> {
                    videos += Entry(name, childUri, entry.size, null, dirUri, dirPath) to entry
                    onProgress(videos.size)
                }
                lower.endsWith(".funscript") -> scripts.put(dirUri, base, childUri)
                multiExt && lower.endsWith(".json") -> altScripts.put(dirUri, base, childUri)
                SUB_EXTS.any { lower.endsWith(it) } -> subs.put(dirUri, base, childUri)
            }
        }

        val now = System.currentTimeMillis()
        videos.mapIndexed { index, (v, entry) ->
            val base = entry.name.substringBeforeLast('.', entry.name).lowercase()
            VideoEntity(
                uri = v.uri,
                folderUri = folderUri,
                title = entry.name.substringBeforeLast('.', entry.name),
                durationMs = 0L,
                width = 0,
                height = 0,
                sizeBytes = entry.size,
                addedAt = now + index,
                funscriptUri = scripts.find(v.dirUri, base) ?: altScripts.find(v.dirUri, base),
                subtitleUri = subs.find(v.dirUri, base),
                mimeType = null,
                bucketUri = v.dirUri,
                bucketPath = v.dirPath,
            )
        }
    }

    private fun walkSmb(
        folderUri: String,
        dirUri: String,
        dirPath: String,
        depth: Int = 0,
        visit: (childUri: String, dirUri: String, dirPath: String, entry: SmbManager.SmbEntry) -> Unit,
    ) {
        if (depth > MAX_SCAN_DEPTH) return // 심링크/DFS 순환에서 무한 재귀(StackOverflow) 방지
        val entries = runCatching { smb.list(dirUri) }.getOrDefault(emptyList())
        entries.forEach { entry ->
            val childUri = SmbManager.childUri(dirUri, entry.name)
            if (entry.isDirectory) {
                val childPath = if (dirPath.isEmpty()) entry.name else "$dirPath/${entry.name}"
                walkSmb(folderUri, childUri, childPath, depth + 1, visit)
            } else {
                visit(childUri, dirUri, dirPath, entry)
            }
        }
    }

    /**
     * SAF 트리를 DocumentsContract 커서로 순회한다.
     * DocumentFile은 name·size·type을 각각 별도 ContentResolver 쿼리로 가져와서
     * 파일 수에 비례해 IPC가 폭증한다. 여기서는 디렉토리당 쿼리 1회로 끝낸다.
     * `.nomedia`·숨김 파일을 걸러내지 않는다(MediaStore를 거치지 않으므로 영향도 없다).
     */
    private fun walkTree(treeUri: Uri, ctx: CoroutineContext, visit: (Entry) -> Unit) {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return
        // (documentId, 깊이, 루트 기준 상대 경로)
        val queue = ArrayDeque<Triple<String, Int, String>>()
        queue.add(Triple(rootId, 0, ""))

        while (queue.isNotEmpty()) {
            ctx.ensureActive()
            val (docId, depth, relPath) = queue.removeFirst()
            if (depth > MAX_SCAN_DEPTH) continue
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            // 쿼리 실패(권한 해제된 하위 폴더 등)는 건너뛰되, 취소 예외는 그대로 전파한다.
            val cursor = runCatching {
                resolver.query(childrenUri, DOC_PROJECTION, null, null, null)
            }.getOrNull() ?: continue
            cursor.use { c ->
                while (c.moveToNext()) {
                    ctx.ensureActive()
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childPath = if (relPath.isEmpty()) name else "$relPath/$name"
                        queue.add(Triple(id, depth + 1, childPath))
                    } else {
                        visit(
                            Entry(
                                name = name,
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                                size = if (c.isNull(3)) 0L else c.getLong(3),
                                mimeType = mime,
                                dirUri = dirUri,
                                dirPath = relPath,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_SCAN_DEPTH = 16
        private val VIDEO_EXTS = listOf(".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi", ".ts", ".flv")
        private val SUB_EXTS = listOf(".srt", ".vtt", ".ass", ".ssa")
        private val DOC_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
