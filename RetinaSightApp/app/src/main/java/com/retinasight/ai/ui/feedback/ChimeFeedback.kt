package com.retinasight.ai.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Confirmation feedback: a short chime and a haptic tick.
 *
 * This is UI feedback only. It never speaks and never carries a result - the
 * spoken summary belongs to SpeechManager, which owns the app's only
 * TextToSpeech instance. A second engine here would race it for the audio
 * focus and for the voice, so the design's TTS half is deliberately not ported.
 *
 * The tone is synthesised rather than shipped as an asset: it costs no APK
 * space, and a caught-consent confirmation must work on a phone with no media
 * files and the ringer muted, where the haptic still lands.
 */
class ChimeFeedback(private val context: Context) {

    /**
     * A short bell at ~2.6 kHz over a fast exponential decay.
     *
     * Deliberately high and brief: a low beep reads as an error, which is the
     * opposite of what consent-given should feel like. Synthesis and playback
     * run off the main thread so a tap is never delayed by audio setup.
     */
    fun playConfirmChime() {
        hapticTick()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 44100
                val durationMs = 320
                val samples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(samples)

                val fundamental = 2640.0
                val harmonic = 3960.0

                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-14.0 * t)
                    val mixed = (
                        sin(2.0 * PI * fundamental * t) +
                            0.35 * sin(2.0 * PI * harmonic * t)
                        ) * envelope * 0.7
                    buffer[i] = (mixed * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }

                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            // SONIFICATION, not MEDIA: this must not duck or
                            // interrupt the spoken summary.
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
                    .setBufferSizeInBytes(minBuffer.coerceAtLeast(buffer.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                delay(durationMs.toLong() + 50)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Audio is a nicety. A device that refuses to play it must still
                // complete the screening, so this is logged and swallowed.
                Log.e(TAG, "chime failed: ${e.message}")
            }
        }
    }

    /**
     * A double pulse, shaped like a heartbeat, for a referral the worker must
     * not miss.
     *
     * Fired on arrival at a result whose urgency is URGENT or IMMEDIATE. It
     * reaches the hand rather than the eye, which matters when the phone is
     * being passed across a table and nobody is looking at the screen yet.
     * It carries no information the screen does not also state.
     */
    fun urgentHeartbeat() {
        try {
            val vibrator = vibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 120, 150)
                val amplitudes = intArrayOf(0, 220, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 120, 150), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "heartbeat failed: ${e.message}")
        }
    }

    /** A single confirmation tick. */
    fun hapticTick() {
        try {
            val vibrator = vibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (e: Exception) {
            Log.e(TAG, "haptic failed: ${e.message}")
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object {
        const val TAG = "ChimeFeedback"
    }
}
