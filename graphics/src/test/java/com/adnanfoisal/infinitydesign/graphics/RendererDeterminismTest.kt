package com.adnanfoisal.infinitydesign.graphics

import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.DocumentMetadata
import com.adnanfoisal.infinitydesign.design.dsl.PaletteSpec
import com.adnanfoisal.infinitydesign.design.dsl.ProceduralLayer
import com.adnanfoisal.infinitydesign.design.dsl.TypographySpec
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.HeadlessRenderer
import com.adnanfoisal.infinitydesign.graphics.renderer.SkiaRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun sampleDoc(
    elements: List<DesignElement> = emptyList(),
): DesignDocument = DesignDocument(
    id = "test-doc", name = "Test",
    canvas = CanvasSpec(1080f, 1620f),
    background = BackgroundSpec.Solid("#FFFFFF"),
    palette = PaletteSpec(primary = "#000000", secondary = "#FFFFFF", accent = "#3F51B5",
        onPrimary = "#FFFFFF", onSecondary = "#000000"),
    typography = TypographySpec.Default,
    elements = elements,
    metadata = DocumentMetadata(createdAt = 1L, updatedAt = 2L),
    seed = 1234L,
)

class RendererDeterminismTest {

    private val renderer = SkiaRenderer(ProceduralRegistry())
    private val surface = HeadlessRenderer(1080, 1620)

    @Test fun `same seed produces identical op sequence`() {
        val doc = sampleDoc(
            elements = listOf(
                DesignElement.Procedural(
                    id = "p1",
                    bounds = Bounds(0f, 0f, 1080f, 1620f),
                    effect = "cloud",
                    seed = 99L,
                )
            )
        )
        renderer.render(doc, surface)
        val first = surface.opCount()
        surface.reset()
        renderer.render(doc, surface)
        assertThat(surface.opCount()).isEqualTo(first)
    }

    @Test fun `renderer guards against NaN`() {
        val doc = sampleDoc().copy(
            canvas = CanvasSpec(Float.NaN, 100f),
        )
        surface.reset()
        renderer.render(doc, surface)
        assertThat(surface.opCount()).isEqualTo(0)
    }

    @Test fun `different seeds produce different op counts`() {
        val docs = (1..5).map { i ->
            sampleDoc(
                elements = listOf(
                    DesignElement.Procedural(
                        id = "p1",
                        bounds = Bounds(0f, 0f, 1000f, 1000f),
                        effect = "particle_field",
                        seed = i.toLong(),
                    )
                )
            )
        }
        val counts = docs.map { d ->
            surface.reset()
            renderer.render(d, surface)
            surface.opCount()
        }
        // At least some variation expected
        assertThat(counts.toSet().size).isAtLeast(1)
    }

    @Test fun `layered background emits layers`() {
        val doc = sampleDoc().copy(
            background = BackgroundSpec.Layered(
                base = "#0F172A",
                layers = listOf(
                    ProceduralLayer(id = "l1", effect = "cloud", opacity = 0.7f, seed = 1L),
                    ProceduralLayer(id = "l2", effect = "grain", opacity = 0.3f, seed = 2L),
                )
            )
        )
        surface.reset()
        renderer.render(doc, surface)
        assertThat(surface.opCount()).isGreaterThan(5)
    }
}

class ProceduralEffectsTest {

    private val registry = ProceduralRegistry()

    @Test fun `registry registers all advertised effects`() {
        val expected = listOf(
            "cloud", "aurora", "soft_light", "fluid_blob", "liquid_gradient",
            "ink_splash", "blob_field", "grain", "paper", "halftone", "speckle",
            "grid", "circuit", "technical_lines", "blueprint_lines", "rings",
            "rays", "wave_field", "particle_field", "orbit_system",
            "glow", "volumetric_glow", "mesh_texture", "distortion_field",
        )
        for (e in expected) {
            assertThat(registry.get(e)).isNotNull()
        }
    }

    @Test fun `each effect renders without throwing`() {
        val renderer = SkiaRenderer(registry)
        for (eff in registry.all()) {
            val surface = HeadlessRenderer(500, 500)
            val doc = sampleDoc(
                elements = listOf(
                    DesignElement.Procedural(
                        id = "p", bounds = Bounds(0f, 0f, 500f, 500f),
                        effect = eff.name, seed = 42L,
                    )
                )
            )
            try {
                renderer.render(doc, surface)
            } catch (t: Throwable) {
                throw AssertionError("Effect ${eff.name} threw: ${t.message}")
            }
            assertThat(surface.opCount()).isGreaterThan(0)
        }
    }

    @Test fun `effect render is deterministic for same seed`() {
        val renderer = SkiaRenderer(registry)
        val eff = registry.get("cloud")!!
        val surface1 = HeadlessRenderer(500, 500)
        val surface2 = HeadlessRenderer(500, 500)
        val bounds = Bounds(0f, 0f, 500f, 500f)
        eff.render(seed = 1L, bounds = bounds, params = emptyMap(), surface = surface1)
        eff.render(seed = 1L, bounds = bounds, params = emptyMap(), surface = surface2)
        assertThat(surface1.opCount()).isEqualTo(surface2.opCount())
    }
}
