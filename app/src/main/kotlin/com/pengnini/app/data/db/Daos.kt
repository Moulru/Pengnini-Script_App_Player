package com.pengnini.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE uri = :uri")
    suspend fun get(uri: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE folderUri = :folderUri")
    suspend fun getByFolder(folderUri: String): List<VideoEntity>

    /** 하위 폴더 단위 조회. bucketUri가 없는 구버전 행은 folderUri로 폴백해 잡는다. */
    @Query(
        """
        SELECT * FROM videos
        WHERE COALESCE(bucketUri, folderUri) = :bucketKey
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun observeByBucket(bucketKey: String): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: VideoEntity)

    @Query("DELETE FROM videos WHERE folderUri = :folderUri")
    suspend fun deleteByFolder(folderUri: String)

    @Query("UPDATE videos SET rating = :rating WHERE uri = :uri")
    suspend fun setRating(uri: String, rating: Int)

    @Query("UPDATE videos SET favorite = :favorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, favorite: Boolean)

    @Query("UPDATE videos SET tags = :tags WHERE uri = :uri")
    suspend fun setTags(uri: String, tags: String)

    @Query("UPDATE videos SET lastPositionMs = :positionMs WHERE uri = :uri")
    suspend fun setLastPosition(uri: String, positionMs: Long)

    @Query("UPDATE videos SET customTitle = :title WHERE uri = :uri")
    suspend fun setCustomTitle(uri: String, title: String?)

    @Query("UPDATE videos SET funscriptUri = :scriptUri WHERE uri = :uri")
    suspend fun setFunscript(uri: String, scriptUri: String?)

    @Query("UPDATE videos SET durationMs = :durationMs, width = :width, height = :height WHERE uri = :uri")
    suspend fun updateMediaInfo(uri: String, durationMs: Long, width: Int, height: Int)

    @Query("DELETE FROM videos WHERE uri = :uri")
    suspend fun delete(uri: String)

    /**
      * 라이브러리 기본 순서(추가순 내림차순 → 제목 → uri)에서 바로 앞/뒤 영상.
      * 전체 URI 목록을 메모리에 올리지 않고 한 건만 가져온다.
      *
      * uri까지 비교하는 이유: 한 폴더를 한 번에 스캔하면 addedAt이 모두 같고,
      * 하위 폴더가 나뉘면서 같은 파일명(`2023/clip.mp4`·`2024/clip.mp4`)이 흔해졌다.
      * (addedAt, title)만으로는 동점인 영상들이 서로 건너뛰어져 목록에 보이는데도 도달할 수 없다.
      */
    @Query(
        """
        SELECT uri FROM videos
        WHERE addedAt > :addedAt
           OR (addedAt = :addedAt AND title COLLATE NOCASE < :title)
           OR (addedAt = :addedAt AND title COLLATE NOCASE = :title AND uri < :uri)
        ORDER BY addedAt ASC, title COLLATE NOCASE DESC, uri DESC
        LIMIT 1
        """,
    )
    suspend fun getPrevUri(addedAt: Long, title: String, uri: String): String?

    @Query(
        """
        SELECT uri FROM videos
        WHERE addedAt < :addedAt
           OR (addedAt = :addedAt AND title COLLATE NOCASE > :title)
           OR (addedAt = :addedAt AND title COLLATE NOCASE = :title AND uri > :uri)
        ORDER BY addedAt DESC, title COLLATE NOCASE ASC, uri ASC
        LIMIT 1
        """,
    )
    suspend fun getNextUri(addedAt: Long, title: String, uri: String): String?

    @Query("DELETE FROM videos")
    suspend fun clear()
}

@Dao
interface VideoUserDataDao {
    @Query("SELECT * FROM video_user_data WHERE uri = :uri")
    suspend fun get(uri: String): VideoUserDataEntity?

    @Query("SELECT * FROM video_user_data")
    suspend fun getAll(): List<VideoUserDataEntity>

    /** 행이 없으면 기본값으로 생성, 있으면 그대로 둔다(부분 UPDATE 전 보장용). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(row: VideoUserDataEntity)

    @Query("UPDATE video_user_data SET rating = :rating WHERE uri = :uri")
    suspend fun updateRating(uri: String, rating: Int)

    @Query("UPDATE video_user_data SET favorite = :favorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, favorite: Boolean)

    @Query("UPDATE video_user_data SET tags = :tags WHERE uri = :uri")
    suspend fun updateTags(uri: String, tags: String)

    @Query("UPDATE video_user_data SET lastPositionMs = :positionMs WHERE uri = :uri")
    suspend fun updateLastPosition(uri: String, positionMs: Long)

    @Query("UPDATE video_user_data SET customTitle = :title WHERE uri = :uri")
    suspend fun updateCustomTitle(uri: String, title: String?)

    @Query("UPDATE video_user_data SET funscriptUri = :scriptUri WHERE uri = :uri")
    suspend fun updateFunscript(uri: String, scriptUri: String?)

    @Query("DELETE FROM video_user_data WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM video_user_data")
    suspend fun clear()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Query("UPDATE folders SET lastScannedAt = :at WHERE uri = :uri")
    suspend fun setLastScannedAt(uri: String, at: Long)

    @Query("DELETE FROM folders WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM folders")
    suspend fun clear()
}
