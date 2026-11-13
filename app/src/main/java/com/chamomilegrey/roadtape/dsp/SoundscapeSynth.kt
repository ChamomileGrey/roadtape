/*
 * roadtape v0.1.0 — real-time "outside sound" synthesizer driven by vehicle state
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/main/java/com/chamomilegrey/roadtape/dsp/SoundscapeSynth.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 wind, road, rain and tunnel layers
 */
package com.chamomilegrey.roadtape.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generates the soundscape procedurally (filtered noise, no recorded samples):
 *
 *  - wind:   band-passed noise; level and brightness rise with speed, slow gusts
 *  - road:   low-passed noise rumble; level rises with speed
 *  - rain:   high-passed hiss plus resonant droplets; density follows wiper level
 *  - tunnel: damped comb reverb mixed in while inside a tunnel
 *
 * Inputs are plain fields written from any thread; [render] smooths them so
 * changes never click. Has no Android dependency, so it is unit-tested on the JVM.
 */
class SoundscapeSynth(private val sampleRate: Int) {

    /** Master switch ("open window" mode). */
    @Volatile var enabled = false

    /** Vehicle speed in km/h. */
    @Volatile var speedKph = 0f

    /** Wiper stage: 0 = off … 3 = fastest. Drives rain intensity. */
    @Volatile var wiperLevel = 0

    /** Real window opening, 0 = closed … 1 = fully open. Ducks the synthetic sound. */
    @Volatile var windowOpen = 0f

    /** True while driving through a tunnel. */
    @Volatile var inTunnel = false

    /** User-selected intensity, 0 … 1. */
    @Volatile var intensity = 0.65f

    /** Audio-focus gain, 0 … 1 (set by the audio engine when ducked). */
    @Volatile var focusGain = 1f

    private val noiseL = Noise(0x2F6E2B1)
    private val noiseR = Noise(0x51A7C3D)
    private val noiseRoad = Noise(0x7B3D9F5)

    private val windL = Svf()
    private val windR = Svf()
    private val hissL = Svf()
    private val hissR = Svf()
    private val dropL = Svf()
    private val dropR = Svf()
    private val road1 = OnePole()
    private val road2 = OnePole()

    private val combsL = combBank(floatArrayOf(31.7f, 37.1f, 41.9f))
    private val combsR = combBank(floatArrayOf(33.3f, 38.9f, 43.3f))

    private var sSpeed = 0f
    private var sRain = 0f
    private var sTunnel = 0f
    private var sMaster = 0f
    private var gustPhase1 = 0.0
    private var gustPhase2 = 1.7

    /** Renders [frames] stereo frames into [out] (interleaved L, R). */
    fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = min(BLOCK, frames - done)
            renderBlock(out, done * 2, n)
            done += n
        }
    }

    private fun renderBlock(out: FloatArray, offset: Int, n: Int) {
        // Parameter smoothing (time constant ≈ 120 ms).
        val k = (1.0 - exp(-n / (sampleRate * 0.12))).toFloat()
        sSpeed += k * ((speedKph / MAX_SPEED_KPH).coerceIn(0f, 1f) - sSpeed)
        sRain += k * (wiperLevel.coerceIn(0, 3) / 3f - sRain)
        sTunnel += k * ((if (inTunnel) 1f else 0f) - sTunnel)

        val masterTarget = (if (enabled) 1f else 0f) *
            intensity.coerceIn(0f, 1f) *
            (1f - WINDOW_DUCK * windowOpen.coerceIn(0f, 1f)) *
            focusGain.coerceIn(0f, 1f)
        val masterStart = sMaster
        sMaster += k * (masterTarget - sMaster)
        val masterStep = (sMaster - masterStart) / n

        if (masterStart < SILENCE && sMaster < SILENCE) {
            out.fill(0f, offset, offset + n * 2)
            return
        }

        val s = sSpeed
        val rain = sRain
        val tunnel = sTunnel

        // Slow gusts: two incommensurate LFOs, slightly different per channel.
        val dt = n.toDouble() / sampleRate
        gustPhase1 += 2.0 * PI * 0.13 * dt
        gustPhase2 += 2.0 * PI * 0.31 * dt
        val gustL = 1f + 0.22f * sin(gustPhase1).toFloat() + 0.12f * sin(gustPhase2).toFloat()
        val gustR = 1f + 0.22f * sin(gustPhase1 + 0.9).toFloat() + 0.12f * sin(gustPhase2 + 2.1).toFloat()

        val windCenter = 350f + 1500f * s
        windL.set(windCenter * gustL, 0.7f, sampleRate)
        windR.set(windCenter * gustR, 0.7f, sampleRate)
        val windGainL = s.pow(1.8f) * 0.9f * (0.8f + 0.2f * gustL)
        val windGainR = s.pow(1.8f) * 0.9f * (0.8f + 0.2f * gustR)

        val roadCutoff = 60f + 160f * s
        road1.setCutoff(roadCutoff, sampleRate)
        road2.setCutoff(roadCutoff, sampleRate)
        val roadGain = s.pow(1.1f) * (1f + 0.3f * tunnel) * 3f

        hissL.set(3000f, 0.7f, sampleRate)
        hissR.set(3200f, 0.7f, sampleRate)
        val hissGain = 0.10f * rain + 0.12f * s * s
        dropL.set(2400f + 1200f * noiseL.next01(), 6f, sampleRate)
        dropR.set(2400f + 1200f * noiseR.next01(), 6f, sampleRate)
        val dropChance = rain * rain * (30f + 90f * s) / sampleRate
        val dropGain = 0.6f * rain.pow(0.5f)

        val wet = tunnel * 0.6f
        var master = masterStart
        var i = offset
        repeat(n) {
            val nl = noiseL.next()
            val nr = noiseR.next()

            windL.process(nl)
            windR.process(nr)
            val road = road2.process(road1.process(noiseRoad.next())) * roadGain

            hissL.process(nl)
            hissR.process(nr)
            dropL.process(if (noiseL.next01() < dropChance) 0.3f + 0.7f * noiseL.next01() else 0f)
            dropR.process(if (noiseR.next01() < dropChance) 0.3f + 0.7f * noiseR.next01() else 0f)

            var l = windL.bp * windGainL + road + hissL.hp * hissGain + dropL.bp * dropGain
            var r = windR.bp * windGainR + road + hissR.hp * hissGain + dropR.bp * dropGain

            // Tunnel reverb: always fed (scaled) so the tail decays naturally on exit.
            var wetL = 0f
            var wetR = 0f
            val inL = l * tunnel
            val inR = r * tunnel
            for (c in combsL) wetL += c.process(inL)
            for (c in combsR) wetR += c.process(inR)
            l += wetL * (wet / 3f)
            r += wetR * (wet / 3f)

            master += masterStep
            out[i] = tanh(l * master) * OUTPUT_CAP
            out[i + 1] = tanh(r * master) * OUTPUT_CAP
            i += 2
        }
    }

    private fun combBank(delaysMs: FloatArray): Array<Comb> =
        Array(delaysMs.size) { Comb((delaysMs[it] * sampleRate / 1000f).toInt(), 0.80f, 0.35f) }

    companion object {
        /** Speed at which the wind and road layers reach full level. */
        const val MAX_SPEED_KPH = 140f

        /** Hard ceiling of the output amplitude (safety cap on loudness). */
        const val OUTPUT_CAP = 0.85f

        private const val WINDOW_DUCK = 0.85f
        private const val SILENCE = 1e-5f
        private const val BLOCK = 256
    }
}
