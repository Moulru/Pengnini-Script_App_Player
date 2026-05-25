package com.pengnini.app.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pengnini.app.data.db.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri, matchScripts: Boolean = true): List<VideoEntity> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val videos = mutableListOf<DocumentFile>()
        val scripts = mutableMapOf<String, Uri>()
        val subs = mutableMapOf<String, Uri>()

        walk(tree) { file ->
            val name = file.name ?: return@walk
            val lower = name.lowercase()
            val base = name.substringBeforeLast('.', name).lowercase()
            when {
                VIDEO_EXTS.any { lower.endsWith(it) } -> videos.add(file)
                matchScripts && lower.endsWith(".funscript") -> scripts[base] = file.uri
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
                funscriptUri = scripts[base]?.toString(),
                subtitleUri = subs[base]?.toString(),
                mimeType = v.type,
            )
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
