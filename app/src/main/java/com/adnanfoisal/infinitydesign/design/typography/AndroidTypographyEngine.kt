package com.adnanfoisal.infinitydesign.design.typography

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment

/**
 * Android typography engine — real text measurement using StaticLayout + Paint.
 *
 * Section 20: real text measurement is mandatory. NOT character-count heuristics.
 *
 * Returned dimensions exactly match what the renderer draws (because the renderer
 * uses the same Paint setup).
 */
class AndroidTypographyEngine(
    @Suppress("unused") private val context: Context,
) : TypographyEngine {

    override fun measure(text: String, fontRole: String, fontSize: Float, maxWidth: Float,
                         alignment: TextAlignment, lineSpacing: Float, weight: Int,
                         italic: Boolean, letterSpacing: Float, truncate: Boolean): MeasuredText {
        if (fontSize <= 0f || maxWidth <= 0f || text.isBlank()) {
            return MeasuredText(emptyList(), 0f, fontSize * lineSpacing, fontSize, false)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = fontSize
            typeface = pickTypeface(fontRole, weight, italic)
            this.letterSpacing = letterSpacing
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt().coerceAtLeast(1))
            .setAlignment(mapAlignment(alignment))
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        val lines = ArrayList<MeasuredLine>(layout.lineCount)
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val lineText = text.substring(start, end)
            val lineW = layout.getLineRight(i) - layout.getLineLeft(i)
            val ascent = -layout.getLineAscent(i).toFloat()
            val descent = layout.getLineDescent(i).toFloat()
            lines.add(MeasuredLine(lineText, lineW, ascent, descent))
        }
        val totalH = layout.height.toFloat()
        val maxW = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
        return MeasuredText(lines, maxW, totalH, fontSize, false)
    }

    override fun autoFit(text: String, fontRole: String, maxWidth: Float, maxHeight: Float,
                         min: Float, max: Float, alignment: TextAlignment, lineSpacing: Float,
                         weight: Int, letterSpacing: Float): Float {
        if (maxWidth <= 0f || maxHeight <= 0f) return min
        var lo = min
        var hi = max
        var best = min
        repeat(8) {
            val mid = (lo + hi) / 2f
            val m = measure(text, fontRole, mid, maxWidth, alignment, lineSpacing, weight, false, letterSpacing)
            if (m.width <= maxWidth && m.height <= maxHeight && !m.overflowed) {
                best = mid; lo = mid
            } else {
                hi = mid
            }
        }
        return best.coerceIn(min, max)
    }

    private fun mapAlignment(a: TextAlignment): Layout.Alignment = when (a) {
        TextAlignment.LEFT, TextAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun pickTypeface(role: String, weight: Int, italic: Boolean): Typeface {
        val base = when (role) {
            "geometric-display", "condensed-display", "neutral-sans", "humanist-sans" -> Typeface.SANS_SERIF
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
