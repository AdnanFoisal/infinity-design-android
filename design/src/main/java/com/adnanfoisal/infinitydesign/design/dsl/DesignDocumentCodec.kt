package com.adnanfoisal.infinitydesign.design.dsl

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import kotlinx.serialization.json.Json

/**
 * Single source of truth for JSON encoding/decoding of design documents.
 *
 * - `ignoreUnknownKeys` so future schema versions can include new fields
 *   without breaking older builds (forward compatibility).
 * - `encodeDefaults` so round-tripping preserves intent.
 * - `explicitNulls = false` so absent fields are not emitted.
 *
 * Section 32 of the spec: import must recover gracefully from partially
 * corrupted documents.
 */
object DesignDocumentCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        classDiscriminator = "type"
        isLenient = false
    }

    fun encode(doc: DesignDocument): String = json.encodeToString(DesignDocument.serializer(), doc)

    fun decode(input: String): AppResult<DesignDocument> {
        if (input.isBlank()) {
            return errResult(AppError.Kind.SchemaValidation, "Empty document")
        }
        return try {
            val parsed = json.decodeFromString(DesignDocument.serializer(), input)
            okResult(parsed)
        } catch (e: Throwable) {
            errResult(AppError.Kind.SchemaValidation, "Decode failure: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    fun encodeBlueprint(bp: DesignBlueprint): String =
        json.encodeToString(DesignBlueprint.serializer(), bp)

    fun decodeBlueprint(input: String): AppResult<DesignBlueprint> {
        if (input.isBlank()) {
            return errResult(AppError.Kind.SchemaValidation, "Empty blueprint")
        }
        return try {
            val parsed = json.decodeFromString(DesignBlueprint.serializer(), input)
            okResult(parsed)
        } catch (e: Throwable) {
            errResult(AppError.Kind.SchemaValidation, "Blueprint decode failure: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
}
