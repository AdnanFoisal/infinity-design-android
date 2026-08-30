package com.adnanfoisal.infinitydesign.graphics.renderer

import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.ColorStop
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.FillSpec
import com.adnanfoisal.infinitydesign.design.dsl.ImageFit
import com.adnanfoisal.infinitydesign.design.dsl.ShapeKind
import com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec
import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment

/**
 * Platform-neutral drawing surface. Implementations:
 *  - [SkiaRenderer] on Android (delegates to android.graphics.Canvas)
 *  - [HeadlessRenderer] in tests (records draw calls — used by golden-image
 *    and property-based tests)
 *
 * Section 13 of the spec: rendering must support paths, transforms, gradients,
 * clipping, masks, blend modes, blur, shadows, text, image layers,
 * shader-driven effects, compositing, alpha, rotation, scaling — all through
 * this single interface so the design engine never knows about Android.
 *
 * Section 47: every number reaching the renderer is finite. Callers MUST
 * sanitise via SafeMath before invoking these methods; implementations also
 * defensively re-check.
 */
interface DrawSurface {
    val width: Int
    val height: Int

    fun save()
    fun restore()
    fun clipRect(x: Float, y: Float, w: Float, h: Float)
    fun translate(dx: Float, dy: Float)
    fun rotate(rad: Float)
    fun scale(sx: Float, sy: Float)

    fun fillBackground(color: Int)
    fun drawSolid(color: Int, bounds: Bounds)
    fun drawRect(bounds: Bounds, fill: FillSpec, stroke: StrokeSpec?, cornerRadius: Float)
    fun drawEllipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: FillSpec, stroke: StrokeSpec?)
    fun drawTriangle(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, fill: FillSpec, stroke: StrokeSpec?)
    fun drawPolygon(points: List<Pair<Float, Float>>, fill: FillSpec, stroke: StrokeSpec?)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, stroke: StrokeSpec)
    fun drawPath(path: DrawPath, fill: FillSpec, stroke: StrokeSpec?)
    fun drawText(text: String, x: Float, y: Float, fontSize: Float, color: Int, fontRole: String,
                 weight: Int, italic: Boolean, align: TextAlignment, letterSpacing: Float)
    fun drawImage(assetId: String, bounds: Bounds, fit: ImageFit, cornerRadius: Float)
    fun applyBlur(radius: Float)
    fun applyShadow(dx: Float, dy: Float, blur: Float, color: Int)
    fun setBlendMode(mode: BlendMode)
    fun setOpacity(alpha: Float)
    fun pushLayer()
    fun popLayer()
}

interface DrawPath {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float)
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float)
    fun close()
}

enum class BlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN,
    COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT,
    DIFFERENCE, EXCLUSION, ADDITIVE,
}

fun String.toBlendMode(): BlendMode = when (this) {
    "normal" -> BlendMode.NORMAL
    "multiply" -> BlendMode.MULTIPLY
    "screen" -> BlendMode.SCREEN
    "overlay" -> BlendMode.OVERLAY
    "darken" -> BlendMode.DARKEN
    "lighten" -> BlendMode.LIGHTEN
    "color_dodge" -> BlendMode.COLOR_DODGE
    "color_burn" -> BlendMode.COLOR_BURN
    "hard_light" -> BlendMode.HARD_LIGHT
    "soft_light" -> BlendMode.SOFT_LIGHT
    "difference" -> BlendMode.DIFFERENCE
    "exclusion" -> BlendMode.EXCLUSION
    "additive" -> BlendMode.ADDITIVE
    else -> BlendMode.NORMAL
}

/**
 * The head renderer. Modules that want to render (app, export, thumbnailer)
 * get this injected — they don't know whether they're talking to Android
 * Skia or the test renderer.
 */
interface DesignRenderer {
    fun render(doc: DesignDocument, surface: DrawSurface, quality: RenderQuality = RenderQuality.EDIT)
}

enum class RenderQuality { EDIT, EXPORT_HIGH, THUMBNAIL }
