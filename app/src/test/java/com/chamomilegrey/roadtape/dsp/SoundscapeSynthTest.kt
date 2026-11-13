/*
 * roadtape v0.1.0 — JVM unit tests for the soundscape synthesizer
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/test/java/com/chamomilegrey/roadtape/dsp/SoundscapeSynthTest.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 level, ducking and safety-cap tests
 */
package com.chamomilegrey.roadtape.dsp

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundscapeSynthTest {

    private val sampleRate = 48_000

    /** RMS of one second of output after a two-second settle. Also checks every sample. */
    private fun rms(configure: SoundscapeSynth.() -> Unit): Double {
        val synth = SoundscapeSynth(sampleRate)
        synth.enabled = true
        synth.intensity = 1f
        synth.configure()
        val buffer = FloatArray(sampleRate * 2)
        synth.render(buffer, sampleRate)
        synth.render(buffer, sampleRate)
        synth.render(buffer, sampleRate)
        var sum = 0.0
        for (x in buffer) {
            assertTrue("sample must be finite", x.isFinite())
            assertTrue("sample must stay under the cap", abs(x) <= SoundscapeSynth.OUTPUT_CAP)
            sum += x.toDouble() * x
        }
        return sqrt(sum / buffer.size)
    }

    @Test
    fun disabledIsSilent() {
        assertEquals(0.0, rms { enabled = false; speedKph = 120f; wiperLevel = 3 }, 0.0)
    }

    @Test
    fun standstillWithoutRainIsNearSilent() {
        assertTrue(rms { speedKph = 0f } < 0.005)
    }

    @Test
    fun levelRisesWithSpeed() {
        val slow = rms { speedKph = 30f }
        val mid = rms { speedKph = 80f }
        val fast = rms { speedKph = 130f }
        assertTrue("30 < 80 km/h ($slow, $mid)", mid > slow * 1.5)
        assertTrue("80 < 130 km/h ($mid, $fast)", fast > mid * 1.2)
    }

    @Test
    fun wiperLevelAddsRain() {
        val dry = rms { speedKph = 0f }
        val light = rms { speedKph = 0f; wiperLevel = 1 }
        val heavy = rms { speedKph = 0f; wiperLevel = 3 }
        assertTrue("rain is audible at standstill ($dry, $light)", light > dry + 0.005)
        assertTrue("faster wipers mean heavier rain ($light, $heavy)", heavy > light * 1.5)
    }

    @Test
    fun openRealWindowDucksTheApp() {
        val closed = rms { speedKph = 80f }
        val open = rms { speedKph = 80f; windowOpen = 1f }
        assertTrue("open window lowers app level ($closed, $open)", open < closed * 0.4)
    }

    @Test
    fun tunnelAddsReverbEnergy() {
        val outside = rms { speedKph = 80f }
        val inside = rms { speedKph = 80f; inTunnel = true }
        assertTrue("tunnel is louder ($outside, $inside)", inside > outside * 1.1)
    }

    @Test
    fun lowerIntensityIsQuieter() {
        val low = rms { speedKph = 80f; intensity = 0.35f }
        val high = rms { speedKph = 80f; intensity = 1f }
        assertTrue("intensity scales level ($low, $high)", low < high * 0.6)
    }

    @Test
    fun focusLossSilences() {
        assertEquals(0.0, rms { speedKph = 120f; focusGain = 0f }, 1e-4)
    }

    @Test
    fun worstCaseStaysBounded() {
        // rms() asserts the cap on every sample.
        rms { speedKph = 250f; wiperLevel = 3; inTunnel = true; intensity = 1f }
    }
}
