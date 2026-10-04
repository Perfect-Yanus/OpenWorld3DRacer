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
    private var sfxAudioTrack: AudioTrack? = null
    private var bgmAudioTrack: AudioTrack? = null

    @Volatile
    private var isPlaying = false
    var isBgmEnabled = true

    // Sound state params
    @Volatile var currentSpeedKmh = 0f
    @Volatile var currentPowerKw = 0f
    @Volatile var isDrifting = false
    @Volatile var isInWater = false
    @Volatile var isCollided = false
    @Volatile var isNitroActive = false

    // Stunt / Item SFX Triggers
    @Volatile private var sfxTrigger = 0 // 1: item chime, 2: jump launch, 3: landing thud

    private val sampleRate = 44100 // Standard 44.1kHz sample rate

    fun startAudio() {
        if (isPlaying) return
        isPlaying = true

        try {
            initMotorSynth()
            initDriftSynth()
            initSfxSynth()
            initBgmSynth()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun stopAudio() {
        isPlaying = false
        try {
            motorAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
            driftAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
            sfxAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
            bgmAudioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        motorAudioTrack = null
        driftAudioTrack = null
        sfxAudioTrack = null
        bgmAudioTrack = null
    }

    private fun createAudioTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null

        val bufferSize = minBuffer * 2

        return try {
            AudioTrack.Builder()
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
                .build().also {
                    if (it.state != AudioTrack.STATE_INITIALIZED) {
                        it.release()
                        return null
                    }
                }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun initMotorSynth() {
        motorAudioTrack = createAudioTrack() ?: return
        try {
            motorAudioTrack?.play()
        } catch (e: Throwable) {
            return
        }

        thread(name = "MotorSoundSynthThread") {
            val buffer = ShortArray(1024)
            var phase = 0.0

            while (isPlaying) {
                try {
                    val nitroMultiplier = if (isNitroActive) 1.5 else 1.0
                    val targetFreq = (110.0 + (currentSpeedKmh * 8.5) + (currentPowerKw * 0.45)) * nitroMultiplier
                    val phaseIncrement = (2.0 * Math.PI * targetFreq) / sampleRate
                    val volumeScale = if (currentSpeedKmh > 1f || currentPowerKw > 1f || isNitroActive) 0.55f else 0.12f

                    for (i in buffer.indices) {
                        phase += phaseIncrement
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                        val harmonic = if (isNitroActive) sin(phase * 3.0) * 0.4 else sin(phase * 2.0) * 0.3
                        val sample = (sin(phase) * 0.7 + harmonic) * 15000.0 * volumeScale
                        buffer[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
                    }

                    motorAudioTrack?.write(buffer, 0, buffer.size)
                    Thread.sleep(15)
                } catch (e: Throwable) {
                    break
                }
            }
        }
    }

    private fun initDriftSynth() {
        driftAudioTrack = createAudioTrack() ?: return
        try {
            driftAudioTrack?.play()
        } catch (e: Throwable) {
            return
        }

        thread(name = "DriftSoundSynthThread") {
            val buffer = ShortArray(1024)
            var lastSample = 0f

            while (isPlaying) {
                try {
                    val active = isDrifting || isInWater || isCollided

                    for (i in buffer.indices) {
                        if (active) {
                            val noise = (Random.nextFloat() * 2f - 1f)
                            val filtered = lastSample * 0.45f + noise * 0.55f
                            lastSample = filtered
                            val vol = if (isCollided) 24000f else (if (isDrifting) 16000f else 10000f)
                            buffer[i] = (filtered * vol).toInt().coerceIn(-32767, 32767).toShort()
                        } else {
                            buffer[i] = 0
                        }
                    }

                    if (isCollided) isCollided = false

                    driftAudioTrack?.write(buffer, 0, buffer.size)
                    Thread.sleep(15)
                } catch (e: Throwable) {
                    break
                }
            }
        }
    }

    private fun initSfxSynth() {
        sfxAudioTrack = createAudioTrack() ?: return
        try {
            sfxAudioTrack?.play()
        } catch (e: Throwable) {
            return
        }

        thread(name = "SfxSoundSynthThread") {
            val buffer = ShortArray(1024)

            while (isPlaying) {
                try {
                    val trigger = sfxTrigger
                    if (trigger != 0) {
                        sfxTrigger = 0
                        when (trigger) {
                            1 -> playMelodicChime() // Item Pickup Chime
                            2 -> playLaunchWhoosh() // Jump Ramp Launch
                            3 -> playImpactThud()   // Landing Thud
                        }
                    } else {
                        buffer.fill(0)
                        sfxAudioTrack?.write(buffer, 0, buffer.size)
                        Thread.sleep(20)
                    }
                } catch (e: Throwable) {
                    break
                }
            }
        }
    }

    private fun playMelodicChime() {
        val toneLength = 2200 // ~50ms per tone
        val buffer = ShortArray(toneLength * 2)
        val f1 = 880.0  // A5
        val f2 = 1760.0 // A6

        for (i in 0 until toneLength) {
            val phase = (2.0 * Math.PI * f1 * i) / sampleRate
            val env = 1.0 - (i.toDouble() / toneLength)
            buffer[i] = (sin(phase) * env * 22000.0).toInt().toShort()
        }
        for (i in 0 until toneLength) {
            val phase = (2.0 * Math.PI * f2 * i) / sampleRate
            val env = 1.0 - (i.toDouble() / toneLength)
            buffer[toneLength + i] = (sin(phase) * env * 26000.0).toInt().toShort()
        }
        sfxAudioTrack?.write(buffer, 0, buffer.size)
    }

    private fun playLaunchWhoosh() {
        val length = 4400 // ~100ms
        val buffer = ShortArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / length
            val freq = 200.0 + 900.0 * t
            val phase = (2.0 * Math.PI * freq * i) / sampleRate
            val noise = (Random.nextFloat() * 2f - 1f) * 0.4f
            val env = sin(t * Math.PI)
            val sample = (sin(phase) * 0.6 + noise) * env * 24000.0
            buffer[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
        }
        sfxAudioTrack?.write(buffer, 0, buffer.size)
    }

    private fun playImpactThud() {
        val length = 3200 // ~70ms
        val buffer = ShortArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / length
            val freq = 120.0 * (1.0 - t * 0.6)
            val phase = (2.0 * Math.PI * freq * i) / sampleRate
            val env = (1.0 - t) * (1.0 - t)
            val sample = sin(phase) * env * 28000.0
            buffer[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
        }
        sfxAudioTrack?.write(buffer, 0, buffer.size)
    }

    private fun initBgmSynth() {
        bgmAudioTrack = createAudioTrack() ?: return
        try {
            bgmAudioTrack?.play()
        } catch (e: Throwable) {
            return
        }

        thread(name = "BgmMusicThread") {
            val notes = doubleArrayOf(220.0, 261.63, 293.66, 329.63, 392.00, 440.0, 523.25)
            var noteIndex = 0
            val buffer = ShortArray(2048)

            while (isPlaying) {
                try {
                    if (!isBgmEnabled) {
                        Thread.sleep(200)
                        continue
                    }

                    val freq = notes[noteIndex % notes.size]
                    val phaseInc = (2.0 * Math.PI * freq) / sampleRate
                    var phase = 0.0

                    for (i in buffer.indices) {
                        phase += phaseInc
                        val pulse = if (sin(phase) > 0) 0.5 else -0.5
                        val env = (1.0 - i.toDouble() / buffer.size)
                        buffer[i] = (pulse * env * 3500.0).toInt().toShort()
                    }

                    bgmAudioTrack?.write(buffer, 0, buffer.size)
                    noteIndex++
                    Thread.sleep(140)
                } catch (e: Throwable) {
                    break
                }
            }
        }
    }

    fun playItemChime() {
        sfxTrigger = 1
    }

    fun playJumpLaunch() {
        sfxTrigger = 2
    }

    fun playLandingSound() {
        sfxTrigger = 3
    }

    fun triggerCollisionImpact() {
        isCollided = true
    }
}
