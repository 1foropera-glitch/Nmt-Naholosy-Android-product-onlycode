package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sound feedback manager for correct/wrong answers.
 *
 * Correct-answer audio is generated entirely in code with a data-driven
 * additive I/Q synthesizer reconstructed from the reference WAV. It uses
 * only measured tonal components — no noise layer, sparkle noise, exciter,
 * or reverb — and loads its measured envelopes from the bundled asset.
 *
 * Wrong-answer synthesis is intentionally left unchanged.
 */
class SoundFeedbackManager(context: android.content.Context) {
    private val assets = context.applicationContext.assets
    private fun loadBands(): Array<IqBand> = synchronized(SoundFeedbackManager::class.java) {
        cachedBands ?: run {
            val data = org.json.JSONArray(assets.open("feedback-bands.json").bufferedReader().use { it.readText() })
            Array(data.length()) { index ->
                val band = data.getJSONObject(index)
                IqBand(band.getDouble("carrierHz"), band.getInt("hopSamples"), band.getDouble("scale").toFloat(), decodeIq(band.getString("iq")))
            }.also { cachedBands = it }
        }
    }


    private data class IqBand(
        val carrierHz: Double,
        val hopSamples: Int,
        val scale: Float,
        val iq: ShortArray
    )

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioLock = Any()
    private val activeTracks = mutableSetOf<AudioTrack>()
    @Volatile private var closed = false
    private val correctPcm: ShortArray by lazy {
        val pcm = FloatArray(CORRECT_SOURCE_END_SAMPLE_EXCLUSIVE - CORRECT_SOURCE_START_SAMPLE)
        for (band in loadBands()) addIqBand(pcm, band)
        floatToInt16Linear(pcm)
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TWO_PI = 2.0 * PI

        // The reference WAV contains leading/trailing capture silence.
        // Only the actual tonal event is rendered so UI feedback starts immediately.
        private const val CORRECT_SOURCE_START_SAMPLE = 10621
        private const val CORRECT_SOURCE_END_SAMPLE_EXCLUSIVE = 56164

        /**
         * V5 clean-bright model. The Base64 payloads are quantized I/Q control
         * envelopes for 15 measured tonal carrier bands. They decode once on
         * first use (~75 KB of ShortArray data) and are then reused.
         */
        private var cachedBands: Array<IqBand>? = null

        private fun decodeIq(encoded: String): ShortArray {
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val output = ShortArray(bytes.size / 2)
            var byteIndex = 0
            var shortIndex = 0
            while (byteIndex + 1 < bytes.size) {
                val low = bytes[byteIndex].toInt() and 0xFF
                val high = bytes[byteIndex + 1].toInt()
                output[shortIndex] = ((high shl 8) or low).toShort()
                byteIndex += 2
                shortIndex += 1
            }
            return output
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ────────────────────────────────────────────────────────────────

    /**
     * Clean, bright tonal correct-answer chime reconstructed from the
     * analyzed reference WAV. No external audio asset is required.
     */
    fun playCorrect() {
        scope.launch {
            try {
                synthesizeCorrect()
            } catch (_: Exception) {
                // Silently fail if audio device is busy or unavailable
            }
        }
    }

    /**
     * Soft, warm descending minor interval — informative but gentle.
     * Muted harmonics keep it non-stressful.
     */
    fun playWrong() {
        scope.launch {
            try {
                synthesizeWrong()
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }

    /**
     * Release system resources if needed.
     */
    fun release() {
        closed = true
        scope.cancel()
        synchronized(audioLock) {
            activeTracks.forEach { track -> runCatching { track.stop(); track.release() } }
            activeTracks.clear()
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  CORRECT ANSWER — reconstructed clean tonal V5
    // ────────────────────────────────────────────────────────────────

    /**
     * Reconstructs the final V5 correct-answer sound from measured tonal bands.
     *
     * Each band is a clean carrier oscillator whose complex I/Q envelope is
     * linearly interpolated from analysis control points. This preserves the
     * tiny amplitude/phase changes that make the reference sound crisp and
     * bell-like without adding synthetic noise, chorus, or reverb.
     */
    private fun synthesizeCorrect() { playPcm(correctPcm) }

    private fun addIqBand(buffer: FloatArray, band: IqBand) {
        val controlCount = band.iq.size / 2
        if (controlCount < 2) return

        val phaseStep = TWO_PI * band.carrierHz / SAMPLE_RATE
        var phase = (
            TWO_PI * band.carrierHz * CORRECT_SOURCE_START_SAMPLE.toDouble() / SAMPLE_RATE
        ) % TWO_PI
        var carrierCos = kotlin.math.cos(phase)
        var carrierSin = sin(phase)
        val stepCos = kotlin.math.cos(phaseStep)
        val stepSin = sin(phaseStep)

        for (i in buffer.indices) {
            val sourceSample = CORRECT_SOURCE_START_SAMPLE + i
            val position = sourceSample.toDouble() / band.hopSamples
            val controlIndex = position.toInt().coerceIn(0, controlCount - 1)
            val nextIndex = (controlIndex + 1).coerceAtMost(controlCount - 1)
            val fraction = position - controlIndex

            val base = controlIndex * 2
            val next = nextIndex * 2

            val i0 = band.iq[base].toDouble() * band.scale
            val q0 = band.iq[base + 1].toDouble() * band.scale
            val i1 = band.iq[next].toDouble() * band.scale
            val q1 = band.iq[next + 1].toDouble() * band.scale

            val iValue = i0 + (i1 - i0) * fraction
            val qValue = q0 + (q1 - q0) * fraction

            // real((I + jQ) * exp(j*phase))
            buffer[i] += (iValue * carrierCos - qValue * carrierSin).toFloat()

            val nextCos = carrierCos * stepCos - carrierSin * stepSin
            val nextSin = carrierSin * stepCos + carrierCos * stepSin
            carrierCos = nextCos
            carrierSin = nextSin

            // Keep recursive oscillator magnitude numerically stable.
            if ((i and 2047) == 2047) {
                val norm = kotlin.math.sqrt(carrierCos * carrierCos + carrierSin * carrierSin)
                if (norm > 0.0) {
                    carrierCos /= norm
                    carrierSin /= norm
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  WRONG ANSWER — soft muted thud
    // ────────────────────────────────────────────────────────────────

    private fun synthesizeWrong() {
        val totalDurationSec = 0.45f
        val totalSamples = (totalDurationSec * SAMPLE_RATE).toInt()
        val pcm = FloatArray(totalSamples)

        // Layer 1: Warm fundamental — Eb4 (311.13 Hz) with filtered harmonics
        addMutedTone(
            buffer = pcm,
            fundamentalHz = 311.13,
            startSec = 0.0f,
            durationSec = 0.35f,
            volume = 0.35f,
            attackSec = 0.012f,
            decayRate = 4.5,
            harmonics = floatArrayOf(1.0f, 0.30f, 0.08f), // fewer highs = warmer
            rolloffPerHarmonic = 0.55f
        )

        // Layer 2: Descending to Bb3 (233.08 Hz) — minor third below
        addMutedTone(
            buffer = pcm,
            fundamentalHz = 233.08,
            startSec = 0.07f,
            durationSec = 0.35f,
            volume = 0.28f,
            attackSec = 0.015f,
            decayRate = 4.0,
            harmonics = floatArrayOf(1.0f, 0.25f, 0.05f),
            rolloffPerHarmonic = 0.50f
        )

        // Layer 3: Very subtle sub-bass warmth — Eb3 (155.56 Hz)
        addMutedTone(
            buffer = pcm,
            fundamentalHz = 155.56,
            startSec = 0.02f,
            durationSec = 0.25f,
            volume = 0.12f,
            attackSec = 0.020f,
            decayRate = 5.0,
            harmonics = floatArrayOf(1.0f, 0.15f),
            rolloffPerHarmonic = 0.40f
        )

        // Gentle reverb — less than correct to keep it muted
        applySimpleReverb(pcm, mix = 0.10f)

        val output = floatToInt16(pcm, masterGain = 0.80f)
        playPcm(output)
    }

    // ────────────────────────────────────────────────────────────────
    //  SYNTHESIS HELPERS
    // ────────────────────────────────────────────────────────────────

    /**
     * Muted warm tone — fewer harmonics, extra per-harmonic rolloff,
     * no chorus detuning. Feels soft and non-aggressive.
     */
    private fun addMutedTone(
        buffer: FloatArray,
        fundamentalHz: Double,
        startSec: Float,
        durationSec: Float,
        volume: Float,
        attackSec: Float,
        decayRate: Double,
        harmonics: FloatArray,
        rolloffPerHarmonic: Float
    ) {
        val startSample = (startSec * SAMPLE_RATE).toInt()
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val attackSamples = (attackSec * SAMPLE_RATE).toInt()

        for (i in 0 until numSamples) {
            val idx = startSample + i
            if (idx >= buffer.size) break

            val t = i.toDouble() / SAMPLE_RATE

            // Envelope: smooth attack + gentle exponential decay
            val envelope = if (i < attackSamples) {
                (1.0 - kotlin.math.cos(PI * i / attackSamples)) * 0.5
            } else {
                exp(-decayRate * (i - attackSamples).toDouble() / SAMPLE_RATE)
            }

            // Additive synthesis with progressive rolloff
            var sample = 0.0
            for ((h, gain) in harmonics.withIndex()) {
                val harmNum = h + 1
                val freq = fundamentalHz * harmNum
                val rolloff = rolloffPerHarmonic.toDouble().pow(h.toDouble())
                sample += gain * rolloff * sin(TWO_PI * freq * t)
            }

            buffer[idx] += (sample * volume * envelope).toFloat()
        }
    }

    /**
     * Simple Schroeder-style comb filter reverb.
     * Adds spatial depth without external libraries.
     */
    private fun applySimpleReverb(buffer: FloatArray, mix: Float) {
        // Four comb filters with prime-ish delay lengths (in samples)
        val delays = intArrayOf(1117, 1487, 1777, 2053)
        val feedback = 0.35f

        val temp = FloatArray(buffer.size)

        for (delaySamples in delays) {
            for (i in delaySamples until buffer.size) {
                temp[i] += buffer[i - delaySamples] + temp[i - delaySamples] * feedback
            }
        }

        // Mix wet signal into dry
        for (i in buffer.indices) {
            buffer[i] = buffer[i] * (1f - mix) + temp[i] * mix * 0.25f // /4 for 4 combs
        }
    }

    /**
     * Linear PCM conversion used by the reconstructed correct-answer sound.
     * No saturation/soft clipping is applied because that would alter its
     * measured upper harmonics and make the chime less precise.
     */
    private fun floatToInt16Linear(buffer: FloatArray): ShortArray {
        val output = ShortArray(buffer.size)
        for (i in buffer.indices) {
            val clamped = buffer[i].coerceIn(-1f, 1f)
            output[i] = kotlin.math.round(clamped * 32767f)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
        }
        return output
    }

    /**
     * Convert float buffer [-1..1+] to Int16 PCM with soft-clip limiting.
     */
    private fun floatToInt16(buffer: FloatArray, masterGain: Float): ShortArray {
        val output = ShortArray(buffer.size)
        for (i in buffer.indices) {
            // Soft-clip (tanh-style) to avoid harsh digital clipping
            val scaled = buffer[i] * masterGain
            val clipped = if (scaled > 1f) 1f
            else if (scaled < -1f) -1f
            else scaled * (1.5f - 0.5f * scaled * scaled) // gentle saturation curve
            output[i] = (clipped * 28000f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    // ────────────────────────────────────────────────────────────────
    //  PLAYBACK
    // ────────────────────────────────────────────────────────────────

    private fun playPcm(pcm: ShortArray) {
        val track = synchronized(audioLock) {
            if (closed) return
            val created = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            try {
                created.write(pcm, 0, pcm.size)
                created.play()
                activeTracks.add(created)
                created
            } catch (error: Exception) {
                created.release()
                throw error
            }
        }
        scope.launch {
            delay(pcm.size * 1000L / SAMPLE_RATE + 200)
            synchronized(audioLock) {
                if (activeTracks.remove(track)) runCatching { track.stop(); track.release() }
            }
        }
    }
}
