package com.adnanfoisal.infinitydesign.core.util

/**
 * Hex colour utilities + parsing. Designed to reject anything malformed.
 *
 * Supported formats:
 *   #RGB              (12-bit)
 *   #RGBA             (16-bit)
 *   #RRGGBB           (24-bit)
 *   #RRGGBBAA         (32-bit)
 *   rgb(r,g,b)        (0..255)
 *   rgba(r,g,b,a)     (a 0..1)
 *
 * All normalised to an ARGB Int (the Android standard).
 */
object ColorUtil {

    private val HEX = Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
    private val RGB = Regex("""^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([01]?\.\d+)\s*)?\)$""", RegexOption.IGNORE_CASE)

    fun parseOrNull(input: String): Int? = try { parse(input) } catch (_: Throwable) { null }

    fun parse(input: String): Int {
        val s = input.trim()
        HEX.matchEntire(s)?.let { m ->
            val hex = m.groupValues[1]
            return when (hex.length) {
                3 -> expand(hex, 1) { c -> c.toString().repeat(2).toInt(16) }
                    .let { (r, g, b) -> argb(255, r, g, b) }
                4 -> expand(hex, 1) { c -> c.toString().repeat(2).toInt(16) }
                    .let { (r, g, b, a) -> argb(a, r, g, b) }
                6 -> argb(255, hex.substring(0,2).toInt(16), hex.substring(2,4).toInt(16), hex.substring(4,6).toInt(16))
                8 -> hex.substring(0,2).toInt(16).let { a -> argb(a, hex.substring(2,4).toInt(16), hex.substring(4,6).toInt(16), hex.substring(6,8).toInt(16)) }
                else -> null
            } ?: throw IllegalArgumentException("Bad hex color: $input")
        }
        RGB.matchEntire(s)?.let { m ->
            val r = m.groupValues[1].toInt()
            val g = m.groupValues[2].toInt()
            val b = m.groupValues[3].toInt()
            val a = if (m.groupValues[4].isEmpty()) 255 else (255f * m.groupValues[4].toFloat()).toInt()
            require(r in 0..255 && g in 0..255 && b in 0..255) { "rgb channels out of range in $input" }
            require(a in 0..255) { "alpha out of range in $input" }
            return argb(a, r, g, b)
        }
        throw IllegalArgumentException("Unrecognised color format: $input")
    }

    private inline fun expand(hex: String, ignored: Int, transform: (Char) -> Int): List<Int> = hex.map(transform)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun toHex(argb: Int, withAlpha: Boolean = false): String {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return if (withAlpha) "#%02X%02X%02X%02X".format(a, r, g, b)
        else "#%02X%02X%02X".format(r, g, b)
    }

    /** Linear interpolation in sRGB space (acceptable for non-perceptual use cases). */
    fun lerp(c1: Int, c2: Int, t: Float): Int {
        val a = ((c1 ushr 24) and 0xFF) + (((c2 ushr 24) and 0xFF) - ((c1 ushr 24) and 0xFF)) * t
        val r = ((c1 ushr 16) and 0xFF) + (((c2 ushr 16) and 0xFF) - ((c1 ushr 16) and 0xFF)) * t
        val g = ((c1 ushr 8) and 0xFF) + (((c2 ushr 8) and 0xFF) - ((c1 ushr 8) and 0xFF)) * t
        val b = (c1 and 0xFF) + ((c2 and 0xFF) - (c1 and 0xFF)) * t
        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    fun withAlpha(argb: Int, alpha: Float): Int {
        val a = (255 * SafeMath.clampSafe(alpha, 0f, 1f)).toInt()
        return (a shl 24) or (argb and 0x00FFFFFF)
    }
}
