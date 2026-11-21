package com.wanderwildwood.tokei.noise

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseGenTest {

    private fun run(p: Preset, seed: Int = 12345, n: Int = NoiseGen.RATE * 10): ShortArray {
        val out = ShortArray(n)
        NoiseGen(p, seed).fill(out)
        return out
    }

    @Test
    fun everySoundStaysInRangeIsNotSilentAndHasNoDrift() {
        for (p in Preset.values()) {
            val x = run(p)
            val mean = x.fold(0.0) { a, v -> a + v } / x.size
            val rms = sqrt(x.fold(0.0) { a, v -> a + v.toDouble() * v } / x.size) / 32767.0
            assertTrue("$p rms $rms", rms in 0.05..0.35)
            assertTrue("$p mean $mean", abs(mean) / 32767.0 < 0.02)
            assertTrue("$p peak", NoiseGen.peak(x) <= 32767)
            // Not stuck: plenty of distinct values.
            assertTrue(x.toSet().size > 1000)
        }
    }

    @Test
    fun filtersStayStableOverALongRun() {
        for (p in Preset.values()) {
            val g = NoiseGen(p, 99)
            val buf = ShortArray(NoiseGen.RATE)
            var last = 0.0
            repeat(60) {
                g.fill(buf)
                last = sqrt(buf.fold(0.0) { a, v -> a + v.toDouble() * v } / buf.size)
            }
            assertTrue("$p $last", last > 1000 && last < 12000)
        }
    }

    @Test
    fun sameSeedSameNoiseDifferentSeedNot() {
        assertTrue(run(Preset.FAN, 7, 1000).contentEquals(run(Preset.FAN, 7, 1000)))
        assertTrue(!run(Preset.FAN, 7, 1000).contentEquals(run(Preset.FAN, 8, 1000)))
        // Seed zero would stick a xorshift at zero.
        assertTrue(run(Preset.SOFT, 0, 1000).any { it.toInt() != 0 })
    }

    @Test
    fun gainScalesAndClamps() {
        val a = ShortArray(1000)
        NoiseGen(Preset.DEEP, 5).fill(a, gain = 0f)
        assertTrue(a.all { it.toInt() == 0 })
        val b = ShortArray(1000)
        NoiseGen(Preset.DEEP, 5).fill(b, gain = 7f)
        assertTrue(NoiseGen.peak(b) <= 32767)
    }

    @Test
    fun deepIsLowerThanFan() {
        // Brown noise changes slowly from sample to sample; the fan's band does so less.
        fun roughness(x: ShortArray): Double {
            var d = 0.0; var e = 0.0
            for (i in 1 until x.size) { d += abs(x[i] - x[i - 1]).toDouble(); e += abs(x[i].toDouble()) }
            return d / e
        }
        assertTrue(roughness(run(Preset.DEEP, 3, 100000)) < roughness(run(Preset.FAN, 3, 100000)))
    }

    @Test
    fun fadesRunFromOneToZero() {
        assertEquals(1f, NoiseGen.fade(60_000, 15_000), 0f)
        assertEquals(0.5f, NoiseGen.fade(7_500, 15_000), 0.0001f)
        assertEquals(0f, NoiseGen.fade(0, 15_000), 0f)
        assertEquals(0f, NoiseGen.fade(-5, 15_000), 0f)
        assertEquals(0f, NoiseGen.fadeIn(0, 1_500), 0f)
        assertEquals(1f, NoiseGen.fadeIn(5_000, 1_500), 0f)
    }

    @Test
    fun presetKeysRoundTrip() {
        for (p in Preset.values()) assertEquals(p, Preset.of(p.key))
        assertEquals(Preset.SOFT, Preset.of("nonsense"))
        assertEquals(Preset.SOFT, Preset.of(null))
    }
}
