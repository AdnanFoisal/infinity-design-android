package com.adnanfoisal.infinitydesign.export.png

import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.DesignRenderer
import com.adnanfoisal.infinitydesign.graphics.renderer.DrawSurface
import com.adnanfoisal.infinitydesign.graphics.renderer.RenderQuality
import com.adnanfoisal.infinitydesign.graphics.renderer.SkiaRenderer

/**
 * Android-aware wrapper around the core SkiaRenderer. The renderer itself is
 * platform-neutral — this class only exists to satisfy the Android type system
 * and inject the ProceduralRegistry.
 */
class SkiaRendererAndroid(private val registry: ProceduralRegistry) : DesignRenderer {
    private val delegate = SkiaRenderer(registry)
    override fun render(doc: DesignDocument, surface: DrawSurface, quality: RenderQuality) {
        delegate.render(doc, surface, quality)
    }
}
