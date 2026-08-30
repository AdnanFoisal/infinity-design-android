package com.adnanfoisal.infinitydesign.backend.api

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintDensity
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintPalette
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintTypography
import com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem
import com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintPayload
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.PalettePayload
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.generation.blueprint.TypographyPayload
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

object RequestValidator {

    fun validateBlueprintRequest(r: BlueprintRequest): AppResult<BlueprintRequest> {
        val errors = buildList {
            if (r.prompt.isBlank()) add("prompt must not be blank")
            if (r.prompt.length > 5_000) add("prompt too long (max 5000 chars)")
            val styleLen = r.style?.length ?: 0
            if (styleLen > 500) add("style too long")
            if (r.aspectId.isBlank() || r.aspectId.length > 100) add("bad aspectId")
            if (r.variations !in 1..6) add("variations must be in 1..6")
            when (r.provider) {
                ProviderKind.GEMINI -> {
                    if (r.providerConfig.geminiApiKey.isNullOrBlank()) add("geminiApiKey required")
                    if (r.providerConfig.geminiModel.isBlank()) add("geminiModel required")
                }
                ProviderKind.LITELLM -> {
                    if (r.providerConfig.litellmUrl.isNullOrBlank()) add("litellmUrl required")
                    if (r.providerConfig.litellmApiKey.isNullOrBlank()) add("litellmApiKey required")
                    if (r.providerConfig.litellmModel.isNullOrBlank()) add("litellmModel required")
                }
            }
        }
        return if (errors.isEmpty()) okResult(r)
        else errResult(AppError.Kind.SchemaValidation, errors.joinToString("; "))
    }

    fun validateProviderConfigForPing(p: ProviderConfig, kind: ProviderKind): AppResult<ProviderConfig> {
        val errs = buildList {
            when (kind) {
                ProviderKind.GEMINI -> {
                    if (p.geminiApiKey.isNullOrBlank()) add("geminiApiKey required")
                }
                ProviderKind.LITELLM -> {
                    if (p.litellmUrl.isNullOrBlank()) add("litellmUrl required")
                    if (p.litellmApiKey.isNullOrBlank()) add("litellmApiKey required")
                    if (p.litellmModel.isNullOrBlank()) add("litellmModel required")
                }
            }
        }
        return if (errs.isEmpty()) okResult(p)
        else errResult(AppError.Kind.SchemaValidation, errs.joinToString("; "))
    }
}

fun BlueprintPayload.toDesignBlueprint(prompt: String): DesignBlueprint = DesignBlueprint(
    id = id,
    prompt = prompt,
    title = title,
    purpose = purpose,
    audience = audience,
    mood = mood,
    visualDirection = visualDirection,
    palette = BlueprintPalette(
        name = palette.name,
        primary = palette.primary,
        secondary = palette.secondary,
        accent = palette.accent,
        neutrals = palette.neutrals,
        background = palette.background,
        foreground = palette.foreground,
    ),
    typography = BlueprintTypography(
        displayRole = typography.displayRole,
        bodyRole = typography.bodyRole,
        captionRole = typography.captionRole,
        displayWeight = typography.displayWeight,
        bodyWeight = typography.bodyWeight,
        displayTracking = typography.displayTracking,
        bodyTracking = typography.bodyTracking,
    ),
    composition = composition,
    visualLanguage = visualLanguage,
    density = density,
    texture = texture,
    decorative = decorative,
    lighting = lighting,
    hierarchy = hierarchy,
    semanticContent = semanticContent,
    imagery = imagery,
    constraints = constraints,
    seed = seed,
)

fun AppError.Kind.toHttpStatus(): HttpStatusCode = when (this) {
    AppError.Kind.Unauthorized -> HttpStatusCode.Unauthorized
    AppError.Kind.Forbidden -> HttpStatusCode.Forbidden
    AppError.Kind.RateLimited -> HttpStatusCode.TooManyRequests
    AppError.Kind.NetworkTimeout -> HttpStatusCode.RequestTimeout
    AppError.Kind.SchemaValidation -> HttpStatusCode.BadRequest
    AppError.Kind.MalformedResponse -> HttpStatusCode.BadGateway
    AppError.Kind.EmptyResponse -> HttpStatusCode.BadGateway
    AppError.Kind.NotFound -> HttpStatusCode.NotFound
    AppError.Kind.ProviderUnavailable -> HttpStatusCode.BadGateway
    AppError.Kind.ProviderRefusal -> HttpStatusCode.BadGateway
    AppError.Kind.Cancelled -> HttpStatusCode.BadRequest
    else -> HttpStatusCode.InternalServerError
}

@Serializable
data class ErrorResponse(
    val error: String,
    val kind: String,
    val detail: String? = null,
)
