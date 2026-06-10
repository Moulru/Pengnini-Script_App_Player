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
    /** 사용자가 지정한 표시 이름. null이면 파일명 기반 title 사용. 파일 자체는 변경하지 않음. */
    val customTitle: String? = null,
)

/** 라이브러리·플레이어에서 보여줄 제목. customTitle이 있으면 우선. */
val VideoEntity.displayTitle: String
    get() = customTitle?.takeIf { it.isNotBlank() } ?: title

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * 사용자 메타데이터의 영속 저장소(uri PK). videos 테이블과 분리돼 있어
 * 폴더 제거→재등록·캐시 삭제에도 보존되며, 스캔 시 videos로 다시 복원된다.
 */
@Entity(tableName = "video_user_data")
data class VideoUserDataEntity(
    @PrimaryKey val uri: String,
    val rating: Int = 0,
    val favorite: Boolean = false,
    val tags: String = "",
    val lastPositionMs: Long = 0L,
    val customTitle: String? = null,
)
