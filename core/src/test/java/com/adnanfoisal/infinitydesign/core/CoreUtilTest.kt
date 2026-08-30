package com.adnanfoisal.infinitydesign.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeMathTest {

    @Test fun `positive rejects zero and negatives`() {
        assertThat(SafeMath.requirePositive(1f)).isEqualTo(1f)
        try { SafeMath.requirePositive(0f); assert(false) } catch (_: Throwable) {}
        try { SafeMath.requirePositive(-1f); assert(false) } catch (_: Throwable) {}
        try { SafeMath.requirePositive(Float.NaN); assert(false) } catch (_: Throwable) {}
        try { SafeMath.requirePositive(Float.POSITIVE_INFINITY); assert(false) } catch (_: Throwable) {}
    }

    @Test fun `sanitize replaces NaN with fallback`() {
        assertThat(SafeMath.sanitize(Float.NaN)).isEqualTo(0f)
        assertThat(SafeMath.sanitize(Float.POSITIVE_INFINITY, 5f)).isEqualTo(5f)
        assertThat(SafeMath.sanitize(3.14f, 0f)).isEqualTo(3.14f)
    }

    @Test fun `clampSafe clamps and sanitises`() {
        assertThat(SafeMath.clampSafe(5f, 0f, 10f)).isEqualTo(5f)
        assertThat(SafeMath.clampSafe(20f, 0f, 10f)).isEqualTo(10f)
        assertThat(SafeMath.clampSafe(-5f, 0f, 10f)).isEqualTo(0f)
        assertThat(SafeMath.clampSafe(Float.NaN, 0f, 10f)).isEqualTo(0f)
    }

    @Test fun `allFinite detects invalid inputs`() {
        assertThat(SafeMath.allFinite(1f, 2f, 3f)).isTrue()
        assertThat(SafeMath.allFinite(1f, Float.NaN, 3f)).isFalse()
        assertThat(SafeMath.allFinite(Float.POSITIVE_INFINITY)).isFalse()
    }
}

class DeterministicRandomTest {

    @Test fun `same seed produces identical sequence`() {
        val a = DeterministicRandom(12345L)
        val b = DeterministicRandom(12345L)
        repeat(100) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong())
        }
    }

    @Test fun `nextFloat stays in range`() {
        val r = DeterministicRandom(7L)
        repeat(1000) {
            val f = r.nextFloat()
            assertThat(f).isAtLeast(0f)
            assertThat(f).isLessThan(1f)
        }
    }

    @Test fun `nextFloat min max stays in range`() {
        val r = DeterministicRandom(99L)
        repeat(1000) {
            val f = r.nextFloat(10f, 20f)
            assertThat(f).isAtLeast(10f)
            assertThat(f).isLessThan(20f)
        }
    }

    @Test fun `pickFrom returns element from list`() {
        val r = DeterministicRandom(42L)
        val list = listOf("a", "b", "c")
        repeat(100) {
            assertThat(list).contains(r.pickFrom(list))
        }
    }
}

class ColorUtilTest {

    @Test fun `parse hex 6 digit`() {
        val c = ColorUtil.parse("#FF0000")
        assertThat((c ushr 16) and 0xFF).isEqualTo(255)
        assertThat((c ushr 8) and 0xFF).isEqualTo(0)
        assertThat(c and 0xFF).isEqualTo(0)
        assertThat((c ushr 24) and 0xFF).isEqualTo(255)
    }

    @Test fun `parse hex 8 digit with alpha`() {
        val c = ColorUtil.parse("#80FF0000")
        assertThat((c ushr 24) and 0xFF).isEqualTo(0x80)
        assertThat((c ushr 16) and 0xFF).isEqualTo(255)
    }

    @Test fun `parse rgba literal`() {
        val c = ColorUtil.parse("rgba(255, 0, 0, 0.5)")
        assertThat((c ushr 24) and 0xFF).isAtLeast(120)
        assertThat((c ushr 24) and 0xFF).isAtMost(130)
    }

    @Test fun `invalid color throws`() {
        for (s in listOf("", "not-a-color", "#FFF00000", "rgb(999,0,0)", "#zzz")) {
            try {
                ColorUtil.parse(s); assert(false) { "should reject $s" }
            } catch (_: Throwable) {}
        }
    }

    @Test fun `toHex round trips`() {
        val c = ColorUtil.parse("#123456")
        assertThat(ColorUtil.toHex(c)).isEqualTo("#123456")
        assertThat(ColorUtil.toHex(c, withAlpha = true)).isEqualTo("#FF123456")
    }
}

class NoiseFieldTest {

    @Test fun `gradient noise stays in range`() {
        repeat(200) { i ->
            val v = NoiseField.gradient(123L, i * 0.13f, i * 0.27f)
            assertThat(v).isAtMost(1.0001f)
            assertThat(v).isAtLeast(-1.0001f)
        }
    }

    @Test fun `same seed same noise`() {
        for (i in 0..50) {
            val a = NoiseField.fbm(99L, i * 0.1f, i * 0.2f)
            val b = NoiseField.fbm(99L, i * 0.1f, i * 0.2f)
            assertThat(a).isEqualTo(b)
    }
    }
}
