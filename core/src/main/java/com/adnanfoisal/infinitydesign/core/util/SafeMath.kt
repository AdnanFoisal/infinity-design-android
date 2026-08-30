package com.adnanfoisal.infinitydesign.core.util

import kotlin.math.abs

/**
 * Centralised guard against NaN/Infinity leaking into rendering maths.
 *
 * The Design spec section 47 mandates: "Never allow NaN or Infinity into the design".
 * Every numeric entering the renderer must pass through here.
 */
object SafeMath {

    private val EPS = 1e-9f

    /** Finite and positive (>0). Use for widths, heights, font sizes. */
    fun requirePositive(v: Float, name: String = "value"): Float {
        require(!v.isNaN()) { "$name is NaN" }
        require(!v.isInfinite()) { "$name is Infinite" }
        require(v > 0f) { "$name must be > 0, got $v" }
        return v
    }

    /** Finite and non-negative (>=0). Use for opacities, radii. */
    fun requireNonNegative(v: Float, name: String = "value"): Float {
        require(!v.isNaN()) { "$name is NaN" }
        require(!v.isInfinite()) { "$name is Infinite" }
        require(v >= 0f) { "$name must be >= 0, got $v" }
        return v
    }

    /** Finite. Sign unrestricted. Use for coordinates. */
    fun requireFinite(v: Float, name: String = "value"): Float {
        require(!v.isNaN()) { "$name is NaN" }
        require(!v.isInfinite()) { "$name is Infinite" }
        return v
    }

    /** Sanitise any float into a finite value (replaces NaN/Inf with fallback). */
    @JvmStatic
    fun sanitize(v: Float, fallback: Float = 0f): Float = when {
        v.isNaN() -> fallback
        v.isInfinite() -> fallback
        else -> v
    }

    /** Clamp v into [min, max] without throwing on NaN/Inf. */
    fun clampSafe(v: Float, min: Float, max: Float): Float {
        val x = sanitize(v, min)
        return if (x < min) min else if (x > max) max else x
    }

    /** True if all numbers in the vararg are finite. */
    fun allFinite(vararg values: Float): Boolean = values.all { !it.isNaN() && !it.isInfinite() }

    fun approxEqual(a: Float, b: Float, tolerance: Float = 1e-4f): Boolean = abs(a - b) < tolerance

    fun approxZero(v: Float, tolerance: Float = EPS): Boolean = abs(v) < tolerance
}
