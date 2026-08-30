package com.adnanfoisal.infinitydesign.generation.blueprint

import com.adnanfoisal.infinitydesign.design.dsl.BlueprintDensity
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintPalette
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintTypography
import com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem
import com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem
import kotlinx.serialization.Serializable

/**
 * Request model — what the Android client posts to the backend.
 * Section 37: validate everything server-side. Reject invalid input with structured errors.
 */
@Serializable
data class BlueprintRequest(
    val prompt: String,
    val style: String? = null,
    val aspectId: String = "portrait-poster",
    val seed: Long = 0L,
    val provider: ProviderKind = ProviderKind.GEMINI,
    val providerConfig: ProviderConfig = ProviderConfig(),
    val variations: Int = 1,
    val locale: String = "en",
)

@Serializable
enum class ProviderKind { GEMINI, LITELLM }

/**
 * User-supplied provider config. Both LiteLLM and Gemini shapes supported.
 * Section 39 BYOK: server must not log this. Section 40: SSRF protection.
 */
@Serializable
data class ProviderConfig(
    val litellmUrl: String? = null,
    val litellmApiKey: String? = null,
    val litellmModel: String? = null,
    val geminiApiKey: String? = null,
    val geminiModel: String = "gemini-3.7-flash",
)

/**
 * Response model — what the backend returns to the client.
 */
@Serializable
data class BlueprintResponse(
    val blueprint: BlueprintPayload,
    val prompt: String,
    val durationMs: Long,
)

@Serializable
data class BlueprintPayload(
    val id: String,
    val title: String,
    val purpose: String,
    val audience: String,
    val mood: String,
    val visualDirection: String,
    val palette: PalettePayload,
    val typography: TypographyPayload,
    val composition: String,
    val visualLanguage: List<String>,
    val density: BlueprintDensity,
    val texture: List<String>,
    val decorative: List<String>,
    val lighting: String,
    val hierarchy: List<HierarchyItem>,
    val semanticContent: List<SemanticContentItem>,
    val imagery: String,
    val constraints: List<String>,
    val seed: Long,
)

@Serializable
data class PalettePayload(
    val name: String,
    val primary: String,
    val secondary: String,
    val accent: String,
    val neutrals: List<String>,
    val background: String,
    val foreground: String,
)

@Serializable
data class TypographyPayload(
    val displayRole: String,
    val bodyRole: String,
    val captionRole: String,
    val displayWeight: Int,
    val bodyWeight: Int,
    val displayTracking: Float,
    val bodyTracking: Float,
)

@Serializable
data class PingResponse(
    val ok: Boolean,
    val provider: String,
    val model: String,
    val durationMs: Long,
    val message: String,
    val detail: String? = null,
)

/**
 * Request model for the /api/ping endpoint.
 * Section 38: never log secrets. The backend MUST NOT persist the API keys.
 */
@Serializable
data class PingRequest(
    val provider: ProviderKind,
    val providerConfig: ProviderConfig,
)
