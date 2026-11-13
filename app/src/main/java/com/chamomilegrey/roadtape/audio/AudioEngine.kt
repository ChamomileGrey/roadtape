/*
 * roadtape v0.1.0 — streams the synthesizer to an AudioTrack and handles audio focus
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/main/java/com/chamomilegrey/roadtape/audio/AudioEngine.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 initial streaming engine
 */
package com.chamomilegrey.roadtape.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.chamomilegrey.roadtape.dsp.SoundscapeSynth
import kotlin.math.max

/**
 * Owns the audio thread. Navigation prompts, calls and warnings take priority:
 * the engine ducks or goes silent on focus loss and switches off on permanent loss.
 */
class AudioEngine(context: Context) {

    val synth = SoundscapeSynth(SAMPLE_RATE)

    /** Called on the main thread when another app takes audio focus for good. */
    var onForcedOff: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> synth.focusGain = 1f
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> synth.focusGain = DUCK_GAIN
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> synth.focusGain = 0f
            AudioManager.AUDIOFOCUS_LOSS -> {
                setEnabled(false)
                mainHandler.post { onForcedOff?.invoke() }
            }
        }
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(focusListener, mainHandler)
        .build()

    @Volatile private var running = false
    private var thread: Thread? = null
    private val stopLater = Runnable { if (!synth.enabled) stopThread() }

    /** Turns the soundscape on or off. Returns false if audio focus was denied. */
    fun setEnabled(on: Boolean): Boolean {
        mainHandler.removeCallbacks(stopLater)
        if (on) {
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            synth.focusGain = 1f
            synth.enabled = true
            startThread()
        } else {
            synth.enabled = false
            audioManager.abandonAudioFocusRequest(focusRequest)
            // Let the fade-out finish before the track stops.
            mainHandler.postDelayed(stopLater, FADE_OUT_MS)
        }
        return true
    }

    /** Stops playback immediately and frees the audio thread. */
    fun release() {
        mainHandler.removeCallbacks(stopLater)
        synth.enabled = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        stopThread()
    }

    private fun startThread() {
        if (running) return
        running = true
        thread = Thread({ renderLoop() }, "roadtape-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopThread() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
    }

    private fun renderLoop() {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val buffer = FloatArray(BLOCK_FRAMES * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBytes, buffer.size * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            while (running) {
                synth.render(buffer, BLOCK_FRAMES)
                if (track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val BLOCK_FRAMES = 512
        private const val DUCK_GAIN = 0.25f
        private const val FADE_OUT_MS = 700L
        private const val JOIN_TIMEOUT_MS = 500L
    }
}
