package com.adnanfoisal.infinitydesign.design.validation

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.core.util.ColorUtil
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement

/**
 * Validator for DesignDocuments. This is the first line of defense against
 * malformed / hostile input — section 19 hard constraints, section 47 NaN/Infinity
 * guards, and section 83 import security all flow through here.
 *
 * Note: The validator is NOT the only security mechanism. The renderer also
 * independently sanitises every input (defense in depth, section 49).
 */
object DesignValidator {

    private val ALLOWED_EFFECTS = setOf(
        "cloud", "aurora", "fog", "soft_light", "volumetric_glow",
        "ink_splash", "fluid_blob", "liquid_gradient", "blob_field", "organic_path",
        "grain", "paper", "noise", "halftone", "speckle", "mesh_texture",
        "circuit", "grid", "technical_lines", "blueprint_lines", "rings",
        "rays", "wave_field", "particle_field", "orbit_system", "distortion_field",
    )

    private val ALLOWED_BLEND_MODES = setOf(
        "normal", "multiply", "screen", "overlay", "darken", "lighten",
        "color_dodge", "color_burn", "hard_light", "soft_light",
        "difference", "exclusion", "hue", "saturation", "color", "luminosity",
        "additive",
    )

    private val ALLOWED_FONT_ROLES = setOf(
        "neutral-sans", "geometric-display", "editorial-serif", "technical-mono",
        "condensed-display", "humanist-sans", "display-serif",
    )

    fun validate(doc: DesignDocument): AppResult<DesignDocument> {
        val errors = buildList {
            if (doc.schemaVersion <= 0) add("schemaVersion must be > 0")
            if (doc.id.isBlank()) add("id must not be blank")
            if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height))
                add("canvas dimensions must be finite")
            if (doc.canvas.width !in 1f..100_000f) add("canvas.width out of range: ${doc.canvas.width}")
            if (doc.canvas.height !in 1f..100_000f) add("canvas.height out of range: ${doc.canvas.height}")
            addAll(validateBackground(doc.background))
            addAll(validatePalette(doc))
            addAll(validateElements(doc))
            if (doc.elements.groupingBy { it.id }.eachCount().any { it.value > 1 })
                add("Duplicate element IDs detected")
        }
        return if (errors.isEmpty()) okResult(doc)
        else errResult(AppError.Kind.SchemaValidation, errors.joinToString("; "))
    }

    fun validateBlueprint(bp: DesignBlueprint): AppResult<DesignBlueprint> {
        val errors = buildList {
            if (bp.id.isBlank()) add("blueprint.id must not be blank")
            if (bp.prompt.isBlank()) add("blueprint.prompt must not be blank")
            if (bp.title.isBlank()) add("blueprint.title must not be blank")
            if (bp.composition.isBlank()) add("composition must not be blank")
            if (!COMPOSITIONS.contains(bp.composition)) add("unknown composition: ${bp.composition}")
            if (!COLOR_RE.matches(bp.palette.primary)) add("invalid palette.primary")
            if (!COLOR_RE.matches(bp.palette.secondary)) add("invalid palette.secondary")
            if (!COLOR_RE.matches(bp.palette.accent)) add("invalid palette.accent")
            if (!COLOR_RE.matches(bp.palette.background)) add("invalid palette.background")
            if (!COLOR_RE.matches(bp.palette.foreground)) add("invalid palette.foreground")
            bp.semanticContent.forEach { item ->
                if (item.role.isBlank()) add("semanticContent role must not be blank")
                if (item.protected && item.content.isBlank()) add("protected content missing for ${item.role}")
            }
            if (bp.prompt.length > 10_000) add("prompt too long")
            if (bp.hierarchy.size > 50) add("hierarchy too long")
        }
        return if (errors.isEmpty()) okResult(bp)
        else errResult(AppError.Kind.SchemaValidation, errors.joinToString("; "))
    }

    private fun validateBackground(bg: BackgroundSpec): List<String> = when (bg) {
        is BackgroundSpec.Solid -> if (!COLOR_RE.matches(bg.color)) listOf("background.color invalid: ${bg.color}") else emptyList()
        is BackgroundSpec.LinearGradient -> bg.stops.flatMap {
            if (!COLOR_RE.matches(it.color) || !SafeMath.allFinite(it.position))
                listOf("bad color stop ${it.color}")
            else emptyList()
        }
        is BackgroundSpec.RadialGradient -> {
            val r = if (!SafeMath.allFinite(bg.centerX, bg.centerY, bg.radius)) listOf("radial params invalid")
            else emptyList()
            r + bg.stops.flatMap { if (!COLOR_RE.matches(it.color)) listOf("bad stop ${it.color}") else emptyList() }
        }
        is BackgroundSpec.Layered -> {
            val baseColor = if (!COLOR_RE.matches(bg.base)) listOf("background.base invalid") else emptyList()
            val layerErrs = bg.layers.flatMap { l ->
                buildList {
                    if (!ALLOWED_EFFECTS.contains(l.effect)) add("unknown effect: ${l.effect}")
                    if (!ALLOWED_BLEND_MODES.contains(l.blendMode)) add("unknown blend mode: ${l.blendMode}")
                    if (!SafeMath.allFinite(l.opacity) || l.opacity !in 0f..1f) add("opacity invalid: ${l.opacity}")
                    if (l.bounds != null && !SafeMath.allFinite(l.bounds.x, l.bounds.y, l.bounds.width, l.bounds.height))
                        add("layer bounds invalid")
                }
            }
            baseColor + layerErrs
        }
    }

    private fun validatePalette(doc: DesignDocument): List<String> = buildList {
        val colors = listOf(
            doc.palette.primary, doc.palette.secondary, doc.palette.accent,
            doc.palette.onPrimary, doc.palette.onSecondary,
        ) + doc.palette.muted
        for (c in colors) {
            if (!COLOR_RE.matches(c)) add("invalid palette color: $c")
        }
    }

    private fun validateElements(doc: DesignDocument): List<String> = doc.elements.flatMap { el ->
        val baseErrs = buildList {
            if (el.id.isBlank()) add("${el.name}: id blank")
            if (!SafeMath.allFinite(el.bounds.x, el.bounds.y, el.bounds.width, el.bounds.height))
                add("${el.id}: bounds non-finite")
            if (!SafeMath.allFinite(el.rotation, el.opacity))
                add("${el.id}: rotation/opacity non-finite")
            if (el.opacity !in 0f..1f) add("${el.id}: opacity out of range ${el.opacity}")
            if (el.bounds.width < 0f || el.bounds.height < 0f) add("${el.id}: negative dimensions")
        }
        val typeErrs = when (el) {
            is DesignElement.Text -> buildList {
                if (el.content.isBlank()) add("${el.id}: empty content")
                if (el.fontSize <= 0f || el.fontSize > 1000f) add("${el.id}: bad fontSize ${el.fontSize}")
                if (!ALLOWED_FONT_ROLES.contains(el.fontRole)) add("${el.id}: bad fontRole ${el.fontRole}")
                if (el.content.length > 100_000) add("${el.id}: text too long")
            }
            is DesignElement.Shape -> buildList {
                if (el.cornerRadius < 0f) add("${el.id}: bad cornerRadius")
                if (el.stroke != null && !SafeMath.allFinite(el.stroke.width)) add("${el.id}: bad stroke width")
            }
            is DesignElement.Procedural -> buildList {
                if (!ALLOWED_EFFECTS.contains(el.effect)) add("${el.id}: unknown effect ${el.effect}")
                if (!ALLOWED_BLEND_MODES.contains(el.blendMode)) add("${el.id}: bad blend ${el.blendMode}")
            }
            is DesignElement.Image -> buildList {
                if (el.assetId.isBlank()) add("${el.id}: blank assetId")
            }
            is DesignElement.Group -> buildList {
                if (el.childrenIds.isEmpty()) add("${el.id}: empty group")
                val groupRefs = el.childrenIds.toSet()
                val childCount = doc.elements.count { it.id in groupRefs }
                if (childCount != el.childrenIds.size) add("${el.id}: dangling child refs")
                val visited = mutableSetOf<String>()
                val stack = ArrayDeque<String>().apply { addAll(el.childrenIds) }
                var depth = 0
                while (stack.isNotEmpty()) {
                    depth++
                    if (depth > 64) { add("${el.id}: group nesting too deep"); break }
                    val cur = stack.removeLast()
                    if (!visited.add(cur)) { add("${el.id}: cycle in group children"); break }
                }
            }
        }
        baseErrs + typeErrs
    }

    private val COLOR_RE = Regex("""^(#[0-9a-fA-F]{3,8}|rgba?\([^)]+\))$""")

    val COMPOSITIONS: Set<String> = setOf(
        "asymmetric-left", "asymmetric-right", "centered-impact", "editorial",
        "split-vertical", "split-horizontal", "typography-dominant", "image-dominant",
        "modular-grid", "full-bleed", "framed", "minimal", "experimental",
        "technical", "poster-like", "magazine-like",
    )

    val ALLOWED_FONTS: Set<String> get() = ALLOWED_FONT_ROLES
    val ALLOWED_EFFECT_NAMES: Set<String> get() = ALLOWED_EFFECTS
    val ALLOWED_BLEND_MODE_NAMES: Set<String> get() = ALLOWED_BLEND_MODES
}
