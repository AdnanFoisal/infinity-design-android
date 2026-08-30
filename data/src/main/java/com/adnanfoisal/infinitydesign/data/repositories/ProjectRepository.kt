package com.adnanfoisal.infinitydesign.data.repositories

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.data.database.BlueprintCacheDao
import com.adnanfoisal.infinitydesign.data.database.BlueprintCacheEntity
import com.adnanfoisal.infinitydesign.data.database.ProjectDao
import com.adnanfoisal.infinitydesign.data.database.ProjectEntity
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocumentCodec
import com.adnanfoisal.infinitydesign.design.validation.DesignDocumentMigrator
import com.adnanfoisal.infinitydesign.design.validation.DesignValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/**
 * Repository for projects. Section 32/33: handles save/load/import/export,
 * migration, corruption recovery — never crash.
 *
 * All heavy I/O on Dispatchers.IO. Returns [AppResult] to keep callers informed
 * of structured failure modes (storage unavailable, schema mismatch, corrupt JSON).
 */
class ProjectRepository(
    private val dao: ProjectDao,
    private val blueprintDao: BlueprintCacheDao,
) {

    fun observeSummaries(): Flow<List<com.adnanfoisal.infinitydesign.data.database.ProjectSummary>> =
        dao.observeSummaries()

    suspend fun save(document: DesignDocument, blueprint: DesignBlueprint? = null, prompt: String? = null): AppResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val id = document.id
                val now = System.currentTimeMillis()
                val json = DesignDocumentCodec.encode(document)
                val bpJson = blueprint?.let { DesignDocumentCodec.encodeBlueprint(it) }
                dao.upsert(ProjectEntity(
                    id = id,
                    name = document.name,
                    createdAt = if (document.metadata.createdAt == 0L) now else document.metadata.createdAt,
                    updatedAt = now,
                    schemaVersion = document.schemaVersion,
                    documentJson = json,
                    blueprintJson = bpJson,
                    prompt = prompt ?: document.metadata.notes,
                    seed = document.seed,
                    thumbnail = null,
                ))
                okResult(id)
            } catch (e: Throwable) {
                errResult(AppError.Kind.StorageUnavailable, "Failed to save: ${e.message}", e)
            }
        }

    suspend fun load(id: String): AppResult<ProjectLoaded> = withContext(Dispatchers.IO) {
        try {
            val e = dao.get(id) ?: return@withContext errResult(AppError.Kind.NotFound, "Project $id not found")
            val raw = DesignDocumentCodec.decode(e.documentJson)
            val migrated = when (raw) {
                is AppResult.Ok -> DesignDocumentMigrator.migrate(raw.value)
                is AppResult.Err -> raw
            }
            if (migrated is AppResult.Err) return@withContext migrated
            val validated = DesignValidator.validate((migrated as AppResult.Ok).value)
            if (validated is AppResult.Err) return@withContext validated
            val bp = e.blueprintJson?.let { DesignDocumentCodec.decodeBlueprint(it).getOrNull() }
            okResult(ProjectLoaded((validated as AppResult.Ok).value, bp, e.prompt, e.seed))
        } catch (e: Throwable) {
            errResult(AppError.Kind.CorruptProject, "Failed to load: ${e.message}", e)
        }
    }

    suspend fun delete(id: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteById(id)
            okResult(Unit)
        } catch (e: Throwable) {
            errResult(AppError.Kind.StorageUnavailable, "Failed to delete: ${e.message}", e)
        }
    }

    suspend fun rename(id: String, name: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.rename(id, name, System.currentTimeMillis())
            okResult(Unit)
        } catch (e: Throwable) {
            errResult(AppError.Kind.StorageUnavailable, "Failed to rename: ${e.message}", e)
        }
    }

    suspend fun saveThumbnail(id: String, thumbnail: ByteArray): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.saveThumbnail(id, thumbnail, System.currentTimeMillis())
            okResult(Unit)
        } catch (e: Throwable) {
            errResult(AppError.Kind.StorageUnavailable, "Failed to save thumbnail: ${e.message}", e)
        }
    }

    suspend fun cacheBlueprint(blueprint: DesignBlueprint, prompt: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val hash = sha256(prompt)
                blueprintDao.upsert(BlueprintCacheEntity(
                    blueprintId = blueprint.id,
                    promptHash = hash,
                    blueprintJson = DesignDocumentCodec.encodeBlueprint(blueprint),
                    createdAt = System.currentTimeMillis(),
                ))
                okResult(Unit)
            } catch (e: Throwable) {
                errResult(AppError.Kind.StorageUnavailable, "Failed to cache blueprint", e)
            }
        }

    suspend fun findCachedBlueprints(prompt: String, limit: Int = 5): List<DesignBlueprint> =
        withContext(Dispatchers.IO) {
            val hash = sha256(prompt)
            blueprintDao.findByPrompt(hash, limit).mapNotNull { entity ->
                DesignDocumentCodec.decodeBlueprint(entity.blueprintJson).getOrNull()
            }
        }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

data class ProjectLoaded(
    val document: DesignDocument,
    val blueprint: DesignBlueprint?,
    val prompt: String?,
    val seed: Long,
)
