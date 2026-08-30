package com.adnanfoisal.infinitydesign.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT id, name, createdAt, updatedAt, schemaVersion, seed, thumbnail FROM projects ORDER BY updatedAt DESC")
    fun observeSummaries(): Flow<List<ProjectSummary>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET documentJson = :json, updatedAt = :updatedAt WHERE id = :id")
    suspend fun saveDocument(id: String, json: String, updatedAt: Long)

    @Query("UPDATE projects SET thumbnail = :thumb, updatedAt = :updatedAt WHERE id = :id")
    suspend fun saveThumbnail(id: String, thumb: ByteArray, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int
}

@Dao
interface BlueprintCacheDao {
    @Query("SELECT * FROM blueprint_cache WHERE blueprintId = :id LIMIT 1")
    suspend fun get(id: String): BlueprintCacheEntity?

    @Query("SELECT * FROM blueprint_cache WHERE promptHash = :hash ORDER BY createdAt DESC LIMIT :limit")
    suspend fun findByPrompt(hash: String, limit: Int = 5): List<BlueprintCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BlueprintCacheEntity)

    @Query("DELETE FROM blueprint_cache WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM blueprint_cache")
    suspend fun count(): Int
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int,
    val seed: Long,
    val thumbnail: ByteArray? = null,
)
