package com.adnanfoisal.infinitydesign.core.math

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal 2D vector + affine transform. We avoid depending on a heavier math
 * library because the renderer hot-path needs this to be allocation-free for
 * common operations.
 */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = sqrt(x * x + y * y)
    fun normalize(): Vec2 {
        val l = length().takeIf { it > 1e-9f } ?: return Zero
        return Vec2(x / l, y / l)
    }
    companion object {
        val Zero = Vec2(0f, 0f)
        val X = Vec2(1f, 0f)
        val Y = Vec2(0f, 1f)
    }
}

data class RectF(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
    fun contains(p: Vec2): Boolean = p.x in x..right && p.y in y..bottom
    fun intersects(o: RectF): Boolean = !(o.x >= right || o.right <= x || o.y >= bottom || o.bottom <= y)
    fun translate(dx: Float, dy: Float) = RectF(x + dx, y + dy, width, height)
    fun expand(amount: Float) = RectF(x - amount, y - amount, width + 2 * amount, height + 2 * amount)
    companion object {
        val Zero = RectF(0f, 0f, 0f, 0f)
    }
}

object AffineMatrix {
    /**
     * Apply counter-clockwise rotation about origin by radians.
     * Returns [xx, xy, yx, yy, tx, ty] (row-major 2x3 affine).
     */
    fun rotation(rad: Float): FloatArray {
        val c = cos(rad); val s = sin(rad)
        return floatArrayOf(c, -s, s, c, 0f, 0f)
    }
    fun scale(sx: Float, sy: Float): FloatArray = floatArrayOf(sx, 0f, 0f, sy, 0f, 0f)
    fun translate(tx: Float, ty: Float): FloatArray = floatArrayOf(1f, 0f, 0f, 1f, tx, ty)

    fun apply(m: FloatArray, p: Vec2): Vec2 =
        Vec2(m[0] * p.x + m[1] * p.y + m[4], m[2] * p.x + m[3] * p.y + m[5])
}

object MathConstants {
    const val TAU = (2.0 * PI).toFloat()
    const val PHI = 1.6180339887f
}
