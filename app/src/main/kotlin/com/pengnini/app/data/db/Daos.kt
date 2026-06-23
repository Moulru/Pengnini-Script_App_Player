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

    @Query("SELECT uri FROM videos ORDER BY addedAt DESC")
    suspend fun getAllUrisOrdered(): List<String>

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

    @Query("DELETE FROM folders WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM folders")
    suspend fun clear()
}
