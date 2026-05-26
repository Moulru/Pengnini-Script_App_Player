package com.pengnini.app.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pengnini.app.data.db.FolderDao
import com.pengnini.app.data.db.FolderEntity
import com.pengnini.app.data.db.VideoDao
import com.pengnini.app.data.db.VideoEntity
import kotlinx.coroutines.flow.Flow

enum class LibraryViewMode { GRID, LIST }

class LibraryRepository(
    private val folderDao: FolderDao,
    private val videoDao: VideoDao,
    private val scanner: MediaScanner,
    private val context: Context,
) {
    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()
    val videos: Flow<List<VideoEntity>> = videoDao.observeAll()

    suspend fun addFolder(uri: Uri, matchScripts: Boolean = true) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val displayName = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()
        folderDao.insert(FolderEntity(uri.toString(), displayName))
        scanFolder(uri.toString(), matchScripts)
    }

    suspend fun removeFolder(uriStr: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriStr),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        folderDao.delete(uriStr)
        videoDao.deleteByFolder(uriStr)
    }

    suspend fun scanFolder(uriStr: String, matchScripts: Boolean = true) {
        val uri = Uri.parse(uriStr)
        val items = scanner.scan(uri, matchScripts)
        if (items.isEmpty()) return
        // 재스캔 시 사용자 데이터(rating·favorite·tags·lastPositionMs·addedAt) 보존.
        // OnConflictStrategy.REPLACE가 전체 row를 덮어쓰므로 기존 값을 명시적으로 유지한다.
        val existing = videoDao.getByFolder(uriStr).associateBy { it.uri }
        val merged = items.map { fresh ->
            existing[fresh.uri]?.let { e ->
                fresh.copy(
                    rating = e.rating,
                    favorite = e.favorite,
                    tags = e.tags,
                    lastPositionMs = e.lastPositionMs,
                    addedAt = e.addedAt,
                )
            } ?: fresh
        }
        videoDao.upsertAll(merged)
    }

    suspend fun rescanAll(matchScripts: Boolean = true) {
        folderDao.getAll().forEach { scanFolder(it.uri, matchScripts) }
    }

    suspend fun setRating(uri: String, rating: Int) = videoDao.setRating(uri, rating)
    suspend fun setFavorite(uri: String, favorite: Boolean) = videoDao.setFavorite(uri, favorite)
    suspend fun setTags(uri: String, tags: String) = videoDao.setTags(uri, tags)
    suspend fun setLastPosition(uri: String, positionMs: Long) = videoDao.setLastPosition(uri, positionMs)
    suspend fun getVideo(uri: String): VideoEntity? = videoDao.get(uri)
    suspend fun getAllVideoUrisOrdered(): List<String> = videoDao.getAllUrisOrdered()
    suspend fun clearLibrary() {
        videoDao.clear()
        folderDao.clear()
    }
}
