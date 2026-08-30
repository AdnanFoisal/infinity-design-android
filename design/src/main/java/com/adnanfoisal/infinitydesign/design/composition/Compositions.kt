package com.adnanfoisal.infinitydesign.design.composition

import com.adnanfoisal.infinitydesign.core.util.DeterministicRandom
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.LayoutConstraint
import com.adnanfoisal.infinitydesign.design.dsl.ShapeKind

/**
 * Composition families. Section 16/17/23: each advertised composition must
 * actually exist; multiple genuinely different families are required.
 *
 * A composition family is a function (canvas, blueprint, seed) -> partial
 * DesignDocument skeleton containing layout constraints and recommended
 * element bounds. The candidate generator runs the rest.
 */
typealias CompositionFamily = (CanvasSpec, DesignBlueprint, Long) -> CompositionSkeleton

data class CompositionSkeleton(
    val name: String,
    val elements: List<DesignElement>,
    val constraints: List<LayoutConstraint>,
    val tokens: Map<String, Float> = emptyMap(),
)

object Compositions {

    private val registry: Map<String, CompositionFamily> = mapOf(
        "asymmetric-left" to ::asymmetricLeft,
        "asymmetric-right" to ::asymmetricRight,
        "centered-impact" to ::centeredImpact,
        "editorial" to ::editorial,
        "split-vertical" to ::splitVertical,
        "split-horizontal" to ::splitHorizontal,
        "typography-dominant" to ::typographyDominant,
        "image-dominant" to ::imageDominant,
        "modular-grid" to ::modularGrid,
        "full-bleed" to ::fullBleed,
        "framed" to ::framed,
        "minimal" to ::minimal,
        "experimental" to ::experimental,
        "technical" to ::technical,
        "poster-like" to ::posterLike,
        "magazine-like" to ::magazineLike,
    )

    fun names(): List<String> = registry.keys.toList()
    fun contains(name: String): Boolean = registry.containsKey(name)
    fun get(name: String): CompositionFamily? = registry[name]

    fun apply(canvas: CanvasSpec, bp: DesignBlueprint, seed: Long): CompositionSkeleton {
        val fn = registry[bp.composition] ?: registry["editorial"]!!
        return fn(canvas, bp, seed)
    }

    // === Implementations ===

    private fun textElement(
        id: String, content: String, bounds: Bounds,
        fontSize: Float = 48f, fontRole: String = "geometric-display",
        color: String = "#000000", weight: Int = 700,
        alignment: com.adnanfoisal.infinitydesign.design.dsl.TextAlignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.LEFT,
    ): DesignElement.Text = DesignElement.Text(
        id = id,
        bounds = bounds,
        content = content,
        fontSize = fontSize,
        fontRole = fontRole,
        color = color,
        weight = weight,
        alignment = alignment,
        name = "Text $id",
    )

    private fun shapeElement(
        id: String, bounds: Bounds, color: String,
        kind: ShapeKind = ShapeKind.RECTANGLE,
    ): DesignElement.Shape = DesignElement.Shape(
        id = id, bounds = bounds,
        kind = kind,
        fill = com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color),
        name = "Shape $id",
    )

    private fun proceduralElement(
        id: String, bounds: Bounds, effect: String, seed: Long,
        blendMode: String = "normal", opacity: Float = 1f,
    ): DesignElement.Procedural = DesignElement.Procedural(
        id = id, bounds = bounds, effect = effect, seed = seed,
        blendMode = blendMode, opacity = opacity, name = "Proc $id",
    )

    private fun safeContent(bp: DesignBlueprint, role: String, fallback: String): String =
        bp.semanticContent.firstOrNull { it.role == role }?.content ?: fallback

    private fun asymmetricLeft(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val pad = c.width * 0.06f
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "asymmetric-left",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height), "cloud", s, blendMode = "normal", opacity = 0.4f),
                textElement("title", title, Bounds(pad, pad * 2, c.width * 0.6f, c.height * 0.4f), fontSize = 96f,
                    color = bp.palette.foreground, weight = 800),
                textElement("subtitle", bp.purpose, Bounds(pad, c.height * 0.45f, c.width * 0.4f, c.height * 0.1f),
                    fontSize = 22f, fontRole = "neutral-sans", color = bp.palette.accent, weight = 500),
            ),
            constraints = listOf(),
        )
    }

    private fun asymmetricRight(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val pad = c.width * 0.06f
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "asymmetric-right",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height), "aurora", s, opacity = 0.5f),
                textElement("title", title, Bounds(c.width * 0.4f, pad * 2, c.width * 0.55f, c.height * 0.4f),
                    fontSize = 88f, color = bp.palette.foreground, weight = 800,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.RIGHT),
            ),
            constraints = listOf(),
        )
    }

    private fun centeredImpact(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "centered-impact",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height), "volumetric_glow", s, opacity = 0.7f),
                textElement("title", title,
                    Bounds(c.width * 0.1f, c.height * 0.35f, c.width * 0.8f, c.height * 0.3f),
                    fontSize = 120f, color = bp.palette.foreground, weight = 900,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun editorial(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val pad = c.width * 0.06f
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "editorial",
            elements = listOf(
                shapeElement("rule", Bounds(pad, c.height * 0.18f, c.width * 0.1f, 4f), bp.palette.accent),
                textElement("title", title, Bounds(pad, c.height * 0.2f, c.width * 0.85f, c.height * 0.3f),
                    fontSize = 72f, fontRole = "editorial-serif", color = bp.palette.foreground, weight = 700),
                textElement("body", bp.purpose, Bounds(pad, c.height * 0.55f, c.width * 0.85f, c.height * 0.3f),
                    fontSize = 18f, fontRole = "editorial-serif", color = bp.palette.foreground, weight = 400),
            ),
            constraints = listOf(),
        )
    }

    private fun splitVertical(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "split-vertical",
            elements = listOf(
                proceduralElement("left", Bounds(0f, 0f, c.width * 0.5f, c.height), "fluid_blob", s, opacity = 0.7f),
                textElement("title", title, Bounds(c.width * 0.55f, c.height * 0.1f, c.width * 0.4f, c.height * 0.4f),
                    fontSize = 64f, color = bp.palette.foreground, weight = 800),
            ),
            constraints = listOf(),
        )
    }

    private fun splitHorizontal(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "split-horizontal",
            elements = listOf(
                proceduralElement("top", Bounds(0f, 0f, c.width, c.height * 0.5f), "grid", s, opacity = 0.4f),
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.55f, c.width * 0.9f, c.height * 0.4f),
                    fontSize = 80f, color = bp.palette.foreground, weight = 800),
            ),
            constraints = listOf(),
        )
    }

    private fun typographyDominant(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "typography-dominant",
            elements = listOf(
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.1f, c.width * 0.9f, c.height * 0.7f),
                    fontSize = 160f, fontRole = "condensed-display", color = bp.palette.foreground, weight = 900),
            ),
            constraints = listOf(),
        )
    }

    private fun imageDominant(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "image-dominant",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height * 0.7f), "blob_field", s, opacity = 1f),
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.75f, c.width * 0.9f, c.height * 0.2f),
                    fontSize = 64f, color = bp.palette.foreground, weight = 800,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun modularGrid(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val cols = 3
        val rows = 3
        val cellW = c.width / cols
        val cellH = c.height / rows
        val r = DeterministicRandom(s)
        val cells = (0 until rows).flatMap { j ->
            (0 until cols).map { i ->
                val color = listOf(bp.palette.primary, bp.palette.secondary, bp.palette.accent)[r.nextInt(3)]
                shapeElement("cell-${i}-${j}",
                    Bounds(i * cellW, j * cellH, cellW, cellH), color)
            }
        }
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "modular-grid",
            elements = cells + listOf(
                textElement("title", title, Bounds(cellW, cellH, cellW, cellH),
                    fontSize = 48f, color = bp.palette.foreground, weight = 800,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun fullBleed(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "full-bleed",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height), "aurora", s, opacity = 1f),
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.7f, c.width * 0.9f, c.height * 0.25f),
                    fontSize = 96f, color = bp.palette.foreground, weight = 900,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun framed(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val pad = c.width * 0.06f
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "framed",
            elements = listOf(
                shapeElement("frame", Bounds(pad, pad, c.width - 2 * pad, c.height - 2 * pad),
                    bp.palette.foreground),
                textElement("title", title, Bounds(pad * 2, c.height * 0.3f, c.width - 4 * pad, c.height * 0.4f),
                    fontSize = 80f, color = bp.palette.foreground, weight = 700,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun minimal(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "minimal",
            elements = listOf(
                textElement("title", title, Bounds(c.width * 0.1f, c.height * 0.4f, c.width * 0.8f, c.height * 0.2f),
                    fontSize = 56f, fontRole = "humanist-sans", color = bp.palette.foreground, weight = 400,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun experimental(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        val r = DeterministicRandom(s)
        return CompositionSkeleton(
            name = "experimental",
            elements = listOf(
                proceduralElement("distortion", Bounds(0f, 0f, c.width, c.height), "distortion_field", s, opacity = 0.4f),
                textElement("title", title,
                    Bounds(r.nextFloat(0.05f, 0.3f) * c.width, r.nextFloat(0.3f, 0.5f) * c.height,
                        c.width * 0.7f, c.height * 0.2f),
                    fontSize = 88f, fontRole = "display-serif", color = bp.palette.accent, weight = 700,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.LEFT),
            ),
            constraints = listOf(),
        )
    }

    private fun technical(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "technical",
            elements = listOf(
                proceduralElement("grid", Bounds(0f, 0f, c.width, c.height), "blueprint_lines", s, opacity = 0.6f),
                proceduralElement("circuit", Bounds(0f, 0f, c.width, c.height), "circuit", s + 1L, opacity = 0.7f),
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.1f, c.width * 0.9f, c.height * 0.3f),
                    fontSize = 72f, fontRole = "technical-mono", color = bp.palette.foreground, weight = 700),
            ),
            constraints = listOf(),
        )
    }

    private fun posterLike(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "poster-like",
            elements = listOf(
                proceduralElement("bg", Bounds(0f, 0f, c.width, c.height), "cloud", s, opacity = 0.6f),
                proceduralElement("rays", Bounds(0f, c.height * 0.3f, c.width, c.height * 0.4f), "rays", s + 1L, opacity = 0.5f),
                textElement("title", title, Bounds(c.width * 0.05f, c.height * 0.1f, c.width * 0.9f, c.height * 0.2f),
                    fontSize = 128f, fontRole = "condensed-display", color = bp.palette.foreground, weight = 900),
                textElement("subtitle", bp.purpose, Bounds(c.width * 0.05f, c.height * 0.85f, c.width * 0.9f, c.height * 0.1f),
                    fontSize = 24f, fontRole = "neutral-sans", color = bp.palette.foreground, weight = 400,
                    alignment = com.adnanfoisal.infinitydesign.design.dsl.TextAlignment.CENTER),
            ),
            constraints = listOf(),
        )
    }

    private fun magazineLike(c: CanvasSpec, bp: DesignBlueprint, s: Long): CompositionSkeleton {
        val title = safeContent(bp, "title", bp.title)
        return CompositionSkeleton(
            name = "magazine-like",
            elements = listOf(
                proceduralElement("cover", Bounds(0f, 0f, c.width, c.height * 0.7f), "liquid_gradient", s, opacity = 1f),
                textElement("masthead", title, Bounds(c.width * 0.05f, c.height * 0.05f, c.width * 0.6f, c.height * 0.1f),
                    fontSize = 32f, fontRole = "editorial-serif", color = bp.palette.foreground, weight = 700),
                textElement("headline", bp.purpose, Bounds(c.width * 0.05f, c.height * 0.75f, c.width * 0.9f, c.height * 0.2f),
                    fontSize = 48f, fontRole = "editorial-serif", color = bp.palette.foreground, weight = 800),
            ),
            constraints = listOf(),
        )
    }
}
