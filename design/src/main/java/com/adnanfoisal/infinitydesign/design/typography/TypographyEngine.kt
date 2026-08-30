package com.adnanfoisal.infinitydesign.design.typography

import com.adnanfoisal.infinitydesign.design.dsl.TextAlignment

/**
 * Typography engine abstraction. Section 20: real text measurement is mandatory.
 *
 * The Android implementation uses android.text.StaticLayout + Paint.measureText.
 * The pure-JVM test implementation provides a deterministic heuristic so the
 * candidate generator can be tested without device text shaping.
 *
 * Both must agree on the contract: measure returns a [MeasuredText] whose
 * `lines` field carries wrapped lines with their widths, and `width`/`height`
 * describe the bounding box. Auto-fit returns a best-effort font size.
 */
interface TypographyEngine {
    fun measure(text: String, fontRole: String, fontSize: Float, maxWidth: Float,
                alignment: TextAlignment = TextAlignment.LEFT,
                lineSpacing: Float = 1.2f, weight: Int = 400, italic: Boolean = false,
                letterSpacing: Float = 0f, truncate: Boolean = false): MeasuredText

    /** Find a font size in [min, max] that fits `text` into `maxWidth x maxHeight`. */
    fun autoFit(text: String, fontRole: String, maxWidth: Float, maxHeight: Float,
                min: Float = 8f, max: Float = 96f,
                alignment: TextAlignment = TextAlignment.LEFT,
                lineSpacing: Float = 1.2f, weight: Int = 400,
                letterSpacing: Float = 0f): Float
}

data class MeasuredText(
    val lines: List<MeasuredLine>,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val overflowed: Boolean,
) {
    val lineCount: Int get() = lines.size
}

data class MeasuredLine(val text: String, val width: Float, val ascent: Float, val descent: Float)

/**
 * Headless typography engine — deterministic per-character heuristic.
 *
 * Used in unit tests and in the candidate generator when running on the JVM.
 * Approximates Latin-script metrics. The Android implementation overrides this
 * with real StaticLayout measurement — see [AndroidTypographyEngine] in :app.
 */
class HeuristicTypographyEngine : TypographyEngine {

    private fun roleWidthFactor(role: String): Float = when (role) {
        "geometric-display" -> 0.58f
        "editorial-serif" -> 0.55f
        "neutral-sans" -> 0.52f
        "technical-mono" -> 0.60f
        "condensed-display" -> 0.46f
        "humanist-sans" -> 0.54f
        "display-serif" -> 0.60f
        else -> 0.55f
    }

    private fun weightFactor(weight: Int): Float = when {
        weight >= 700 -> 1.05f
        weight >= 500 -> 1.0f
        else -> 0.95f
    }

    private fun charWidth(ch: Char, fontSize: Float, role: String, weight: Int): Float {
        val base = roleWidthFactor(role) * weightFactor(weight) * fontSize
        return when {
            ch == ' ' -> base * 0.3f
            ch == 'i' || ch == 'l' || ch == '1' || ch == 'I' -> base * 0.3f
            ch == 'm' || ch == 'M' || ch == 'W' || ch == 'w' -> base * 1.2f
            ch.isUpperCase() -> base * 0.85f
            ch.isLetterOrDigit() -> base * 0.55f
            else -> base * 0.4f
        }
    }

    override fun measure(text: String, fontRole: String, fontSize: Float, maxWidth: Float,
                         alignment: TextAlignment, lineSpacing: Float, weight: Int,
                         italic: Boolean, letterSpacing: Float, truncate: Boolean): MeasuredText {
        if (fontSize <= 0f || maxWidth <= 0f) {
            return MeasuredText(emptyList(), 0f, 0f, fontSize, false)
        }
        if (text.isBlank()) {
            return MeasuredText(emptyList(), 0f, fontSize * lineSpacing, fontSize, false)
        }
        val paragraphs = text.split("\n")
        val lines = ArrayList<MeasuredLine>()
        var overflowed = false
        for (para in paragraphs) {
            if (para.isEmpty()) {
                lines.add(MeasuredLine("", 0f, fontSize * 0.8f, fontSize * 0.2f))
                continue
            }
            val words = para.split(" ")
            val cur = StringBuilder()
            var curWidth = 0f
            val spaceW = charWidth(' ', fontSize, fontRole, weight) + letterSpacing
            for (w in words) {
                val wWidth = w.sumOf { charWidth(it, fontSize, fontRole, weight).toDouble() }.toFloat() +
                    letterSpacing * w.length
                val withSpace = if (cur.isEmpty()) wWidth else curWidth + spaceW + wWidth
                if (withSpace <= maxWidth || cur.isEmpty()) {
                    if (cur.isNotEmpty()) { cur.append(' '); curWidth += spaceW }
                    cur.append(w); curWidth = withSpace
                } else {
                    lines.add(MeasuredLine(cur.toString(), curWidth, fontSize * 0.8f, fontSize * 0.2f))
                    cur.clear(); cur.append(w); curWidth = wWidth
                    if (truncate && lines.size >= 4) { overflowed = true; break }
                }
            }
            if (cur.isNotEmpty() && !overflowed) {
                lines.add(MeasuredLine(cur.toString(), curWidth, fontSize * 0.8f, fontSize * 0.2f))
            }
            if (overflowed) break
        }
        val lineH = fontSize * lineSpacing
        val totalH = lines.size * lineH
        val maxLineW = lines.maxOfOrNull { it.width } ?: 0f
        return MeasuredText(lines, maxLineW, totalH, fontSize, overflowed)
    }

    override fun autoFit(text: String, fontRole: String, maxWidth: Float, maxHeight: Float,
                         min: Float, max: Float, alignment: TextAlignment, lineSpacing: Float,
                         weight: Int, letterSpacing: Float): Float {
        if (maxWidth <= 0f || maxHeight <= 0f) return min
        var lo = min
        var hi = max
        var best = min
        repeat(12) {
            val mid = (lo + hi) / 2f
            val m = measure(text, fontRole, mid, maxWidth, alignment, lineSpacing, weight, false, letterSpacing)
            if (m.width <= maxWidth && m.height <= maxHeight && !m.overflowed) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        return best.coerceIn(min, max)
    }
}
