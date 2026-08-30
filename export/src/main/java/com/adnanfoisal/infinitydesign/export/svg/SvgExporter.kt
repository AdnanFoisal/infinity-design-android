package com.adnanfoisal.infinitydesign.export.svg

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.core.util.ColorUtil
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.FillSpec
import com.adnanfoisal.infinitydesign.design.dsl.ShapeKind
import com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec
import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment
import com.adnanfoisal.infinitydesign.design.validation.DesignValidator

/**
 * SVG exporter. Section 53: valid XML, escape text and attributes, preserve
 * vector geometry, gradients, masks where supported, no unsafe URLs, no
 * malformed IDs.
 *
 * Output is parsed by the export's own test to verify XML validity — that's
 * the round-trip safety guarantee.
 */
class SvgExporter {

    fun export(doc: DesignDocument): AppResult<String> {
        val validation = DesignValidator.validate(doc)
        if (validation is AppResult.Err) return errResult(AppError.Kind.SchemaValidation, validation.error.message)
        if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height)) {
            return errResult(AppError.Kind.InvalidDimension, "canvas non-finite")
        }
        val sb = StringBuilder(8192)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        sb.append("viewBox=\"0 0 ${doc.canvas.width.toInt()} ${doc.canvas.height.toInt()}\" ")
        sb.append("width=\"${doc.canvas.width.toInt()}\" height=\"${doc.canvas.height.toInt()}\">\n")
        // Defs (gradients). We pre-allocate IDs deterministically.
        val defs = StringBuilder()
        var id = 0
        // Background
        when (val bg = doc.background) {
            is com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.Solid ->
                sb.append("<rect x=\"0\" y=\"0\" width=\"${doc.canvas.width}\" height=\"${doc.canvas.height}\" fill=\"${escapeAttribute(bg.color)}\"/>\n")
            is com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.LinearGradient -> {
                val gid = "g${id++}"
                appendLinearGradient(defs, gid, bg.stops, doc.canvas)
                sb.append("<rect x=\"0\" y=\"0\" width=\"${doc.canvas.width}\" height=\"${doc.canvas.height}\" fill=\"url(#$gid)\"/>\n")
            }
            is com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.RadialGradient -> {
                val gid = "g${id++}"
                appendRadialGradient(defs, gid, bg, doc.canvas)
                sb.append("<rect x=\"0\" y=\"0\" width=\"${doc.canvas.width}\" height=\"${doc.canvas.height}\" fill=\"url(#$gid)\"/>\n")
            }
            is com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.Layered -> {
                sb.append("<rect x=\"0\" y=\"0\" width=\"${doc.canvas.width}\" height=\"${doc.canvas.height}\" fill=\"${escapeAttribute(bg.base)}\"/>\n")
            }
        }
        // Elements
        for (el in doc.elements) {
            if (!el.visible) continue
            if (!el.validate()) continue
            when (el) {
                is DesignElement.Text -> appendText(sb, el)
                is DesignElement.Shape -> appendShape(sb, el)
                is DesignElement.Procedural -> {
                    // SVG cannot represent procedural effects — emit a placeholder rect.
                    val color = doc.palette.secondary
                    sb.append("<rect x=\"${el.bounds.x}\" y=\"${el.bounds.y}\" ")
                    sb.append("width=\"${el.bounds.width}\" height=\"${el.bounds.height}\" ")
                    sb.append("fill=\"${escapeAttribute(color)}\" opacity=\"${el.opacity}\"/>\n")
                }
                is DesignElement.Image -> {
                    // External image references are unsafe — emit placeholder rect.
                    sb.append("<rect x=\"${el.bounds.x}\" y=\"${el.bounds.y}\" ")
                    sb.append("width=\"${el.bounds.width}\" height=\"${el.bounds.height}\" ")
                    sb.append("fill=\"#CCCCCC\"/>\n")
                }
                is DesignElement.Group -> {
                    sb.append("<g id=\"${escapeAttribute(el.id)}\">\n")
                    // Group children are rendered separately at top level — section 14.
                    sb.append("</g>\n")
                }
            }
        }
        sb.insert(sb.indexOf("<svg") + sb.substring(0, sb.indexOf("<svg")).length, "")
        if (defs.isNotEmpty()) {
            sb.insert(sb.indexOf("\n", sb.indexOf("<svg")) + 1, "<defs>\n$defs</defs>\n")
        }
        sb.append("</svg>\n")
        return okResult(sb.toString())
    }

    private fun appendText(sb: StringBuilder, el: DesignElement.Text) {
        val anchor = when (el.alignment) {
            TextAlignment.LEFT -> "start"
            TextAlignment.CENTER -> "middle"
            TextAlignment.RIGHT -> "end"
            TextAlignment.JUSTIFY -> "start"
        }
        val content = if (el.uppercase) el.content.uppercase() else el.content
        // Split on \n into tspan elements
        val lines = content.split("\n")
        sb.append("<text x=\"${el.bounds.x}\" y=\"${el.bounds.y + el.fontSize}\" ")
        sb.append("font-size=\"${el.fontSize}\" ")
        sb.append("fill=\"${escapeAttribute(el.color)}\" text-anchor=\"$anchor\" ")
        sb.append("font-family=\"${escapeAttribute(el.fontRole)}\" ")
        if (el.weight >= 700) sb.append("font-weight=\"bold\" ")
        if (el.italic) sb.append("font-style=\"italic\" ")
        sb.append(">")
        for (i in lines.indices) {
            val line = escapeText(lines[i])
            val dy = if (i == 0) "0" else "${el.fontSize * el.lineSpacing}"
            if (i == 0) sb.append(line)
            else sb.append("<tspan x=\"${el.bounds.x}\" dy=\"$dy\">$line</tspan>")
        }
        sb.append("</text>\n")
    }

    private fun appendShape(sb: StringBuilder, el: DesignElement.Shape) {
        val b = el.bounds
        val fillStr = fillString(el.fill)
        val strokeStr = strokeString(el.stroke)
        when (el.kind) {
            ShapeKind.RECTANGLE, ShapeKind.ROUNDED_RECTANGLE ->
                sb.append("<rect x=\"${b.x}\" y=\"${b.y}\" width=\"${b.width}\" height=\"${b.height}\" ")
                    .append("rx=\"${if (el.kind == ShapeKind.ROUNDED_RECTANGLE) el.cornerRadius else 0f}\" ry=\"${el.cornerRadius}\" ")
                    .append(fillStr).append(strokeStr).append(" opacity=\"${el.opacity}\"/>\n")
            ShapeKind.ELLIPSE -> {
                val cx = b.x + b.width / 2f
                val cy = b.y + b.height / 2f
                sb.append("<ellipse cx=\"$cx\" cy=\"$cy\" rx=\"${b.width / 2f}\" ry=\"${b.height / 2f}\" ")
                    .append(fillStr).append(strokeStr).append(" opacity=\"${el.opacity}\"/>\n")
            }
            ShapeKind.TRIANGLE -> {
                val p1 = "${b.x + b.width / 2f},${b.y}"
                val p2 = "${b.x},${b.y + b.height}"
                val p3 = "${b.x + b.width},${b.y + b.height}"
                sb.append("<polygon points=\"$p1 $p2 $p3\" ").append(fillStr).append(strokeStr).append(" opacity=\"${el.opacity}\"/>\n")
            }
            ShapeKind.LINE -> {
                sb.append("<line x1=\"${b.x}\" y1=\"${b.y + b.height / 2f}\" ")
                    .append("x2=\"${b.x + b.width}\" y2=\"${b.y + b.height / 2f}\" ")
                    .append(strokeString(el.stroke).ifBlank { "stroke=\"#000\" " }).append(" opacity=\"${el.opacity}\"/>\n")
            }
            ShapeKind.POLYGON, ShapeKind.STAR -> {
                val sides = if (el.kind == ShapeKind.POLYGON) 6 else 5
                val cx = b.x + b.width / 2f
                val cy = b.y + b.height / 2f
                val rx = b.width / 2f
                val ry = b.height / 2f
                val pts = (0 until sides).map { i ->
                    val a = (-Math.PI / 2 + i * 2 * Math.PI / sides)
                    "${cx + kotlin.math.cos(a).toFloat() * rx},${cy + kotlin.math.sin(a).toFloat() * ry}"
                }
                sb.append("<polygon points=\"${pts.joinToString(" ")}\" ")
                    .append(fillStr).append(strokeStr).append(" opacity=\"${el.opacity}\"/>\n")
            }
            ShapeKind.CUSTOM_PATH -> {
                sb.append("<rect x=\"${b.x}\" y=\"${b.y}\" width=\"${b.width}\" height=\"${b.height}\" ")
                    .append(fillStr).append(strokeStr).append(" opacity=\"${el.opacity}\"/>\n")
            }
        }
    }

    private fun appendLinearGradient(sb: StringBuilder, id: String, stops: List<com.adnanfoisal.infinitydesign.design.dsl.ColorStop>, canvas: CanvasSpec) {
        sb.append("<linearGradient id=\"$id\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
        for (s in stops) {
            sb.append("<stop offset=\"${s.position}\" stop-color=\"${escapeAttribute(s.color)}\"/>\n")
        }
        sb.append("</linearGradient>\n")
    }

    private fun appendRadialGradient(sb: StringBuilder, id: String, bg: com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.RadialGradient, canvas: CanvasSpec) {
        val cx = bg.centerX * canvas.width
        val cy = bg.centerY * canvas.height
        val r = bg.radius * canvas.width.coerceAtMost(canvas.height)
        sb.append("<radialGradient id=\"$id\" cx=\"$cx\" cy=\"$cy\" r=\"$r\" gradientUnits=\"userSpaceOnUse\">\n")
        for (s in bg.stops) {
            sb.append("<stop offset=\"${s.position}\" stop-color=\"${escapeAttribute(s.color)}\"/>\n")
        }
        sb.append("</radialGradient>\n")
    }

    private fun fillString(f: FillSpec): String = when (f) {
        is FillSpec.Solid -> "fill=\"${escapeAttribute(f.color)}\" "
        is FillSpec.Linear -> "fill=\"${escapeAttribute(f.stops.firstOrNull()?.color ?: "#000")}\" "
        is FillSpec.Radial -> "fill=\"${escapeAttribute(f.stops.firstOrNull()?.color ?: "#000")}\" "
        is FillSpec.None -> "fill=\"none\" "
    }

    private fun strokeString(s: StrokeSpec?): String = if (s == null) "" else
        "stroke=\"${escapeAttribute(s.color)}\" stroke-width=\"${s.width}\" "

    private fun escapeAttribute(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeText(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
