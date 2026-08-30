package com.adnanfoisal.infinitydesign.design.dsl

import com.adnanfoisal.infinitydesign.core.util.SafeMath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Design Document — the executable, editable source of truth.
 *
 * Schema is versioned. Older projects must migrate forward, never be silently destroyed.
 * The document is intentionally the *compiled* output of a blueprint, not the blueprint itself.
 *
 * Section 6 of the spec: DesignDocument is the source of truth.
 */
@Serializable
data class DesignDocument(
    @SerialName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "Untitled",
    @SerialName("canvas") val canvas: CanvasSpec,
    @SerialName("background") val background: BackgroundSpec,
    @SerialName("palette") val palette: PaletteSpec,
    @SerialName("typography") val typography: TypographySpec,
    @SerialName("tokens") val tokens: DesignTokens = DesignTokens.default(),
    @SerialName("elements") val elements: List<DesignElement> = emptyList(),
    @SerialName("constraints") val constraints: List<LayoutConstraint> = emptyList(),
    @SerialName("metadata") val metadata: DocumentMetadata = DocumentMetadata(),
    @SerialName("blueprintId") val blueprintId: String? = null,
    @SerialName("seed") val seed: Long = 0L,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class CanvasSpec(
    val width: Float,
    val height: Float,
    val name: String = "Custom",
    @SerialName("aspectId") val aspectId: String = "custom",
) {
    val aspect: Float get() = if (height > 0f) width / height else 0f
    val isLandscape: Boolean get() = width > height
    val isPortrait: Boolean get() = height > width
    val isSquare: Boolean get() = SafeMath.approxEqual(width, height)
    companion object {
        val POSTER_PORTRAIT = CanvasSpec(1080f, 1620f, "Poster Portrait", "portrait-poster")
        val POSTER_LANDSCAPE = CanvasSpec(1620f, 1080f, "Poster Landscape", "landscape-poster")
        val SQUARE_SOCIAL = CanvasSpec(1080f, 1080f, "Square Social", "square-social")
        val STORY_VERTICAL = CanvasSpec(1080f, 1920f, "Story Vertical", "story-vertical")
        val A4_PORTRAIT = CanvasSpec(794f, 1123f, "A4 Portrait", "a4-portrait")
        val A4_LANDSCAPE = CanvasSpec(1123f, 794f, "A4 Landscape", "a4-landscape")
        val BUSINESS_CARD = CanvasSpec(1050f, 600f, "Business Card", "business-card")
        val funList = listOf(POSTER_PORTRAIT, POSTER_LANDSCAPE, SQUARE_SOCIAL, STORY_VERTICAL, A4_PORTRAIT, A4_LANDSCAPE, BUSINESS_CARD)
    }
}

@Serializable
sealed class BackgroundSpec {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: String) : BackgroundSpec()

    @Serializable
    @SerialName("linearGradient")
    data class LinearGradient(
        val stops: List<ColorStop>,
        val angle: Float = 0f,
    ) : BackgroundSpec()

    @Serializable
    @SerialName("radialGradient")
    data class RadialGradient(
        val stops: List<ColorStop>,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val radius: Float = 0.7f,
    ) : BackgroundSpec()

    @Serializable
    @SerialName("layered")
    data class Layered(val base: String, val layers: List<ProceduralLayer>) : BackgroundSpec()
}

@Serializable
data class ColorStop(
    val color: String,
    val position: Float = 0f,
)

@Serializable
data class PaletteSpec(
    val name: String = "Default",
    val primary: String = "#000000",
    val secondary: String = "#FFFFFF",
    val accent: String = "#3F51B5",
    val muted: List<String> = listOf("#666666"),
    val onPrimary: String = "#FFFFFF",
    val onSecondary: String = "#000000",
)

@Serializable
data class TypographySpec(
    val display: String = "neutral-sans",
    val body: String = "neutral-sans",
    val caption: String = "neutral-sans",
    val scale: Float = 1f,
) {
    companion object {
        val Default = TypographySpec()
    }
}

/**
 * Design tokens — system-level constants. Section 22 of the spec.
 */
@Serializable
data class DesignTokens(
    val spacing: List<Float> = listOf(4f, 8f, 12f, 16f, 24f, 32f, 48f, 64f),
    val typeScale: List<Float> = listOf(12f, 14f, 16f, 20f, 24f, 32f, 48f, 64f, 96f),
    val radiusScale: List<Float> = listOf(0f, 4f, 8f, 16f, 24f, 32f),
    val shadowScale: List<Float> = listOf(0f, 2f, 4f, 8f, 16f, 24f),
    val borderScale: List<Float> = listOf(0f, 1f, 2f, 4f, 8f),
    val opacityLevels: List<Float> = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f),
) {
    companion object {
        fun default() = DesignTokens()
    }
}

@Serializable
data class DocumentMetadata(
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val author: String = "user",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val extras: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ProceduralLayer(
    val id: String,
    val effect: String,
    val params: Map<String, JsonElement> = emptyMap(),
    val opacity: Float = 1f,
    val blendMode: String = "normal",
    val bounds: Bounds? = null,
    val seed: Long = 0L,
)

@Serializable
data class Bounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    /** Right edge = x + width. */
    val right: Float get() = x + width
    /** Bottom edge = y + height. */
    val bottom: Float get() = y + height
}
