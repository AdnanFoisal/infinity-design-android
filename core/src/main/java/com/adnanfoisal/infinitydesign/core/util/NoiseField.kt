package com.adnanfoisal.infinitydesign.core.util

import kotlin.math.PI
import kotlin.math.sin

/**
 * Value-noise + gradient noise functions used by procedural graphics.
 *
 * Deterministic on every device — we deliberately avoid java.util.Random so
 * the same seed reproduces identical art across Android, CI and JVM tests.
 */
object NoiseField {

    /** Smoothstep easing. */
    fun smoothstep(t: Float): Float = t * t * (3 - 2 * t)

    /** Perlin-style 2D gradient noise. */
    fun gradient(seed: Long, x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x.toDouble()).toInt()
        val yi = kotlin.math.floor(y.toDouble()).toInt()
        val xf = x - xi
        val yf = y - yi
        val tl = valueAt(seed, xi, yi)
        val tr = valueAt(seed, xi + 1, yi)
        val bl = valueAt(seed, xi, yi + 1)
        val br = valueAt(seed, xi + 1, yi + 1)
        val u = smoothstep(xf)
        val v = smoothstep(yf)
        val top = lerp(tl, tr, u)
        val bot = lerp(bl, br, u)
        return lerp(top, bot, v) * 2f - 1f
    }

    /** Fractal brownian motion: stacked gradient noise for richer detail. */
    fun fbm(seed: Long, x: Float, y: Float, octaves: Int = 4, lacunarity: Float = 2f, gain: Float = 0.5f): Float {
        var amp = 0.5f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        for (i in 0 until octaves) {
            sum += amp * gradient(seed, x * freq, y * freq)
            norm += amp
            amp *= gain
            freq *= lacunarity
        }
        return (sum / norm.coerceAtLeast(1e-6f))
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun valueAt(seed: Long, x: Int, y: Int): Float {
        // Hash to [0,1)
        val h = hashSeed(seed, x, y)
        return (h and 0x0FFFFFFFL).toFloat() / (0x0FFFFFFFL.toFloat() + 1f)
    }

    /**
     * Stable hash for integer lattice points.
     */
    private fun hashSeed(seed: Long, x: Int, y: Int): Long {
        val GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
        val M1: Long = 0xBF58476D1CE4E5B9uL.toLong()
        val M2: Long = 0x94D049BB133111EBuL.toLong()
        var s = seed xor (x.toLong() * GAMMA) xor (y.toLong() * M1)
        s = (s xor (s ushr 30)) * M1
        s = (s xor (s ushr 27)) * M2
        s = s xor (s ushr 31)
        return s
    }

    /** Worley-style nearest distance (cheap variant). Returns value in [0, 1]. */
    fun worley(seed: Long, x: Float, y: Float, cellSize: Float): Float {
        val inv = 1f / cellSize.coerceAtLeast(1e-3f)
        val xi = kotlin.math.floor(x * inv).toInt()
        val yi = kotlin.math.floor(y * inv).toInt()
        var minDist = Float.MAX_VALUE
        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = (xi + dx + hash01(seed, xi + dx, yi + dy)) / inv
                val py = (yi + dy + hash01(seed, (xi + dx) * 13, yi + dy) ) / inv
                val d2 = (px - x) * (px - x) + (py - y) * (py - y)
                if (d2 < minDist) minDist = d2
            }
        }
        return kotlin.math.sqrt(minDist).coerceIn(0f, 1f)
    }

    private fun hash01(seed: Long, x: Int, y: Int): Float {
        val GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
        val M1: Long = 0xBF58476D1CE4E5B9uL.toLong()
        val M2: Long = 0x94D049BB133111EBuL.toLong()
        var s = seed xor (x.toLong() * GAMMA) xor (y.toLong() * M1)
        s = (s xor (s ushr 30)) * M1
        s = (s xor (s ushr 27)) * M2
        s = s xor (s ushr 31)
        return (s and 0xFFFFL).toFloat() / 65536f
    }

    /** Sine-of-sines pattern for soft organic blobs without RNG. */
    fun organicWave(x: Float, y: Float, t: Float, freq: Float, amp: Float): Float =
        (sin(x * freq + t) * sin(y * freq * MathConstants_PHI + t * 1.3f) * amp)

    @Suppress("ConstVal", "ObjectPropertyName")
    private const val MathConstants_PHI = 1.6180339887f
}
