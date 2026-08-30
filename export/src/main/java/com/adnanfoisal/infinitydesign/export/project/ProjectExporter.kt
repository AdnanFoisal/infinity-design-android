package com.adnanfoisal.infinitydesign.export.project

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocumentCodec
import com.adnanfoisal.infinitydesign.design.validation.DesignDocumentMigrator
import com.adnanfoisal.infinitydesign.design.validation.DesignValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Project file (JSON) export and import. Section 32/54: round trip must be
 * equivalent. Section 83: imported files are untrusted — validate, migrate,
 * never execute arbitrary code.
 */
class ProjectExporter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun export(doc: DesignDocument, blueprint: DesignBlueprint? = null, prompt: String? = null): AppResult<String> {
        try {
            val obj = buildJsonObject {
                put("schemaVersion", doc.schemaVersion)
                put("app", "com.adnanfoisal.infinitydesign")
                put("appVersion", 1)
                put("document", Json.decodeFromString(JsonObject.serializer(), DesignDocumentCodec.encode(doc)))
                if (blueprint != null) {
                    put("blueprint", Json.decodeFromString(JsonObject.serializer(), DesignDocumentCodec.encodeBlueprint(blueprint)))
                }
                if (prompt != null) put("prompt", JsonPrimitive(prompt))
            }
            return okResult(json.encodeToString(JsonObject.serializer(), obj))
        } catch (e: Throwable) {
            return errResult(AppError.Kind.RendererFailure, "Export failed: ${e.message}", e)
        }
    }

    fun import(raw: String): AppResult<ProjectImported> {
        if (raw.isBlank()) return errResult(AppError.Kind.SchemaValidation, "Empty project file")
        val parsed = try {
            json.parseToJsonElement(raw) as? JsonObject
                ?: return errResult(AppError.Kind.SchemaValidation, "Not a JSON object")
        } catch (e: Throwable) {
            return errResult(AppError.Kind.SchemaValidation, "Malformed JSON: ${e.message}", e)
        }
        val docEl = parsed["document"]
            ?: return errResult(AppError.Kind.SchemaValidation, "Missing document")
        val docJson = json.encodeToString(JsonElement.serializer(), docEl)
        val rawDoc = DesignDocumentCodec.decode(docJson)
        val doc: DesignDocument = when (rawDoc) {
            is AppResult.Ok -> rawDoc.value
            is AppResult.Err -> return errResult(rawDoc.error)
        }
        val migrated = when (val m = DesignDocumentMigrator.migrate(doc)) {
            is AppResult.Ok -> m.value
            is AppResult.Err -> return errResult(m.error)
        }
        val validated = when (val v = DesignValidator.validate(migrated)) {
            is AppResult.Ok -> v.value
            is AppResult.Err -> return errResult(v.error)
        }
        val blueprint = (parsed["blueprint"] as? JsonObject)?.let {
            val bpJson = json.encodeToString(JsonObject.serializer(), it)
            DesignDocumentCodec.decodeBlueprint(bpJson).getOrNull()
        }
        val prompt = (parsed["prompt"] as? JsonPrimitive)?.content
        return okResult(ProjectImported(validated, blueprint, prompt))
    }
}

data class ProjectImported(
    val document: DesignDocument,
    val blueprint: DesignBlueprint?,
    val prompt: String?,
)
