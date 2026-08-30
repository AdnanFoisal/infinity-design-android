package com.adnanfoisal.infinitydesign.generation.parser

import com.adnanfoisal.infinitydesign.core.util.DeterministicRandom
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintDensity
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintPalette
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintTypography
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem
import com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem
import java.util.UUID

/**
 * Offline blueprint builder. Produces a creative direction from the prompt
 * using deterministic rules + seeded variation — NO LLM call.
 *
 * Section 108: offline mode after Stage 1 must work.
 * Section 10: regeneration must produce genuinely distinct art directions.
 *
 * This is the fallback used when no provider is configured. The user can use
 * the app end-to-end to verify the design pipeline before paying for an LLM.
 */
object LocalBlueprintBuilder {

    private val PALETTES = listOf(
        BlueprintPalette("Dark Tech", "#00E5FF", "#0F172A", "#7C4DFF", listOf("#1E293B"), "#0F172A", "#E0F7FA"),
        BlueprintPalette("Editorial Mono", "#000000", "#FFFFFF", "#FF5722", listOf("#666666"), "#FAFAFA", "#000000"),
        BlueprintPalette("Soft Pastel", "#FF6B9D", "#FFE5EC", "#7C4DFF", listOf("#C8B6FF"), "#FFF5F5", "#3F2B4D"),
        BlueprintPalette("Cyber Punk", "#FF00CC", "#1A0F2E", "#00FFCC", listOf("#7700FF"), "#0A0014", "#00FFCC"),
        BlueprintPalette("Japanese Mono", "#1A1A1A", "#F5F5F0", "#D32F2F", listOf("#888888"), "#F5F5F0", "#1A1A1A"),
        BlueprintPalette("Brutalist", "#000000", "#FFFFFF", "#FFD700", listOf("#666666"), "#FFFFFF", "#000000"),
        BlueprintPalette("Warm Earth", "#5D4037", "#FFF8E1", "#D84315", listOf("#8D6E63"), "#FFF8E1", "#3E2723"),
        BlueprintPalette("Cool Forest", "#2E7D32", "#F1F8E9", "#00695C", listOf("#558B2F"), "#F1F8E9", "#1B5E20"),
    )

    private val COMPOSITIONS = listOf(
        "asymmetric-left", "asymmetric-right", "centered-impact", "editorial",
        "split-vertical", "split-horizontal", "typography-dominant", "image-dominant",
        "modular-grid", "full-bleed", "framed", "minimal", "experimental",
        "technical", "poster-like", "magazine-like",
    )

    private val MOODS = listOf(
        "Futuristic", "Editorial", "Bold", "Minimal", "Brutalist", "Cyber",
        "Warm", "Organic", "Technical", "Luxurious", "Playful", "Dramatic",
    )

    private val TEXTURE_BY_COMPOSITION = mapOf(
        "poster-like" to listOf("cloud", "rays", "grain"),
        "technical" to listOf("grid", "circuit", "blueprint_lines"),
        "experimental" to listOf("distortion_field", "speckle"),
        "editorial" to listOf("paper", "speckle"),
        "magazine-like" to listOf("liquid_gradient", "speckle"),
        "minimal" to listOf("grain"),
        "framed" to listOf("grain"),
        "centered-impact" to listOf("volumetric_glow", "rays"),
    )

    private val DECORATIVE_BY_DENSITY = mapOf(
        BlueprintDensity.MINIMAL to listOf(),
        BlueprintDensity.BALANCED to listOf("rings", "rays"),
        BlueprintDensity.RICH to listOf("particle_field", "orbit_system", "rings"),
        BlueprintDensity.DENSE to listOf("particle_field", "circuit", "grid", "rings", "rays"),
    )

    fun build(prompt: String, seed: Long, aspectId: String): DesignBlueprint {
        val r = DeterministicRandom(seed)
        val palette = r.pickFrom(PALETTES)
        val composition = r.pickFrom(COMPOSITIONS)
        val mood = r.pickFrom(MOODS)
        val density = r.pickFrom(BlueprintDensity.values().toList())
        val displayRole = when {
            composition == "editorial" || composition == "magazine-like" -> "editorial-serif"
            composition == "technical" -> "technical-mono"
            composition == "typography-dominant" || composition == "poster-like" -> "condensed-display"
            else -> "geometric-display"
        }
        val typography = BlueprintTypography(
            displayRole = displayRole,
            bodyRole = "neutral-sans",
            captionRole = "technical-mono",
            displayWeight = if (density == BlueprintDensity.DENSE) 900 else 700,
            bodyWeight = 400,
            displayTracking = if (mood == "Brutalist") 0.1f else 0f,
            bodyTracking = 0f,
        )
        val title = extractTitle(prompt) ?: "Untitled"
        val purpose = if (prompt.length > 120) prompt.substring(0, 120) + "…" else prompt
        val visualDirection = "${mood} direction"
        val texture = (TEXTURE_BY_COMPOSITION[composition] ?: listOf("grain")).take(
            when (density) {
                BlueprintDensity.MINIMAL -> 1
                BlueprintDensity.BALANCED -> 2
                BlueprintDensity.RICH -> 3
                BlueprintDensity.DENSE -> 4
            }
        )
        val decorative = DECORATIVE_BY_DENSITY[density] ?: emptyList()
        val hierarchy = listOf(
            HierarchyItem("title", "Title", 10),
            HierarchyItem("subtitle", "Subtitle", 7),
            HierarchyItem("body", "Body text", 5),
            HierarchyItem("caption", "Caption", 3),
        )
        val semanticContent = listOf(
            SemanticContentItem("title", title, protected = true),
        )
        val visualLanguage = when (mood) {
            "Futuristic", "Cyber" -> listOf("glow", "grid", "linework", "particles")
            "Editorial" -> listOf("typography", "whitespace", "rules", "captions")
            "Brutalist" -> listOf("heavy-type", "raw-blocks", "single-accent")
            "Warm", "Organic" -> listOf("gradient", "soft-blobs", "grain")
            else -> listOf("layered-fields", "rhythm", "balance")
        }
        val constraints = listOf(
            "Preserve required content",
            "Stay within safe-zone",
            "Strong typographic hierarchy",
        )
        return DesignBlueprint(
            id = UUID.randomUUID().toString(),
            prompt = prompt,
            title = title,
            purpose = purpose,
            audience = "General audience",
            mood = mood,
            visualDirection = visualDirection,
            palette = palette,
            typography = typography,
            composition = composition,
            visualLanguage = visualLanguage,
            density = density,
            texture = texture,
            decorative = decorative,
            lighting = if (mood == "Futuristic" || mood == "Cyber") "cyan rim" else "soft diffuse",
            hierarchy = hierarchy,
            semanticContent = semanticContent,
            imagery = if (mood == "Futuristic" || mood == "Cyber") "abstract robotic visuals" else "abstract gradient field",
            constraints = constraints,
            seed = seed,
        )
    }

    private fun extractTitle(prompt: String): String? {
        // Heuristic: try to find quoted text. If not, take the first sentence (max 60 chars).
        val quoted = Regex("\"([^\"]{3,80})\"").find(prompt)
        if (quoted != null) return quoted.groupValues[1]
        val firstSentence = prompt.split(Regex("[.!?]"), limit = 2).firstOrNull()?.trim()
        if (firstSentence != null && firstSentence.length in 3..80) {
            return firstSentence.replaceFirstChar { it.uppercase() }
        }
        return prompt.take(60).ifBlank { null }
    }
}
