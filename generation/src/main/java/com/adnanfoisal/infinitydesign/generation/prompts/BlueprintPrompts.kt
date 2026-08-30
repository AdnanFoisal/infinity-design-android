package com.adnanfoisal.infinitydesign.generation.prompts

/**
 * Prompt engineering for blueprint generation.
 *
 * This is the only place prompt strings live. The prompt explicitly forbids the
 * LLM from producing pixel coordinates or layout decisions — section 8 of the spec.
 */
object BlueprintPrompts {

    val system: String = """
You are the Creative Director in a procedural graphic design studio.
You transform natural-language prompts into Design Blueprints.

Your role:
- Understand the user's intent.
- Extract required content (titles, dates, prices, locations, CTAs).
- Choose a creative direction (mood, palette, typography, composition).
- Recommend a layout family from a fixed allowlist.
- Specify texture and decorative intent.

You MUST NOT:
- Produce pixel coordinates.
- Compute final text wrapping.
- Specify exact dimensions.
- Generate SVG / images.
- Embed user API keys or credentials.

Only respond with a single JSON object matching the requested schema.
Do not include prose. Do not wrap the JSON in markdown.
    """.trimIndent()

    fun userPrompt(req: PromptInputs): String = buildString {
        append("User prompt: ")
        append(req.prompt)
        append('\n')
        if (req.style != null) {
            append("Suggested style direction: ")
            append(req.style)
            append('\n')
        }
        append("Aspect ratio id: ")
        append(req.aspectId)
        append('\n')
        append("Seed: ")
        append(req.seed)
        append('\n')
        append('\n')
        append("Respond with a JSON object that contains these fields:\n")
        append("- id (uuid)\n")
        append("- title (short human-readable title)\n")
        append("- purpose (one sentence)\n")
        append("- audience (one sentence)\n")
        append("- mood (3-5 words)\n")
        append("- visualDirection (one phrase)\n")
        append("- palette: { name, primary, secondary, accent, neutrals[], background, foreground } (all as #RRGGBB hex)\n")
        append("- typography: { displayRole, bodyRole, captionRole, displayWeight, bodyWeight, displayTracking, bodyTracking }\n")
        append("- composition: one of these exact strings — asymmetric-left, asymmetric-right, centered-impact, editorial, split-vertical, split-horizontal, typography-dominant, image-dominant, modular-grid, full-bleed, framed, minimal, experimental, technical, poster-like, magazine-like\n")
        append("- visualLanguage: array of 3-6 short phrases\n")
        append("- density: one of MINIMAL, BALANCED, RICH, DENSE\n")
        append("- texture: array of 2-5 short phrases (cloud, grain, halftone, grid, glow, etc.)\n")
        append("- decorative: array of 0-4 short phrases\n")
        append("- lighting: one phrase\n")
        append("- hierarchy: array of { role, label, importance 1-10 }\n")
        append("- semanticContent: array of { role, content (exact quoted text), protected (true/false) }\n")
        append("- imagery: one phrase describing visual treatment\n")
        append("- constraints: array of 0-5 notes\n")
        append("- seed: same seed as input\n")
        append('\n')
        append("Preserve every quoted piece of content from the user's prompt verbatim.\n")
        append("Choose a genuinely different creative direction if the seed changes.")
    }

    data class PromptInputs(
        val prompt: String,
        val style: String?,
        val aspectId: String,
        val seed: Long,
    )
}
