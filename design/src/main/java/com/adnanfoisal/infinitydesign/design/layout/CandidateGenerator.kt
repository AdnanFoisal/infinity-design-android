package com.adnanfoisal.infinitydesign.design.layout

import com.adnanfoisal.infinitydesign.core.util.DeterministicRandom
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.composition.Compositions
import com.adnanfoisal.infinitydesign.design.composition.CompositionSkeleton
import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.DocumentMetadata
import com.adnanfoisal.infinitydesign.design.dsl.PaletteSpec
import com.adnanfoisal.infinitydesign.design.dsl.TypographySpec
import com.adnanfoisal.infinitydesign.design.typography.HeuristicTypographyEngine
import com.adnanfoisal.infinitydesign.design.typography.TypographyEngine

/**
 * Candidate generator + quality scorer.
 *
 * Section 17/18: do NOT pick the first design that renders. Build N candidates
 * varying composition/spacing/typography, score them, pick the best valid.
 *
 * Section 19: hard constraints (NaN, infinity, missing required content, out
 * of bounds) cause rejection. Soft constraints influence score.
 */
class CandidateGenerator(
    private val typography: TypographyEngine = HeuristicTypographyEngine(),
    private val layoutEngine: LayoutEngine = LayoutEngine(HeuristicTypographyEngine()),
) {

    data class Candidate(
        val document: DesignDocument,
        val score: Float,
        val hardViolations: List<String>,
        val softPenalties: List<String>,
    )

    fun generateCandidates(
        blueprint: DesignBlueprint,
        canvas: CanvasSpec,
        count: Int = 6,
        seeds: List<Long> = emptyList(),
    ): List<Candidate> {
        val r = DeterministicRandom(blueprint.seed.takeIf { it != 0L } ?: 1L)
        val candidates = ArrayList<Candidate>(count)
        val compositionNames = Compositions.names()
        val compositionChoice = if (Compositions.contains(blueprint.composition))
            listOf(blueprint.composition) else compositionNames
        val usedSeeds = seeds.ifEmpty { (0 until count).map { r.nextLong() } }
        for (i in 0 until count) {
            val seed = usedSeeds[i.coerceAtMost(usedSeeds.lastIndex)]
            val compositionName = compositionChoice[i % compositionChoice.size]
            val bpWithComposition = blueprint.copy(composition = compositionName, seed = seed)
            val skeleton = Compositions.apply(canvas, bpWithComposition, seed)
            // Vary spacing scale, typography scale, density param.
            val scale = 0.85f + (r.nextFloat() * 0.5f)
            val doc = skeletonToDocument(skeleton, bpWithComposition, canvas, seed, scale)
            val resolved = layoutEngine.resolve(doc)
            val scored = score(resolved, blueprint)
            candidates += Candidate(
                document = resolved.document,
                score = scored.score,
                hardViolations = scored.hard,
                softPenalties = scored.soft,
            )
        }
        // Reject candidates with hard violations, then sort by score desc.
        return candidates.filter { it.hardViolations.isEmpty() }
            .sortedByDescending { it.score }
    }

    fun best(candidates: List<Candidate>): Candidate? =
        candidates.maxByOrNull { it.score }

    private fun skeletonToDocument(
        skeleton: CompositionSkeleton,
        bp: DesignBlueprint,
        canvas: CanvasSpec,
        seed: Long,
        scale: Float,
    ): DesignDocument {
        val bgColor = bp.palette.background
        return DesignDocument(
            id = "doc-$seed",
            name = bp.title,
            canvas = canvas,
            background = BackgroundSpec.Solid(bgColor),
            palette = PaletteSpec(
                primary = bp.palette.primary,
                secondary = bp.palette.secondary,
                accent = bp.palette.accent,
                muted = bp.palette.neutrals,
                onPrimary = bp.palette.foreground,
                onSecondary = bp.palette.background,
            ),
            typography = TypographySpec(
                display = bp.typography.displayRole,
                body = bp.typography.bodyRole,
                caption = bp.typography.captionRole,
                scale = scale,
            ),
            elements = skeleton.elements.map { el ->
                el.withBounds(Bounds(
                    el.bounds.x * scale + (canvas.width * (1f - scale) * 0.5f),
                    el.bounds.y * scale + (canvas.height * (1f - scale) * 0.5f),
                    el.bounds.width * scale,
                    el.bounds.height * scale,
                ))
            },
            constraints = skeleton.constraints,
            metadata = DocumentMetadata(
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                tags = listOf(bp.mood, bp.visualDirection),
            ),
            blueprintId = bp.id,
            seed = seed,
        )
    }

    private fun score(
        resolved: LayoutEngine.LayoutResult,
        bp: DesignBlueprint,
    ): ScoreResult {
        val hard = ArrayList<String>()
        val soft = ArrayList<String>()
        val doc = resolved.document

        // Hard: out of bounds
        if (resolved.outOfBounds.isNotEmpty())
            hard += "Out of bounds: ${resolved.outOfBounds.size} element(s)"

        // Hard: required content missing
        val elementTexts = doc.elements.mapNotNull { (it as? DesignElement.Text)?.content }.toSet()
        for (item in bp.semanticContent.filter { it.protected }) {
            val contains = elementTexts.any { it.contains(item.content, ignoreCase = true) }
            if (!contains) hard += "Missing required content: ${item.role}"
        }

        // Hard: NaN/Infinity (defense in depth — validator already caught this)
        for (el in doc.elements) {
            if (!SafeMath.allFinite(el.bounds.x, el.bounds.y, el.bounds.width, el.bounds.height,
                    el.rotation, el.opacity)) {
                hard += "${el.id}: non-finite values"
            }
        }

        // Soft: text overflow
        var totalScore = 100f
        var overflowPenalty = 0f
        for ((id, m) in resolved.measured) {
            val el = doc.elements.find { it.id == id } as? DesignElement.Text ?: continue
            if (m.overflowed) {
                overflowPenalty += 10f
                soft += "Text $id overflowed"
            }
            if (m.height > el.bounds.height) overflowPenalty += 5f
        }

        // Soft: empty space balance — too many elements in one quadrant loses points
        val quadCounts = IntArray(4)
        for (el in doc.elements) {
            val cx = el.bounds.x + el.bounds.width / 2f
            val cy = el.bounds.y + el.bounds.height / 2f
            val qx = if (cx < doc.canvas.width / 2f) 0 else 1
            val qy = if (cy < doc.canvas.height / 2f) 0 else 2
            quadCounts[qx + qy]++
        }
        val maxQuad = quadCounts.maxOrNull() ?: 0
        val totalEls = doc.elements.size.coerceAtLeast(1)
        if (maxQuad.toFloat() / totalEls > 0.7f && totalEls > 3) {
            totalScore -= 8f
            soft += "Imbalanced composition"
        }

        // Soft: title prominence — title fontSize should dominate body fontSize
        val textEls: List<DesignElement> = doc.elements.filter { it is DesignElement.Text }
        if (textEls.size > 1) {
            val fontSizeOf: (DesignElement) -> Float = { el ->
                (el as DesignElement.Text).fontSize
            }
            val sortedBySize = textEls.sortedByDescending(fontSizeOf)
            val titleEl2 = sortedBySize[0] as DesignElement.Text
            val maxBody = (sortedBySize[1] as DesignElement.Text).fontSize
            if (titleEl2.fontSize <= maxBody * 1.4f) {
                totalScore -= 6f
                soft += "Weak hierarchy"
            }
        }

        totalScore -= overflowPenalty
        totalScore = totalScore.coerceIn(0f, 100f)
        return ScoreResult(totalScore, hard, soft)
    }

    private data class ScoreResult(val score: Float, val hard: List<String>, val soft: List<String>)
}
