/*
 * roadtape v0.1.0 — small DSP building blocks (noise source, filters, comb delay)
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/main/java/com/chamomilegrey/roadtape/dsp/Filters.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 initial DSP primitives
 */
package com.chamomilegrey.roadtape.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.tan

/** Fast xorshift32 white-noise source. Deterministic for a given seed. */
internal class Noise(seed: Int) {
    private var state = if (seed == 0) 0x1234567 else seed

    /** Uniform sample in [-1, 1). */
    fun next(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x.toFloat() * 4.656613e-10f
    }

    /** Uniform sample in [0, 1). */
    fun next01(): Float = (next() + 1f) * 0.5f
}

/** One-pole low-pass filter. */
internal class OnePole {
    private var z = 0f
    private var a = 1f

    fun setCutoff(hz: Float, sampleRate: Int) {
        a = (1.0 - exp(-2.0 * PI * hz / sampleRate)).toFloat()
    }

    fun process(x: Float): Float {
        z += a * (x - z)
        return z
    }
}

/** Topology-preserving state-variable filter; exposes low-, band- and high-pass outputs. */
internal class Svf {
    private var ic1 = 0f
    private var ic2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1f

    var lp = 0f
        private set
    var bp = 0f
        private set
    var hp = 0f
        private set

    fun set(hz: Float, q: Float, sampleRate: Int) {
        val fc = hz.coerceIn(20f, sampleRate * 0.45f)
        val g = tan(PI * fc / sampleRate).toFloat()
        k = 1f / q
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun process(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        lp = v2
        bp = v1
        hp = x - k * v1 - v2
    }
}

/** Feedback comb filter with a damped loop; used for the tunnel reverb. */
internal class Comb(delaySamples: Int, private val feedback: Float, private val damping: Float) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0
    private var loopState = 0f

    fun process(x: Float): Float {
        val out = buffer[index]
        loopState += damping * (out - loopState)
        buffer[index] = x + loopState * feedback
        index++
        if (index == buffer.size) index = 0
        return out
    }
}
