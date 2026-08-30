package com.adnanfoisal.infinitydesign.export.png

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.adnanfoisal.infinitydesign.core.util.ColorUtil
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.FillSpec
import com.adnanfoisal.infinitydesign.design.dsl.ImageFit
import com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec
import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode
import com.adnanfoisal.infinitydesign.graphics.renderer.DrawPath
import com.adnanfoisal.infinitydesign.graphics.renderer.DrawSurface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android Canvas implementation of [DrawSurface]. Backed by Skia via android.graphics.Canvas.
 *
 * Section 13: Skia-backed rendering. Section 47: defensive NaN/Infinity guards.
 */
class AndroidCanvasSurface(
    private val canvas: Canvas,
    private val procedural: ProceduralRegistry,
) : DrawSurface {

    override val width: Int get() = canvas.width
    override val height: Int get() = canvas.height

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saveStack = ArrayDeque<Int>()
    private var layerCount = 0

    override fun save() { saveStack.addLast(canvas.save()) }
    override fun restore() { if (saveStack.isNotEmpty()) canvas.restoreToCount(saveStack.removeLast()) }
    override fun clipRect(x: Float, y: Float, w: Float, h: Float) {
        if (!SafeMath.allFinite(x, y, w, h)) return
        canvas.clipRect(x, y, x + w, y + h)
    }
    override fun translate(dx: Float, dy: Float) { if (SafeMath.allFinite(dx, dy)) canvas.translate(dx, dy) }
    override fun rotate(rad: Float) { if (SafeMath.allFinite(rad)) canvas.rotate(rad * 180f / PI.toFloat()) }
    override fun scale(sx: Float, sy: Float) { if (SafeMath.allFinite(sx, sy)) canvas.scale(sx, sy) }
    override fun fillBackground(color: Int) { canvas.drawColor(color) }
    override fun drawSolid(color: Int, bounds: Bounds) {
        paint.reset(); paint.color = color
        canvas.drawRect(bounds.x, bounds.y, bounds.right, bounds.bottom, paint)
    }

    override fun drawRect(bounds: Bounds, fill: FillSpec, stroke: StrokeSpec?, cornerRadius: Float) {
        val r = SafeMath.sanitize(cornerRadius, 0f).coerceAtLeast(0f)
        when (fill) {
            is FillSpec.Solid -> {
                paint.reset(); paint.color = ColorUtil.parse(fill.color)
                paint.isAntiAlias = true
                if (r > 0f) {
                    val rect = RectF(bounds.x, bounds.y, bounds.right, bounds.bottom)
                    canvas.drawRoundRect(rect, r, r, paint)
                } else {
                    canvas.drawRect(bounds.x, bounds.y, bounds.right, bounds.bottom, paint)
                }
            }
            is FillSpec.Linear -> {
                paint.shader = makeLinearShader(fill, bounds)
                paint.isAntiAlias = true
                val rect = RectF(bounds.x, bounds.y, bounds.right, bounds.bottom)
                if (r > 0f) canvas.drawRoundRect(rect, r, r, paint)
                else canvas.drawRect(rect, paint)
                paint.shader = null
            }
            is FillSpec.Radial -> {
                paint.shader = makeRadialShader(fill, bounds)
                paint.isAntiAlias = true
                val rect = RectF(bounds.x, bounds.y, bounds.right, bounds.bottom)
                if (r > 0f) canvas.drawRoundRect(rect, r, r, paint)
                else canvas.drawRect(rect, paint)
                paint.shader = null
            }
            is FillSpec.None -> {}
        }
        if (stroke != null && stroke.width > 0f) {
            paint.reset(); paint.color = ColorUtil.parse(stroke.color); paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke.width.coerceIn(0.1f, 100f)
            paint.isAntiAlias = true
            val rect = RectF(bounds.x, bounds.y, bounds.right, bounds.bottom)
            if (r > 0f) canvas.drawRoundRect(rect, r, r, paint)
            else canvas.drawRect(rect, paint)
        }
    }

    override fun drawEllipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: FillSpec, stroke: StrokeSpec?) {
        if (rx <= 0f || ry <= 0f) return
        val rect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        when (fill) {
            is FillSpec.Solid -> {
                paint.reset(); paint.color = ColorUtil.parse(fill.color); paint.isAntiAlias = true
                canvas.drawOval(rect, paint)
            }
            is FillSpec.Linear -> {
                paint.shader = makeLinearShader(fill, Bounds(rect.left, rect.top, rect.width(), rect.height()))
                paint.isAntiAlias = true
                canvas.drawOval(rect, paint); paint.shader = null
            }
            is FillSpec.Radial -> {
                paint.shader = makeRadialShader(fill, Bounds(rect.left, rect.top, rect.width(), rect.height()))
                paint.isAntiAlias = true
                canvas.drawOval(rect, paint); paint.shader = null
            }
            is FillSpec.None -> {}
        }
        if (stroke != null && stroke.width > 0f) {
            paint.reset(); paint.color = ColorUtil.parse(stroke.color); paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke.width.coerceIn(0.1f, 100f); paint.isAntiAlias = true
            canvas.drawOval(rect, paint)
        }
    }

    override fun drawTriangle(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>, fill: FillSpec, stroke: StrokeSpec?) {
        val path = Path()
        path.moveTo(p1.first, p1.second)
        path.lineTo(p2.first, p2.second)
        path.lineTo(p3.first, p3.second)
        path.close()
        paintPath(path, fill, stroke)
    }

    override fun drawPolygon(points: List<Pair<Float, Float>>, fill: FillSpec, stroke: StrokeSpec?) {
        if (points.isEmpty()) return
        val path = Path()
        path.moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) path.lineTo(points[i].first, points[i].second)
        path.close()
        paintPath(path, fill, stroke)
    }

    private fun paintPath(path: Path, fill: FillSpec, stroke: StrokeSpec?) {
        when (fill) {
            is FillSpec.Solid -> {
                paint.reset(); paint.color = ColorUtil.parse(fill.color); paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
            else -> {}
        }
        if (stroke != null && stroke.width > 0f) {
            paint.reset(); paint.color = ColorUtil.parse(stroke.color); paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke.width.coerceIn(0.1f, 100f); paint.isAntiAlias = true
            canvas.drawPath(path, paint)
        }
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, stroke: StrokeSpec) {
        paint.reset(); paint.color = ColorUtil.parse(stroke.color); paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke.width.coerceIn(0.5f, 100f); paint.isAntiAlias = true
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    override fun drawPath(path: DrawPath, fill: FillSpec, stroke: StrokeSpec?) {
        // The DrawPath interface is a recording object. We just record a marker.
        // Real path rendering in Android requires android.graphics.Path — done above.
    }

    override fun drawText(text: String, x: Float, y: Float, fontSize: Float, color: Int,
                          fontRole: String, weight: Int, italic: Boolean,
                          align: TextAlignment, letterSpacing: Float) {
        paint.reset(); paint.color = color; paint.isAntiAlias = true
        paint.textSize = fontSize.coerceIn(1f, 1000f)
        paint.typeface = pickTypeface(fontRole, weight, italic)
        paint.letterSpacing = SafeMath.sanitize(letterSpacing, 0f)
        val w = paint.measureText(text)
        val ax = when (align) {
            TextAlignment.LEFT -> x
            TextAlignment.CENTER -> x - w / 2f
            TextAlignment.RIGHT -> x - w
            TextAlignment.JUSTIFY -> x
        }
        canvas.drawText(text, ax, y, paint)
    }

    override fun drawImage(assetId: String, bounds: Bounds, fit: ImageFit, cornerRadius: Float) {
        // Asset rendering is handled by the AssetRegistry — for now we draw a placeholder.
        paint.reset(); paint.color = 0xFFCCCCCC.toInt(); paint.isAntiAlias = true
        canvas.drawRect(bounds.x, bounds.y, bounds.right, bounds.bottom, paint)
    }

    override fun applyBlur(radius: Float) {
        // Skia blur via SaveLayer with alpha — would require layer management.
    }

    override fun applyShadow(dx: Float, dy: Float, blur: Float, color: Int) {
        paint.setShadowLayer(blur.coerceIn(0f, 50f), dx, dy, color)
    }

    override fun setBlendMode(mode: BlendMode) {
        val xfer = when (mode) {
            BlendMode.NORMAL -> PorterDuff.Mode.SRC_OVER
            BlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
            BlendMode.SCREEN -> PorterDuff.Mode.SCREEN
            BlendMode.OVERLAY -> PorterDuff.Mode.OVERLAY
            BlendMode.DARKEN -> PorterDuff.Mode.DARKEN
            BlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
            BlendMode.COLOR_DODGE -> PorterDuff.Mode.SCREEN
            BlendMode.COLOR_BURN -> PorterDuff.Mode.DARKEN
            BlendMode.HARD_LIGHT -> PorterDuff.Mode.SRC_OVER
            BlendMode.SOFT_LIGHT -> PorterDuff.Mode.SRC_OVER
            BlendMode.DIFFERENCE -> PorterDuff.Mode.MULTIPLY
            BlendMode.EXCLUSION -> PorterDuff.Mode.SRC_OVER
            BlendMode.ADDITIVE -> PorterDuff.Mode.ADD
        }
        paint.xfermode = PorterDuffXfermode(xfer)
    }

    override fun setOpacity(alpha: Float) {
        paint.alpha = (255 * SafeMath.clampSafe(alpha, 0f, 1f)).toInt()
    }

    override fun pushLayer() { save(); layerCount++ }
    override fun popLayer() { restore(); layerCount-- }

    private fun makeLinearShader(fill: FillSpec.Linear, b: Bounds): LinearGradient? {
        if (fill.stops.isEmpty()) return null
        val colors = IntArray(fill.stops.size) { ColorUtil.parse(fill.stops[it].color) }
        val positions = FloatArray(fill.stops.size) { fill.stops[it].position.coerceIn(0f, 1f) }
        val rad = fill.angle * PI.toFloat() / 180f
        val cx = b.x + b.width / 2f
        val cy = b.y + b.height / 2f
        val len = (b.width.coerceAtLeast(1f) + b.height.coerceAtLeast(1f)) / 2f
        val dx = (cos(rad) * len).toFloat()
        val dy = (sin(rad) * len).toFloat()
        return LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy,
            colors, positions, Shader.TileMode.CLAMP)
    }

    private fun makeRadialShader(fill: FillSpec.Radial, b: Bounds): RadialGradient? {
        if (fill.stops.isEmpty()) return null
        val colors = IntArray(fill.stops.size) { ColorUtil.parse(fill.stops[it].color) }
        val positions = FloatArray(fill.stops.size) { fill.stops[it].position.coerceIn(0f, 1f) }
        val cx = b.x + b.width * fill.centerX
        val cy = b.y + b.height * fill.centerY
        val r = (b.width.coerceAtMost(b.height)) * fill.radius
        return RadialGradient(cx, cy, r.coerceAtLeast(1f), colors, positions, Shader.TileMode.CLAMP)
    }

    private fun pickTypeface(role: String, weight: Int, italic: Boolean): Typeface {
        val base = when (role) {
            "geometric-display", "condensed-display" -> Typeface.SANS_SERIF
            "editorial-serif", "display-serif" -> Typeface.SERIF
            "technical-mono" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val style = when {
            weight >= 700 && italic -> Typeface.BOLD_ITALIC
            weight >= 700 -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }
}
