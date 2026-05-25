package com.pengnini.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val uri: String,
    val folderUri: String,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val addedAt: Long,
    val funscriptUri: String?,
    val subtitleUri: String?,
    val rating: Int = 0,
    val favorite: Boolean = false,
    val tags: String = "",
    val mimeType: String? = null,
    val lastPositionMs: Long = 0L,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
