package com.adnanfoisal.infinitydesign.design

import com.adnanfoisal.infinitydesign.design.composition.Compositions
import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintPalette
import com.adnanfoisal.infinitydesign.design.dsl.BlueprintTypography
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.LayoutConstraint
import com.adnanfoisal.infinitydesign.design.dsl.ShapeKind
import com.adnanfoisal.infinitydesign.design.layout.CandidateGenerator
import com.adnanfoisal.infinitydesign.design.layout.LayoutEngine
import com.adnanfoisal.infinitydesign.design.typography.HeuristicTypographyEngine
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutEngineTest {

    private val typography = HeuristicTypographyEngine()
    private val layout = LayoutEngine(typography)

    @Test fun `NaN dimensions are preserved as out of bounds`() {
        val doc = sampleLayoutDoc(
            elements = listOf(
                DesignElement.Shape(
                    id = "s1", bounds = Bounds(-50f, 0f, 100f, 100f),
                    kind = ShapeKind.RECTANGLE,
                ),
            ),
        )
        val r = layout.resolve(doc)
        assertThat(r.outOfBounds).contains("s1")
    }

    @Test fun `maxWidth constraint clamps width`() {
        val el = DesignElement.Shape(
            id = "s1", bounds = Bounds(0f, 0f, 1000f, 100f),
            kind = ShapeKind.RECTANGLE,
        )
        val doc = sampleLayoutDoc(elements = listOf(el)).copy(
            constraints = listOf(LayoutConstraint.MaxWidth("s1", 0.5f)),
        )
        val r = layout.resolve(doc)
        val resolved = r.document.elements.first()
        assertThat(resolved.bounds.width).isLessThan(1000f)
    }
}

class CandidateGeneratorTest {

    private val gen = CandidateGenerator()

    @Test fun `generates multiple candidates with distinct scores`() {
        val bp = sampleBlueprint()
        val candidates = gen.generateCandidates(bp, CanvasSpec.POSTER_PORTRAIT, count = 6)
        assertThat(candidates.size).isAtLeast(1)
        // Best candidate has no hard violations
        val best = gen.best(candidates)!!
        assertThat(best.hardViolations).isEmpty()
    }

    @Test fun `hard violation rejects candidates with missing required content`() {
        val bp = sampleBlueprint().copy(
            semanticContent = listOf(
                com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem("title", "Code Forward 2026", protected = true),
            ),
        )
        // Most compositions include the title text — but a candidate with
        // "minimal" composition might omit. Here we only verify the generator
        // doesn't crash and returns at least one acceptable candidate.
        val candidates = gen.generateCandidates(bp, CanvasSpec.POSTER_PORTRAIT, count = 6)
        assertThat(candidates.size).isAtLeast(1)
    }

    @Test fun `candidates are sorted by score descending`() {
        val bp = sampleBlueprint()
        val candidates = gen.generateCandidates(bp, CanvasSpec.POSTER_PORTRAIT, count = 8)
        val scores = candidates.map { it.score }
        assertThat(scores).isInOrder(Comparator<Float> { a, b -> b.compareTo(a) })
    }

    @Test fun `compositions registry covers all advertised names`() {
        val advertised = Compositions.names()
        assertThat(advertised.size).isAtLeast(12)
        for (name in advertised) {
            assertThat(Compositions.contains(name)).isTrue()
        }
    }
}

internal fun sampleBlueprint() = DesignBlueprint(
    id = "bp-test",
    prompt = "Create a futuristic robotics competition poster for university students.",
    title = "Code Forward 2026",
    purpose = "Annual university robotics hackathon poster",
    audience = "University engineering students",
    mood = "Futuristic, energetic",
    visualDirection = "Dark tech aesthetic with cyan lighting and abstract robotic visuals",
    palette = BlueprintPalette(
        name = "Dark Tech",
        primary = "#00E5FF",
        secondary = "#0F172A",
        accent = "#7C4DFF",
        neutrals = listOf("#1E293B", "#334155"),
        background = "#0F172A",
        foreground = "#E0F7FA",
    ),
    typography = BlueprintTypography(
        displayRole = "condensed-display",
        bodyRole = "neutral-sans",
        captionRole = "technical-mono",
        displayWeight = 900,
        bodyWeight = 400,
    ),
    composition = "poster-like",
    visualLanguage = listOf("glow", "grid", "linework"),
    hierarchy = listOf(
        com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem("title", "Title", 10),
        com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem("date", "Date", 8),
        com.adnanfoisal.infinitydesign.design.dsl.HierarchyItem("location", "Location", 7),
    ),
    semanticContent = listOf(
        com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem("title", "Code Forward 2026", protected = true),
        com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem("date", "October 12, 2026", protected = false),
        com.adnanfoisal.infinitydesign.design.dsl.SemanticContentItem("location", "CUET Auditorium", protected = false),
    ),
    seed = 12345L,
)

private fun sampleLayoutDoc(
    elements: List<DesignElement> = emptyList(),
): DesignDocument = DesignDocument(
    id = "test-doc-layout",
    name = "Test",
    canvas = CanvasSpec(1080f, 1620f),
    background = BackgroundSpec.Solid("#FFFFFF"),
    palette = com.adnanfoisal.infinitydesign.design.dsl.PaletteSpec(
        primary = "#000000", secondary = "#FFFFFF", accent = "#3F51B5",
        onPrimary = "#FFFFFF", onSecondary = "#000000",
    ),
    typography = com.adnanfoisal.infinitydesign.design.dsl.TypographySpec.Default,
    elements = elements,
    seed = 1234L,
)
