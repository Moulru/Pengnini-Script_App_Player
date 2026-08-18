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
    /**
     * 영상이 실제로 들어 있는 디렉토리 URI(SAF document URI 또는 SMB 폴더 URI).
     * folderUri(등록한 최상위 폴더)와 달리 하위 폴더까지 구분한다.
     * null = 이 컬럼이 생기기 전에 스캔된 행 — 재스캔하면 채워진다.
     */
    val bucketUri: String? = null,
    /** 등록한 폴더 기준 상대 경로(예: `여행/2024`). 루트 바로 아래면 빈 문자열. */
    val bucketPath: String? = null,
)

/** 라이브러리·플레이어에서 보여줄 제목. customTitle이 있으면 우선. */
val VideoEntity.displayTitle: String
    get() = customTitle?.takeIf { it.isNotBlank() } ?: title

/**
 * 폴더 분류 키. 하위 폴더별로 나뉜다.
 * 재스캔 전 구버전 행은 bucketUri가 없으므로 등록한 폴더를 그대로 쓴다.
 */
val VideoEntity.bucketKey: String
    get() = bucketUri ?: folderUri

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /** 마지막으로 스캔을 끝낸 시각. 한 번도 안 했으면 null. */
    val lastScannedAt: Long? = null,
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
    /** 수동 연결한 스크립트 URI. 폴더 제거→재등록 시 복원용(자동 매칭은 스캔이 다시 채움). */
    val funscriptUri: String? = null,
)
