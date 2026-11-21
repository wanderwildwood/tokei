package com.wanderwildwood.tokei.noise

import kotlin.math.abs

/** The sounds on offer for sleep, each made live from random numbers and a filter or two. */
enum class Preset(val key: String) {
    /** Pink noise, rounded off at the top: rain heard from indoors. */
    SOFT("soft"),

    /** Brown noise: a low rumble, like distant surf or a plane's cabin. */
    DEEP("deep"),

    /** White noise held to the middle of the range: a fan across the room. */
    FAN("fan");

    companion object {
        fun of(key: String?): Preset = values().firstOrNull { it.key == key } ?: SOFT
    }
}

/**
 * A stream of noise made as it is asked for, so nothing is ever a loop that could be heard to
 * come round. Mono, samples in -1..1. One instance per playing; not for two threads at once.
 */
class NoiseGen(private val preset: Preset, seed: Int = System.nanoTime().toInt()) {
    private var s = if (seed == 0) 0x2545F491 else seed

    // Paul Kellet's pink-noise filter state.
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    private var brown = 0f
    private var lowA = 0f
    private var lowB = 0f

    /** White noise, uniform in -1..1 (xorshift32). */
    private fun white(): Float {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x / 2147483648f
    }

    private fun next(): Float {
        val w = white()
        val y = when (preset) {
            Preset.SOFT -> {
                b0 = 0.99886f * b0 + w * 0.0555179f
                b1 = 0.99332f * b1 + w * 0.0750759f
                b2 = 0.96900f * b2 + w * 0.1538520f
                b3 = 0.86650f * b3 + w * 0.3104856f
                b4 = 0.55000f * b4 + w * 0.5329522f
                b5 = -0.7616f * b5 - w * 0.0168980f
                val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
                b6 = w * 0.115926f
                // Round off the top.
                lowA += 0.35f * (pink - lowA)
                lowA * 1.0f
            }
            Preset.DEEP -> {
                brown = 0.98039f * brown + 0.0196f * w
                brown * 3.0f
            }
            Preset.FAN -> {
                // Between about 150 Hz and 3 kHz at 44.1 kHz: a low-pass less a lower low-pass.
                lowA += 0.35f * (w - lowA)
                lowB += 0.02f * (w - lowB)
                (lowA - lowB) * 0.8f
            }
        }
        return if (y > 1f) 1f else if (y < -1f) -1f else y
    }

    /** Fills [out] with 16-bit samples, each scaled by [gain] (0..1). */
    fun fill(out: ShortArray, count: Int = out.size, gain: Float = 1f) {
        val g = gain.coerceIn(0f, 1f) * 32767f
        for (i in 0 until count) out[i] = (next() * g).toInt().toShort()
    }

    companion object {
        const val RATE = 44_100

        /** How the volume tails off over the last [fadeMs] of a timed sleep: 1 until then, 0 at the end. */
        fun fade(remainingMs: Long, fadeMs: Long): Float =
            if (remainingMs >= fadeMs) 1f else if (remainingMs <= 0L) 0f else remainingMs.toFloat() / fadeMs

        /** The same coming in, over [inMs] from [elapsedMs] of playing, so it never starts with a click. */
        fun fadeIn(elapsedMs: Long, inMs: Long): Float =
            if (elapsedMs >= inMs) 1f else if (elapsedMs <= 0L) 0f else elapsedMs.toFloat() / inMs

        /** Loudest sample of [x], for the tests. */
        internal fun peak(x: ShortArray): Int = x.maxOf { abs(it.toInt()) }
    }
}
