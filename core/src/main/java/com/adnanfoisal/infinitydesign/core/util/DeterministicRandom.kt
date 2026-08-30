package com.adnanfoisal.infinitydesign.core.util

/**
 * Deterministic pseudo-random number generator.
 *
 * Same seed MUST always produce the same sequence, on every device, every JVM version.
 * The procedural graphics engine and the design candidate generator both depend on this.
 *
 * Uses SplitMix64 — well-tested, fast, statistically reasonable, no platform dependencies.
 */
class DeterministicRandom(seed: Long) {

    // Bit constants that exceed signed Long range — use ULong literals and convert back.
    private val GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
    private val MURMUR_M1: Long = 0xBF58476D1CE4E5B9uL.toLong()
    private val MURMUR_M2: Long = 0x94D049BB133111EBuL.toLong()

    private var state: Long = seed

    /** Next non-negative Long. */
    fun nextLong(): Long {
        // SplitMix64 step
        state = state + GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MURMUR_M1
        z = (z xor (z ushr 27)) * MURMUR_M2
        z = z xor (z ushr 31)
        return z
    }

    /** Next Int in [0, n). */
    fun nextInt(n: Int): Int {
        require(n > 0) { "n must be > 0" }
        return (nextLong() and 0x7FFFFFFFL).toInt() % n
    }

    /** Next Int across the full signed Int range. */
    fun nextInt(): Int = nextLong().toInt()

    /** Next float in [0, 1). */
    fun nextFloat(): Float = (nextLong() and 0x1FFFFFFFL).toFloat() / (0x1FFFFFFFL.toFloat() + 1f)

    /** Next float in [min, max). */
    fun nextFloat(min: Float, max: Float): Float {
        require(max > min) { "max must be > min" }
        return min + nextFloat() * (max - min)
    }

    /** True with probability p (0..1). */
    fun chance(p: Float): Boolean = nextFloat() < p

    /** Pick a random element from a non-empty list. */
    fun <T> pickFrom(list: List<T>): T {
        require(list.isNotEmpty()) { "list must not be empty" }
        return list[nextInt(list.size)]
    }

    /** Returns a shuffled copy of the list. */
    fun <T> shuffled(list: List<T>): List<T> {
        val copy = list.toMutableList()
        for (i in copy.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = copy[i]; copy[i] = copy[j]; copy[j] = tmp
        }
        return copy
    }

    /** Re-seed for sub-streams without exposing the caller to the raw state. */
    fun derive(seed: Long): DeterministicRandom =
        DeterministicRandom(this.nextLong() xor seed.toLong())
}
