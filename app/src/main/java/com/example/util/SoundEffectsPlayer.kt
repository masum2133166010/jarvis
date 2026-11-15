package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class HudSoundType {
    TAP,
    MIC_START,
    RESPONSE_READY,
    ALERT
}

/**
 * Synthesizes subtle, crisp futuristic HUD tones using PCM AudioTrack
 * so no external audio files are required.
 */
class SoundEffectsPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    var enabled: Boolean = true

    fun play(type: HudSoundType) {
        if (!enabled) return
        scope.launch {
            runCatching {
                when (type) {
                    HudSoundType.TAP -> playDualTone(880.0, 1180.0, 45, 0.14)
                    HudSoundType.MIC_START -> playSweep(520.0, 1040.0, 110, 0.18)
                    HudSoundType.RESPONSE_READY -> playDualTone(660.0, 990.0, 95, 0.16)
                    HudSoundType.ALERT -> playDualTone(340.0, 260.0, 140, 0.2)
                }
            }
        }
    }

    private fun playDualTone(freqA: Double, freqB: Double, durationMs: Int, gain: Double) {
        val sampleRate = 22050
        val count = (sampleRate * durationMs) / 1000
        val samples = ShortArray(count)
        val half = count / 2
        for (i in 0 until count) {
            val freq = if (i < half) freqA else freqB
            val env = fadeEnvelope(i, count)
            val value = sin(2.0 * PI * freq * i / sampleRate) * env * gain
            samples[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        writePcm(samples, sampleRate)
    }

    private fun playSweep(startFreq: Double, endFreq: Double, durationMs: Int, gain: Double) {
        val sampleRate = 22050
        val count = (sampleRate * durationMs) / 1000
        val samples = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * t
            phase += 2.0 * PI * freq / sampleRate
            val env = fadeEnvelope(i, count)
            samples[i] = (sin(phase) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        writePcm(samples, sampleRate)
    }

    private fun fadeEnvelope(index: Int, total: Int): Double {
        val attack = (total * 0.15).coerceAtLeast(1.0)
        val release = (total * 0.3).coerceAtLeast(1.0)
        return when {
            index < attack -> index / attack
            index > total - release -> (total - index) / release
            else -> 1.0
        }.coerceIn(0.0, 1.0)
    }

    private fun writePcm(samples: ShortArray, sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(samples.size * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(((samples.size * 1000L) / sampleRate) + 30L)
        track.stop()
        track.release()
    }
}
