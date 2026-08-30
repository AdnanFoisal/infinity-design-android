package com.adnanfoisal.infinitydesign.graphics.renderer

import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.FillSpec
import com.adnanfoisal.infinitydesign.design.dsl.ImageFit
import com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec
import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment

/**
 * Records draw operations. Used in unit tests to verify the renderer does the
 * right thing — and to support golden-image tests by hashing the recorded ops.
 *
 * Section 89 of the spec: golden image tests for visual regression.
 */
class HeadlessRenderer(
    override val width: Int,
    override val height: Int,
) : DrawSurface {

    sealed class Op {
        data class Save(val depth: Int) : Op()
        data class Restore(val depth: Int) : Op()
        data class ClipRect(val x: Float, val y: Float, val w: Float, val h: Float) : Op()
        data class Translate(val dx: Float, val dy: Float) : Op()
        data class Rotate(val rad: Float) : Op()
        data class Scale(val sx: Float, val sy: Float) : Op()
        data class FillBackground(val color: Int) : Op()
        data class DrawSolid(val color: Int, val bounds: Bounds) : Op()
        data class DrawRect(val bounds: Bounds, val fill: FillSpec, val stroke: StrokeSpec?, val radius: Float) : Op()
        data class DrawEllipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val fill: FillSpec, val stroke: StrokeSpec?) : Op()
        data class DrawPolygon(val points: List<Pair<Float, Float>>, val fill: FillSpec, val stroke: StrokeSpec?) : Op()
        data class DrawLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val stroke: StrokeSpec) : Op()
        data class DrawText(val text: String, val x: Float, val y: Float, val fontSize: Float, val color: Int, val fontRole: String, val weight: Int, val italic: Boolean, val align: TextAlignment, val letterSpacing: Float) : Op()
        data class DrawImage(val assetId: String, val bounds: Bounds, val fit: ImageFit, val cornerRadius: Float) : Op()
        data class Blur(val radius: Float) : Op()
        data class Shadow(val dx: Float, val dy: Float, val blur: Float, val color: Int) : Op()
        data class SetBlendMode(val mode: BlendMode) : Op()
        data class SetOpacity(val alpha: Float) : Op()
        data class PushLayer(val idx: Int) : Op()
        data class PopLayer(val idx: Int) : Op()
    }

    private val ops: MutableList<Op> = ArrayList()
    private var depth: Int = 0
    private var layerCount: Int = 0

    val operations: List<Op> get() = ops.toList()

    fun opCount(): Int = ops.size
    fun reset() { ops.clear(); depth = 0; layerCount = 0 }

    override fun save() { depth++; ops.add(Op.Save(depth)) }
    override fun restore() { ops.add(Op.Restore(depth)); depth = (depth - 1).coerceAtLeast(0) }
    override fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        if (!SafeMath.allFinite(x, y, w, h)) return
        ops.add(Op.ClipRect(x, y, w, h))
    }
    override fun translate(dx: Float, dy: Float) { if (SafeMath.allFinite(dx, dy)) ops.add(Op.Translate(dx, dy)) }
    override fun rotate(rad: Float) { if (SafeMath.allFinite(rad)) ops.add(Op.Rotate(rad)) }
    override fun scale(sx: Float, sy: Float) { if (SafeMath.allFinite(sx, sy)) ops.add(Op.Scale(sx, sy)) }
    override fun fillBackground(color: Int) { ops.add(Op.FillBackground(color)) }
    override fun drawSolid(color: Int, bounds: Bounds) { ops.add(Op.DrawSolid(color, bounds)) }
    override fun drawRect(bounds: Bounds, fill: FillSpec, stroke: StrokeSpec?, cornerRadius: Float) {
        if (!SafeMath.allFinite(bounds.x, bounds.y, bounds.width, bounds.height, cornerRadius)) return
        ops.add(Op.DrawRect(bounds, fill, stroke, cornerRadius))
    }
    override fun drawEllipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: FillSpec, stroke: StrokeSpec?) {
        if (!SafeMath.allFinite(cx, cy, rx, ry)) return
        if (rx < 0f || ry < 0f) return
        ops.add(Op.DrawEllipse(cx, cy, rx, ry, fill, stroke))
    }
    override fun drawTriangle(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, fill: FillSpec, stroke: StrokeSpec?) {
        ops.add(Op.DrawPolygon(listOf(p1, p2, p3), fill, stroke))
    }
    override fun drawPolygon(points: List<Pair<Float, Float>>, fill: FillSpec, stroke: StrokeSpec?) {
        if (points.any { !SafeMath.allFinite(it.first, it.second) }) return
        ops.add(Op.DrawPolygon(points, fill, stroke))
    }
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, stroke: StrokeSpec) {
        if (!SafeMath.allFinite(x1, y1, x2, y2)) return
        ops.add(Op.DrawLine(x1, y1, x2, y2, stroke))
    }
    override fun drawPath(path: DrawPath, fill: FillSpec, stroke: StrokeSpec?) {
        // For headless testing we just record a marker; the actual path is opaque.
        ops.add(Op.DrawPolygon(emptyList(), fill, stroke))
    }
    override fun drawText(text: String, x: Float, y: Float, fontSize: Float, color: Int, fontRole: String,
                          weight: Int, italic: Boolean, align: TextAlignment, letterSpacing: Float) {
        if (!SafeMath.allFinite(x, y, fontSize)) return
        if (fontSize <= 0f || fontSize > 1000f) return
        if (text.length > 100_000) return
        ops.add(Op.DrawText(text, x, y, fontSize, color, fontRole, weight, italic, align, letterSpacing))
    }
    override fun drawImage(assetId: String, bounds: Bounds, fit: ImageFit, cornerRadius: Float) {
        if (!SafeMath.allFinite(bounds.x, bounds.y, bounds.width, bounds.height, cornerRadius)) return
        ops.add(Op.DrawImage(assetId, bounds, fit, cornerRadius))
    }
    override fun applyBlur(radius: Float) { if (SafeMath.allFinite(radius) && radius in 0f..100f) ops.add(Op.Blur(radius)) }
    override fun applyShadow(dx: Float, dy: Float, blur: Float, color: Int) {
        if (SafeMath.allFinite(dx, dy, blur)) ops.add(Op.Shadow(dx, dy, blur, color))
    }
    override fun setBlendMode(mode: BlendMode) { ops.add(Op.SetBlendMode(mode)) }
    override fun setOpacity(alpha: Float) { ops.add(Op.SetOpacity(SafeMath.clampSafe(alpha, 0f, 1f))) }
    override fun pushLayer() { ops.add(Op.PushLayer(layerCount)) }
    override fun popLayer() { ops.add(Op.PopLayer(layerCount)); layerCount++ }
}
