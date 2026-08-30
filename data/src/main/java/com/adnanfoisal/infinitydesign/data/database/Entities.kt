package com.adnanfoisal.infinitydesign.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int,
    val documentJson: String,
    val blueprintJson: String? = null,
    val prompt: String? = null,
    val seed: Long = 0L,
    val thumbnail: ByteArray? = null,
)

@Entity(tableName = "blueprint_cache")
data class BlueprintCacheEntity(
    @PrimaryKey val blueprintId: String,
    val promptHash: String,
    val blueprintJson: String,
    val createdAt: Long,
)
