package com.adnanfoisal.infinitydesign.design.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Design Blueprint — the *creative intent* produced by an LLM.
 *
 * Section 7 of the spec: blueprint is separate from the executable DesignDocument.
 * The blueprint must NEVER carry exact pixel coordinates.
 */
@Serializable
data class DesignBlueprint(
    @SerialName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("id") val id: String,
    @SerialName("prompt") val prompt: String,
    @SerialName("title") val title: String,
    @SerialName("purpose") val purpose: String,
    @SerialName("audience") val audience: String = "",
    @SerialName("mood") val mood: String,
    @SerialName("visualDirection") val visualDirection: String,
    @SerialName("palette") val palette: BlueprintPalette,
    @SerialName("typography") val typography: BlueprintTypography,
    @SerialName("composition") val composition: String,
    @SerialName("visualLanguage") val visualLanguage: List<String> = emptyList(),
    @SerialName("density") val density: BlueprintDensity = BlueprintDensity.BALANCED,
    @SerialName("texture") val texture: List<String> = emptyList(),
    @SerialName("decorative") val decorative: List<String> = emptyList(),
    @SerialName("lighting") val lighting: String = "",
    @SerialName("hierarchy") val hierarchy: List<HierarchyItem> = emptyList(),
    @SerialName("semanticContent") val semanticContent: List<SemanticContentItem> = emptyList(),
    @SerialName("imagery") val imagery: String = "",
    @SerialName("constraints") val constraints: List<String> = emptyList(),
    @SerialName("seed") val seed: Long = 0L,
    @SerialName("referenceRepository") val referenceRepository: String = REFERENCE_REPO,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val REFERENCE_REPO = "https://github.com/AdnanFoisal/infinity-design"
    }
}

@Serializable
data class BlueprintPalette(
    val name: String,
    val primary: String,
    val secondary: String,
    val accent: String,
    val neutrals: List<String> = emptyList(),
    val background: String,
    val foreground: String,
)

@Serializable
data class BlueprintTypography(
    val displayRole: String,
    val bodyRole: String,
    val captionRole: String = "neutral-sans",
    val displayWeight: Int = 700,
    val bodyWeight: Int = 400,
    val displayTracking: Float = 0f,
    val bodyTracking: Float = 0f,
)

@Serializable
enum class BlueprintDensity { MINIMAL, BALANCED, RICH, DENSE }

@Serializable
data class HierarchyItem(
    val role: String,
    val label: String,
    val importance: Int = 5,
)

@Serializable
data class SemanticContentItem(
    val role: String,
    val content: String,
    val protected: Boolean = true,
)
