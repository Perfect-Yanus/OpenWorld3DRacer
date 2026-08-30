package com.openworld.racer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.sin
import kotlin.random.Random

class SoundManager {

    private var motorAudioTrack: AudioTrack? = null
    private var driftAudioTrack: AudioTrack? = null
    private var bgmAudioTrack: AudioTrack? = null

    private var isPlaying = false
    private var isBgmEnabled = true

    // Sound state params
    var currentSpeedKmh = 0f
    var currentPowerKw = 0f
    var isDrifting = false
    var isInWater = false
    var isCollided = false

    private val sampleRate = 22050

    fun startAudio() {
        if (isPlaying) return
        isPlaying = true

        initMotorSynth()
        initDriftSynth()
        initBgmSynth()
    }

    fun stopAudio() {
        isPlaying = false
        try {
            motorAudioTrack?.stop()
            motorAudioTrack?.release()
            driftAudioTrack?.stop()
            driftAudioTrack?.release()
            bgmAudioTrack?.stop()
            bgmAudioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        motorAudioTrack = null
        driftAudioTrack = null
        bgmAudioTrack = null
    }

    private fun initMotorSynth() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        motorAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        motorAudioTrack?.play()

        thread(name = "MotorSoundSynthThread") {
            val buffer = ShortArray(512)
            var phase = 0.0

            while (isPlaying) {
                // Calculate Motor Frequency (80Hz to 1400Hz)
                val targetFreq = 90.0 + (currentSpeedKmh * 8.5) + (currentPowerKw * 0.4)
                val phaseIncrement = (2.0 * Math.PI * targetFreq) / sampleRate

                val volumeScale = if (currentSpeedKmh > 1f || currentPowerKw > 1f) 0.65f else 0.15f

                for (i in buffer.indices) {
                    phase += phaseIncrement
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI

                    // Electric EV Motor Whine (Sine + Harmonic)
                    val sample = (sin(phase) * 0.7 + sin(phase * 2.0) * 0.3) * 16000.0 * volumeScale
                    buffer[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
                }

                motorAudioTrack?.write(buffer, 0, buffer.size)
                try { Thread.sleep(10) } catch (_: Exception) {}
            }
        }
    }

    private fun initDriftSynth() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        driftAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        driftAudioTrack?.play()

        thread(name = "DriftSoundSynthThread") {
            val buffer = ShortArray(512)
            var lastSample = 0f

            while (isPlaying) {
                val active = isDrifting || isInWater || isCollided

                for (i in buffer.indices) {
                    if (active) {
                        val noise = (Random.nextFloat() * 2f - 1f)
                        // Filtered high pitched noise for tire screech / water splash
                        val filtered = lastSample * 0.45f + noise * 0.55f
                        lastSample = filtered
                        val vol = if (isCollided) 28000f else (if (isDrifting) 18000f else 12000f)
                        buffer[i] = (filtered * vol).toInt().coerceIn(-32767, 32767).toShort()
                    } else {
                        buffer[i] = 0
                    }
                }

                if (isCollided) isCollided = false

                driftAudioTrack?.write(buffer, 0, buffer.size)
                try { Thread.sleep(12) } catch (_: Exception) {}
            }
        }
    }

    private fun initBgmSynth() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        bgmAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        bgmAudioTrack?.play()

        thread(name = "BgmMusicThread") {
            val notes = doubleArrayOf(220.0, 261.63, 293.66, 329.63, 392.00, 440.0, 523.25) // A minor synthwave
            var noteIndex = 0
            val buffer = ShortArray(1024)

            while (isPlaying) {
                if (!isBgmEnabled) {
                    try { Thread.sleep(200) } catch (_: Exception) {}
                    continue
                }

                val freq = notes[noteIndex % notes.size]
                val phaseInc = (2.0 * Math.PI * freq) / sampleRate
                var phase = 0.0

                for (i in buffer.indices) {
                    phase += phaseInc
                    // Synth pulse wave melody
                    val pulse = if (sin(phase) > 0) 0.5 else -0.5
                    val env = (1.0 - i.toDouble() / buffer.size) // Note decay
                    buffer[i] = (pulse * env * 4500.0).toInt().toShort()
                }

                bgmAudioTrack?.write(buffer, 0, buffer.size)
                noteIndex++
                try { Thread.sleep(120) } catch (_: Exception) {}
            }
        }
    }

    fun triggerCollisionImpact() {
        isCollided = true
    }
}
