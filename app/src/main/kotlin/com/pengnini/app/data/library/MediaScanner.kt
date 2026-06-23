package com.pengnini.app.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pengnini.app.data.db.VideoEntity
import com.pengnini.app.data.smb.SmbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(private val context: Context, private val smb: SmbManager) {

    suspend fun scan(
        treeUri: Uri,
        matchScripts: Boolean = true,
        multiExt: Boolean = false,
    ): List<VideoEntity> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val videos = mutableListOf<DocumentFile>()
        val scripts = mutableMapOf<String, Uri>()     // .funscript (우선)
        val altScripts = mutableMapOf<String, Uri>()  // .json (multiExt 시 보조)
        val subs = mutableMapOf<String, Uri>()

        walk(tree) { file ->
            val name = file.name ?: return@walk
            // 사이드카 메타데이터 파일(.pengnini.json / .pengnini.tmp)은 스크립트로 오인 안 되게 제외
            if (name.startsWith(".pengnini", ignoreCase = true)) return@walk
            val lower = name.lowercase()
            val base = name.substringBeforeLast('.', name).lowercase()
            when {
                VIDEO_EXTS.any { lower.endsWith(it) } -> videos.add(file)
                matchScripts && lower.endsWith(".funscript") -> scripts[base] = file.uri
                matchScripts && multiExt && lower.endsWith(".json") -> altScripts[base] = file.uri
                SUB_EXTS.any { lower.endsWith(it) } -> subs[base] = file.uri
            }
        }

        videos.mapNotNull { v ->
            val name = v.name ?: return@mapNotNull null
            val base = name.substringBeforeLast('.', name).lowercase()
            val displayTitle = name.substringBeforeLast('.', name)
            val meta = runCatching { extractMetadata(v.uri) }.getOrNull() ?: VideoMetadata()
            VideoEntity(
                uri = v.uri.toString(),
                folderUri = treeUri.toString(),
                title = displayTitle,
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                sizeBytes = v.length(),
                addedAt = System.currentTimeMillis(),
                funscriptUri = (scripts[base] ?: altScripts[base])?.toString(),
                subtitleUri = subs[base]?.toString(),
                mimeType = v.type,
            )
        }
    }

    /** SMB 네트워크 폴더 스캔. 길이·해상도는 네트워크 비용 때문에 0으로 두고 재생 시 채운다. */
    suspend fun scanSmb(folderUri: String, multiExt: Boolean = false): List<VideoEntity> =
        withContext(Dispatchers.IO) {
            val videos = mutableListOf<Pair<String, SmbManager.SmbEntry>>()
            val scripts = HashMap<String, String>()
            val altScripts = HashMap<String, String>()
            val subs = HashMap<String, String>()

            walkSmb(folderUri) { childUri, entry ->
                val name = entry.name
                if (name.startsWith(".pengnini", ignoreCase = true)) return@walkSmb
                val lower = name.lowercase()
                val base = name.substringBeforeLast('.', name).lowercase()
                when {
                    VIDEO_EXTS.any { lower.endsWith(it) } -> videos += childUri to entry
                    lower.endsWith(".funscript") -> scripts[base] = childUri
                    multiExt && lower.endsWith(".json") -> altScripts[base] = childUri
                    SUB_EXTS.any { lower.endsWith(it) } -> subs[base] = childUri
                }
            }

            videos.map { (fileUri, entry) ->
                val base = entry.name.substringBeforeLast('.', entry.name).lowercase()
                VideoEntity(
                    uri = fileUri,
                    folderUri = folderUri,
                    title = entry.name.substringBeforeLast('.', entry.name),
                    durationMs = 0L,
                    width = 0,
                    height = 0,
                    sizeBytes = entry.size,
                    addedAt = System.currentTimeMillis(),
                    funscriptUri = scripts[base] ?: altScripts[base],
                    subtitleUri = subs[base],
                    mimeType = null,
                )
            }
        }

    private fun walkSmb(folderUri: String, visit: (String, SmbManager.SmbEntry) -> Unit) {
        val entries = runCatching { smb.list(folderUri) }.getOrDefault(emptyList())
        entries.forEach { entry ->
            val childUri = SmbManager.childUri(folderUri, entry.name)
            if (entry.isDirectory) walkSmb(childUri, visit) else visit(childUri, entry)
        }
    }

    private fun walk(file: DocumentFile, visit: (DocumentFile) -> Unit) {
        if (file.isFile) {
            visit(file)
        } else if (file.isDirectory) {
            file.listFiles().forEach { walk(it, visit) }
        }
    }

    private fun extractMetadata(uri: Uri): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            )
        } catch (e: Exception) {
            VideoMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    data class VideoMetadata(
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )

    companion object {
        private val VIDEO_EXTS = listOf(".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi", ".ts", ".flv")
        private val SUB_EXTS = listOf(".srt", ".vtt", ".ass", ".ssa")
    }
}
