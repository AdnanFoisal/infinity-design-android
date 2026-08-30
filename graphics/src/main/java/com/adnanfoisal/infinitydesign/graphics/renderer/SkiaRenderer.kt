package com.adnanfoisal.infinitydesign.graphics.renderer

import com.adnanfoisal.infinitydesign.core.util.ColorUtil
import com.adnanfoisal.infinitydesign.core.util.SafeMath
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
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The core rendering algorithm. Platform-neutral — talks only to [DrawSurface].
 * This is shared between the editor preview, exports, and tests.
 */
class SkiaRenderer(
    private val procedural: ProceduralRegistry,
) : DesignRenderer {

    override fun render(doc: DesignDocument, surface: DrawSurface, quality: RenderQuality) {
        // Section 47: never trust input even from validator
        if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height)) return
        surface.save()
        renderBackground(doc, surface)
        for (el in doc.elements) {
            if (!el.visible) continue
            if (!el.validate()) continue
            renderElement(el, doc, surface, quality)
        }
        surface.restore()
    }

    private fun renderBackground(doc: DesignDocument, surface: DrawSurface) {
        when (val bg = doc.background) {
            is BackgroundSpec.Solid -> {
                val c = ColorUtil.parse(bg.color)
                surface.fillBackground(c)
            }
            is BackgroundSpec.LinearGradient -> {
                val stops = bg.stops
                if (stops.isEmpty()) return
                surface.fillBackground(ColorUtil.parse(stops.first().color))
                // Linear gradient layers handled by surface implementation detail.
                // For the headless renderer, layered backgrounds reduce to the base color
                // and the procedural engine handles layered texture as separate elements.
            }
            is BackgroundSpec.RadialGradient -> {
                surface.fillBackground(ColorUtil.parse(bg.stops.firstOrNull()?.color ?: "#FFFFFF"))
            }
            is BackgroundSpec.Layered -> {
                surface.fillBackground(ColorUtil.parse(bg.base))
                for (layer in bg.layers) {
                    val b = layer.bounds ?: Bounds(0f, 0f, doc.canvas.width, doc.canvas.height)
                    val eff = procedural.get(layer.effect) ?: continue
                    surface.pushLayer()
                    surface.setOpacity(SafeMath.clampSafe(layer.opacity, 0f, 1f))
                    surface.setBlendMode(layer.blendMode.toBlendMode())
                    eff.render(seed = layer.seed, bounds = b, params = layer.params, surface = surface)
                    surface.popLayer()
                }
            }
        }
    }

    private fun renderElement(el: DesignElement, doc: DesignDocument, surface: DrawSurface, quality: RenderQuality) {
        surface.save()
        surface.setOpacity(SafeMath.clampSafe(el.opacity, 0f, 1f))
        val cx = el.bounds.x + el.bounds.width / 2f
        val cy = el.bounds.y + el.bounds.height / 2f
        if (el.rotation != 0f) {
            surface.translate(cx, cy)
            surface.rotate(el.rotation * (PI.toFloat() / 180f))
            surface.translate(-cx, -cy)
        }
        when (el) {
            is DesignElement.Text -> renderText(el, surface)
            is DesignElement.Shape -> renderShape(el, surface)
            is DesignElement.Procedural -> renderProcedural(el, surface, quality)
            is DesignElement.Image -> renderImage(el, surface)
            is DesignElement.Group -> {
                // Group bounds render as a virtual container — children are rendered
                // independently because they retain their own absolute coordinates.
                // (Group is a logical selection boundary, not a layout container.)
            }
        }
        surface.restore()
    }

    private fun renderText(el: DesignElement.Text, surface: DrawSurface) {
        val content = if (el.uppercase) el.content.uppercase() else el.content
        val color = ColorUtil.parse(el.color)
        // Multi-line: simple text split on \n then per-line baseline computation.
        val lines = content.split("\n")
        var y = el.bounds.y + el.fontSize
        for (line in lines) {
            val x = when (el.alignment) {
                TextAlignment.LEFT -> el.bounds.x
                TextAlignment.CENTER -> el.bounds.x + (el.bounds.width - measureTextWidth(line, el.fontSize)) / 2f
                TextAlignment.RIGHT -> el.bounds.x + el.bounds.width - measureTextWidth(line, el.fontSize)
                TextAlignment.JUSTIFY -> el.bounds.x
            }
            surface.drawText(line, x, y, el.fontSize, color, el.fontRole, el.weight, el.italic, el.alignment, el.letterSpacing)
            y += el.fontSize * el.lineSpacing
        }
    }

    /** Rough default measurement; the actual typography engine on Android overrides this. */
    private fun measureTextWidth(text: String, fontSize: Float): Float =
        text.length * fontSize * 0.55f

    private fun renderShape(el: DesignElement.Shape, surface: DrawSurface) {
        val b = el.bounds
        when (el.kind) {
            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECTANGLE ->
                surface.drawRect(b, el.fill, el.stroke, if (el.kind == ShapeKind.ROUNDED_RECTANGLE) el.cornerRadius else 0f)
            ShapeKind.ELLIPSE ->
                surface.drawEllipse(b.x + b.width / 2f, b.y + b.height / 2f, b.width / 2f, b.height / 2f, el.fill, el.stroke)
            ShapeKind.TRIANGLE -> {
                val p1 = b.x + b.width / 2f to b.y
                val p2 = b.x to b.y + b.height
                val p3 = b.x + b.width to b.y + b.height
                surface.drawTriangle(p1, p2, p3, el.fill, el.stroke)
            }
            ShapeKind.LINE -> {
                val s = el.stroke ?: StrokeSpec("#000000", b.height.coerceAtLeast(1f))
                surface.drawLine(b.x, b.y + b.height / 2f, b.x + b.width, b.y + b.height / 2f, s)
            }
            ShapeKind.POLYGON, ShapeKind.STAR -> {
                val sides = if (el.kind == ShapeKind.POLYGON) 6 else 5
                val pts = polygonPoints(sides, b.x + b.width / 2f, b.y + b.height / 2f, b.width / 2f, b.height / 2f)
                surface.drawPolygon(pts, el.fill, el.stroke)
            }
            ShapeKind.CUSTOM_PATH -> {
                // Custom path content is stored in element params — out of scope here.
                surface.drawRect(b, el.fill, el.stroke, 0f)
            }
        }
    }

    private fun polygonPoints(sides: Int, cx: Float, cy: Float, rx: Float, ry: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>(sides)
        for (i in 0 until sides) {
            val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / sides
            out.add((cx + cos(a) * rx) to (cy + sin(a) * ry))
        }
        return out
    }

    private fun renderProcedural(el: DesignElement.Procedural, surface: DrawSurface, quality: RenderQuality) {
        val eff = procedural.get(el.effect) ?: return
        surface.pushLayer()
        surface.setBlendMode(el.blendMode.toBlendMode())
        eff.render(seed = el.seed, bounds = el.bounds, params = el.params, surface = surface)
        surface.popLayer()
    }

    private fun renderImage(el: DesignElement.Image, surface: DrawSurface) {
        surface.drawImage(el.assetId, el.bounds, el.fit, el.cornerRadius)
    }
}
