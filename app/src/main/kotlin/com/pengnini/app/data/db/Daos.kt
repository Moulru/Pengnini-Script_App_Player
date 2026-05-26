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

    @Query("SELECT uri FROM videos ORDER BY addedAt DESC")
    suspend fun getAllUrisOrdered(): List<String>

    @Query("DELETE FROM videos")
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
