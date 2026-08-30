package com.adnanfoisal.infinitydesign.design.validation

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument

/**
 * Schema migrations. Each future schema bump lives in [migrations]; older
 * projects are migrated forward and never silently destroyed (section 32).
 *
 * A migration is (currentVersion) -> Int (newVersion) with a transformation that
 * operates on the raw decoded document. We deliberately use *reserialization*
 * rather than mutating live objects, so we never break in-flight editor state.
 */
object DesignDocumentMigrator {

    private const val LATEST = 1
    private val migrations: Map<Int, (DesignDocument) -> DesignDocument> = mapOf(
        // No migrations yet — version 1 is the first release.
    )

    fun migrate(doc: DesignDocument): AppResult<DesignDocument> {
        var current = doc
        var v = current.schemaVersion
        if (v > LATEST) {
            return errResult(
                AppError.Kind.SchemaMigration,
                "Document version $v is newer than supported latest $LATEST",
            )
        }
        var safety = 0
        while (v < LATEST && safety++ < 32) {
            val fn = migrations[v]
                ?: return errResult(AppError.Kind.SchemaMigration, "No migration from v$v")
            current = try {
                fn(current)
            } catch (t: Throwable) {
                return errResult(
                    AppError.Kind.SchemaMigration,
                    "Migration v$v failed: ${t.message ?: t.javaClass.simpleName}",
                    t,
                )
            }
            current = current.copy(schemaVersion = v + 1)
            v = current.schemaVersion
        }
        if (v != LATEST) {
            return errResult(AppError.Kind.SchemaMigration, "Migration stuck at v$v")
        }
        return okResult(current)
    }

    fun latestVersion(): Int = LATEST
}
