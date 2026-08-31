package com.hilight.studio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates and plays subtle, dedicated procedural audio cues for each [Pattern].
 *
 * Waveforms are synthesized into small in-memory 16-bit PCM buffers at 44.1 kHz, avoiding
 * external audio assets and providing zero-latency playback.
 */
object PatternAudioPlayer {

    private const val SAMPLE_RATE = 44100
    private const val MASTER_VOLUME = 0.22f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pcmCache = mutableMapOf<Pattern, ByteArray>()

    init {
        for (pattern in Pattern.entries) {
            pcmCache[pattern] = synthesize(pattern)
        }
    }

    /**
     * Retrieves the pre-synthesized PCM buffer for [pattern].
     * Exposed for testing and playback.
     */
    fun getPcmBuffer(pattern: Pattern): ByteArray =
        pcmCache[pattern] ?: synthesize(pattern)

    /**
     * Plays the dedicated sound effect for [pattern] asynchronously.
     */
    fun play(pattern: Pattern) {
        val bytes = getPcmBuffer(pattern)
        if (bytes.isEmpty()) return

        scope.launch {
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bytes.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(bytes, 0, bytes.size)
                track.play()

                // Calculate duration in ms and release track when finished
                val durationMs = (bytes.size / 2 * 1000L) / SAMPLE_RATE
                kotlinx.coroutines.delay(durationMs + 50)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Ignore audio track initialization/playback errors on unsupported devices
            }
        }
    }

    /**
     * Synthesizes 16-bit mono PCM samples for a given [Pattern].
     */
    fun synthesize(pattern: Pattern): ByteArray {
        val durationMs: Int
        val sampleGenerator: (Double, Double) -> Double // (timeSec, totalSec) -> amplitude in [-1.0, 1.0]

        when (pattern) {
            Pattern.OFF -> {
                durationMs = 35
                sampleGenerator = { t, _ ->
                    val env = exp(-t / 0.008)
                    sin(2 * PI * 160.0 * t) * env
                }
            }
            Pattern.SOLID -> {
                durationMs = 25
                sampleGenerator = { t, _ ->
                    val env = if (t < 0.001) t / 0.001 else exp(-(t - 0.001) / 0.007)
                    sin(2 * PI * 1200.0 * t) * env
                }
            }
            Pattern.GRADIENT -> {
                durationMs = 45
                sampleGenerator = { t, _ ->
                    val env = exp(-t / 0.014)
                    (0.6 * sin(2 * PI * 800.0 * t) + 0.4 * sin(2 * PI * 1300.0 * t)) * env
                }
            }
            Pattern.BREATHE -> {
                durationMs = 110
                sampleGenerator = { t, total ->
                    val env = sin(PI * (t / total))
                    sin(2 * PI * 340.0 * t) * env
                }
            }
            Pattern.BLINK -> {
                durationMs = 70
                sampleGenerator = { t, _ ->
                    val click1 = if (t < 0.025) exp(-t / 0.005) * sin(2 * PI * 2200.0 * t) else 0.0
                    val t2 = t - 0.035
                    val click2 = if (t2 >= 0 && t2 < 0.025) exp(-t2 / 0.005) * sin(2 * PI * 2200.0 * t2) else 0.0
                    click1 + click2
                }
            }
            Pattern.PULSE -> {
                durationMs = 60
                sampleGenerator = { t, total ->
                    val freq = 950.0 - (950.0 - 380.0) * (t / total)
                    val env = exp(-t / 0.016)
                    sin(2 * PI * freq * t) * env
                }
            }
            Pattern.CHASE -> {
                durationMs = 60
                sampleGenerator = { t, _ ->
                    var sample = 0.0
                    val freqs = doubleArrayOf(850.0, 1250.0, 1650.0)
                    for (i in freqs.indices) {
                        val ti = t - i * 0.018
                        if (ti >= 0 && ti < 0.015) {
                            sample += exp(-ti / 0.004) * sin(2 * PI * freqs[i] * ti)
                        }
                    }
                    sample
                }
            }
            Pattern.COMET -> {
                durationMs = 80
                sampleGenerator = { t, total ->
                    val freq = 1800.0 * exp(-3.0 * (t / total)) + 400.0
                    val env = sin(PI * (t / total))
                    sin(2 * PI * freq * t) * env
                }
            }
            Pattern.WAVE -> {
                durationMs = 90
                sampleGenerator = { t, total ->
                    val phase = t / total
                    val freq = 600.0 + 350.0 * sin(PI * phase)
                    val env = sin(PI * phase)
                    sin(2 * PI * freq * t) * env
                }
            }
            Pattern.RAINBOW -> {
                durationMs = 120
                sampleGenerator = { t, _ ->
                    var sample = 0.0
                    val notes = doubleArrayOf(1046.5, 1318.5, 1567.98, 1975.53) // C6, E6, G6, B6
                    for (i in notes.indices) {
                        val ti = t - i * 0.024
                        if (ti >= 0 && ti < 0.04) {
                            val env = exp(-ti / 0.012)
                            sample += 0.35 * sin(2 * PI * notes[i] * ti) * env
                        }
                    }
                    sample
                }
            }
            Pattern.RANDOM -> {
                durationMs = 60
                sampleGenerator = { t, _ ->
                    var sample = 0.0
                    val freqs = doubleArrayOf(1400.0, 920.0, 1750.0)
                    val times = doubleArrayOf(0.0, 0.016, 0.034)
                    for (i in freqs.indices) {
                        val ti = t - times[i]
                        if (ti >= 0 && ti < 0.018) {
                            sample += 0.4 * exp(-ti / 0.004) * sin(2 * PI * freqs[i] * ti)
                        }
                    }
                    sample
                }
            }
            Pattern.CUSTOM -> {
                durationMs = 30
                sampleGenerator = { t, _ ->
                    val snap1 = exp(-t / 0.004) * sin(2 * PI * 1500.0 * t)
                    val t2 = t - 0.010
                    val snap2 = if (t2 >= 0) 0.5 * exp(-t2 / 0.005) * sin(2 * PI * 850.0 * t2) else 0.0
                    snap1 + snap2
                }
            }
        }

        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val totalSec = durationMs / 1000.0
        val pcm = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val raw = sampleGenerator(t, totalSec) * MASTER_VOLUME
            val clamped = raw.coerceIn(-1.0, 1.0)
            val sample = (clamped * 32767.0).toInt().toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return pcm
    }
}

