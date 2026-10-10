package com.openworld.racer.engine3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VisualEffects {
    private var motionBlurIntensity = 0f
    private var speedLineIntensity = 0f
    private var screenShakeX = 0f
    private var screenShakeY = 0f
    private var fovMultiplier = 1f
    private var shakeIntensity = 0f
    private var driftSmokeIntensity = 0f
    private var nitroFlameIntensity = 0f
    private var nearMissFlash = 0f

    fun update(dt: Float, speedKmh: Float, isNitroActive: Boolean, isAirborne: Boolean, isDrifting: Boolean) {
        // Motion blur: ramps up from 80 km/h, max at 180+
        motionBlurIntensity = when {
            speedKmh < 60f -> 0f
            speedKmh < 120f -> (speedKmh - 60f) / 60f * 0.3f
            speedKmh < 180f -> 0.3f + (speedKmh - 120f) / 60f * 0.4f
            else -> 0.7f + ((speedKmh - 180f) / 60f * 0.3f).coerceAtMost(0.3f)
        }

        // Speed lines: visible at 100+ km/h, strong at 150+
        speedLineIntensity = when {
            speedKmh < 80f -> 0f
            speedKmh < 120f -> (speedKmh - 80f) / 40f * 0.4f
            speedKmh < 160f -> 0.4f + (speedKmh - 120f) / 40f * 0.3f
            else -> 0.7f + ((speedKmh - 160f) / 40f * 0.3f).coerceAtMost(0.3f)
        }

        // FOV multiplier: base 1.0, +0.15 at high speed, +0.25 with nitro
        val targetFovMult = when {
            isNitroActive -> 1.35f
            speedKmh > 150f -> 1.2f
            speedKmh > 100f -> 1.1f
            else -> 1.0f
        }
        fovMultiplier += (targetFovMult - fovMultiplier) * (dt * 4f).coerceAtMost(1f)

        // Screen shake: subtle at speed, strong with nitro/airborne
        shakeIntensity = when {
            isAirborne -> 0.6f
            isNitroActive -> 0.4f
            speedKmh > 140f -> 0.2f
            else -> 0f
        }
        if (shakeIntensity > 0f) {
            screenShakeX = (Random.nextFloat() - 0.5f) * shakeIntensity * 2f
            screenShakeY = (Random.nextFloat() - 0.5f) * shakeIntensity * 2f
        } else {
            screenShakeX *= 0.9f
            screenShakeY *= 0.9f
        }

        // Drift smoke
        driftSmokeIntensity = if (isDrifting && speedKmh > 20f) {
            (driftSmokeIntensity + dt * 3f).coerceAtMost(1f)
        } else {
            (driftSmokeIntensity - dt * 2f).coerceAtLeast(0f)
        }

        // Nitro flame
        nitroFlameIntensity = if (isNitroActive) {
            (nitroFlameIntensity + dt * 5f).coerceAtMost(1f)
        } else {
            (nitroFlameIntensity - dt * 3f).coerceAtLeast(0f)
        }

        // Near miss flash decay
        if (nearMissFlash > 0f) {
            nearMissFlash = (nearMissFlash - dt * 2f).coerceAtLeast(0f)
        }
    }

    fun getMotionBlurIntensity(): Float = motionBlurIntensity
    fun getSpeedLineIntensity(): Float = speedLineIntensity
    fun getScreenShakeOffset(): Pair<Float, Float> = Pair(screenShakeX, screenShakeY)
    fun getFovMultiplier(): Float = fovMultiplier
    fun getDriftSmokeIntensity(): Float = driftSmokeIntensity
    fun getNitroFlameIntensity(): Float = nitroFlameIntensity
    fun getNearMissFlash(): Float = nearMissFlash

    fun triggerNearMiss() {
        nearMissFlash = 1f
    }

    fun triggerImpact(strength: Float = 1f) {
        shakeIntensity = strength.coerceIn(0f, 2f)
        screenShakeX = (Random.nextFloat() - 0.5f) * shakeIntensity * 4f
        screenShakeY = (Random.nextFloat() - 0.5f) * shakeIntensity * 4f
    }

    fun reset() {
        motionBlurIntensity = 0f
        speedLineIntensity = 0f
        screenShakeX = 0f
        screenShakeY = 0f
        fovMultiplier = 1f
        shakeIntensity = 0f
        driftSmokeIntensity = 0f
        nitroFlameIntensity = 0f
        nearMissFlash = 0f
    }
}

data class Particle(
    var posX: Float,
    var posY: Float,
    var posZ: Float,
    var velX: Float,
    var velY: Float,
    var velZ: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var color: Int
) {
    fun update(dt: Float): Boolean {
        posX += velX * dt
        posY += velY * dt
        posZ += velZ * dt
        velY -= 9.8f * dt // gravity
        life -= dt
        return life > 0f
    }

    fun getAlpha(): Float = (life / maxLife).coerceIn(0f, 1f)
}

object ParticleSystem {
    private val particles = mutableListOf<Particle>()
    private val maxParticles = 500

    fun spawnSparks(posX: Float, posY: Float, posZ: Float, count: Int = 10) {
        for (i in 0 until count) {
            if (particles.size >= maxParticles) break
            val angle = Random.nextFloat() * 360f
            val speed = 5f + Random.nextFloat() * 10f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            particles.add(Particle(
                posX = posX,
                posY = posY,
                posZ = posZ,
                velX = sin(rad) * speed,
                velY = 3f + Random.nextFloat() * 5f,
                velZ = cos(rad) * speed,
                life = 0.5f + Random.nextFloat() * 0.5f,
                maxLife = 1f,
                size = 0.1f + Random.nextFloat() * 0.15f,
                color = if (Random.nextBoolean()) 0xFFFFD600.toInt() else 0xFFFF6D00.toInt()
            ))
        }
    }

    fun spawnDriftSmoke(posX: Float, posY: Float, posZ: Float, headingAngle: Float) {
        if (particles.size >= maxParticles) return
        val rad = Math.toRadians(headingAngle.toDouble()).toFloat()
        val backX = -sin(rad) * 2f
        val backZ = cos(rad) * 2f
        particles.add(Particle(
            posX = posX + backX + (Random.nextFloat() - 0.5f) * 1.5f,
            posY = posY + 0.3f,
            posZ = posZ + backZ + (Random.nextFloat() - 0.5f) * 1.5f,
            velX = (Random.nextFloat() - 0.5f) * 2f,
            velY = 1f + Random.nextFloat() * 2f,
            velZ = (Random.nextFloat() - 0.5f) * 2f,
            life = 0.8f + Random.nextFloat() * 0.6f,
            maxLife = 1.4f,
            size = 0.4f + Random.nextFloat() * 0.4f,
            color = 0x80CCCCCC.toInt()
        ))
    }

    fun spawnNitroFlame(posX: Float, posY: Float, posZ: Float, headingAngle: Float) {
        if (particles.size >= maxParticles) return
        val rad = Math.toRadians(headingAngle.toDouble()).toFloat()
        val backX = -sin(rad) * 2.5f
        val backZ = cos(rad) * 2.5f
        particles.add(Particle(
            posX = posX + backX,
            posY = posY + 0.2f,
            posZ = posZ + backZ,
            velX = -sin(rad) * 8f + (Random.nextFloat() - 0.5f) * 2f,
            velY = Random.nextFloat() * 2f,
            velZ = cos(rad) * 8f + (Random.nextFloat() - 0.5f) * 2f,
            life = 0.3f + Random.nextFloat() * 0.3f,
            maxLife = 0.6f,
            size = 0.3f + Random.nextFloat() * 0.3f,
            color = if (Random.nextBoolean()) 0xFF00E5FF.toInt() else 0xFFFF5722.toInt()
        ))
    }

    fun spawnExplosion(posX: Float, posY: Float, posZ: Float) {
        for (i in 0 until 30) {
            if (particles.size >= maxParticles) break
            val angle = Random.nextFloat() * 360f
            val speed = 8f + Random.nextFloat() * 15f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            particles.add(Particle(
                posX = posX,
                posY = posY,
                posZ = posZ,
                velX = sin(rad) * speed,
                velY = 5f + Random.nextFloat() * 10f,
                velZ = cos(rad) * speed,
                life = 0.8f + Random.nextFloat() * 0.8f,
                maxLife = 1.6f,
                size = 0.2f + Random.nextFloat() * 0.3f,
                color = when (Random.nextInt(3)) {
                    0 -> 0xFFFF5722.toInt()
                    1 -> 0xFFFFD600.toInt()
                    else -> 0xFFFF1744.toInt()
                }
            ))
        }
    }

    fun update(dt: Float) {
        particles.removeAll { !it.update(dt) }
    }

    fun getParticles(): List<Particle> = particles.toList()

    fun clear() {
        particles.clear()
    }
}
