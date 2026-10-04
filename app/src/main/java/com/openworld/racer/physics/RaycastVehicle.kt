package com.openworld.racer.physics

import com.openworld.racer.audio.SoundManager
import com.openworld.racer.model.CollectibleItem
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ItemType
import com.openworld.racer.model.ObjectType
import com.openworld.racer.model.VehicleConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class RaycastVehicle(var config: VehicleConfig) {

    // Position in World Space
    var posX = 0.0f
    var posY = 0.0f // Ground level ~ 0.0f
    var posZ = 0.0f

    // Orientation (Degrees)
    var headingAngle = 0.0f // Yaw (0 = facing -Z)
    var pitchAngle = 0.0f
    var rollAngle = 0.0f

    // Velocity (m/s)
    var velX = 0.0f
    var velY = 0.0f
    var velZ = 0.0f
    var yawRate = 0.0f // Angular speed (deg/s)

    // Current State
    var speedKmh = 0.0f
    var motorPowerKwCurrent = 0.0f
    var batterySocPercent = 100.0f // State of Charge %
    var isDrifting = false
    var isInWater = false
    var currentSurface = "ASPHALT" // "ASPHALT", "SIDEWALK", "DIRT", "WATER", "RAMP", "BOOST"

    // Suspension Travel per Wheel (0.0 = fully extended, 1.0 = compressed)
    val wheelSuspension = FloatArray(4) { 0.0f } // FL, FR, RL, RR
    val wheelRotationDeg = FloatArray(4) { 0.0f }

    // Controls
    var throttleInput = 0.0f // 0.0 ~ 1.0
    var brakeInput = 0.0f // 0.0 ~ 1.0
    var steeringInput = 0.0f // -1.0 (Left) ~ +1.0 (Right)
    var handbrakeInput = false
    var nitroButtonInput = false

    // Smooth progressive steering
    var smoothedSteer = 0.0f

    // Stunt Jump & Airborne Physics
    var isAirborne = false
    var airTimeSeconds = 0.0f
    var currentJumpApexY = 0.0f
    var totalScore = 0
    var comboMultiplier = 1
    var comboTimer = 0.0f
    var lastStuntScore = 0
    var stuntFeedbackText = ""
    var stuntFeedbackTimer = 0.0f

    // Nitro Boost System
    var nitroGauge = 50.0f // 0.0 ~ 100.0%
    var isNitroActive = false
    var nitroRemainingDuration = 0.0f

    // Boost pad debounce & impulse
    private var boostPadCooldown = 0.0f
    var pendingBoostImpulse = 0.0f

    fun resetPosition(x: Float, y: Float, z: Float, angle: Float = 0f) {
        posX = x
        posY = y
        posZ = z
        headingAngle = angle
        pitchAngle = 0f
        rollAngle = 0f
        velX = 0f
        velY = 0f
        velZ = 0f
        yawRate = 0f
        speedKmh = 0f
        isDrifting = false
        isInWater = false
        isAirborne = false
        airTimeSeconds = 0f
        currentJumpApexY = 0f
        isNitroActive = false
        nitroRemainingDuration = 0f
        smoothedSteer = 0f
        boostPadCooldown = 0f
    }

    fun triggerNitroBoost(): Boolean {
        if (nitroGauge >= 20.0f && !isNitroActive) {
            isNitroActive = true
            nitroRemainingDuration = 3.5f
            nitroGauge = (nitroGauge - 25.0f).coerceAtLeast(0.0f)
            return true
        }
        return false
    }

    fun update(
        dt: Float,
        environmentObjects: List<EnvironmentObject>,
        collectibleItems: List<CollectibleItem>? = null,
        soundManager: SoundManager? = null
    ) {
        val mass = config.getTotalMassKg()
        val rad = Math.toRadians(headingAngle.toDouble()).toFloat()

        // Forward and Right Direction Vectors
        val forwardX = sin(rad)
        val forwardZ = -cos(rad)
        val rightX = cos(rad)
        val rightZ = sin(rad)

        // Decompose velocity into local Forward & Lateral speeds
        val forwardSpeed = velX * forwardX + velZ * forwardZ
        val lateralSpeed = velX * rightX + velZ * rightZ
        speedKmh = abs(forwardSpeed) * 3.6f

        // Handle Nitro Button & Cooldowns
        if (nitroButtonInput) {
            triggerNitroBoost()
        }

        if (isNitroActive) {
            nitroRemainingDuration -= dt
            if (nitroRemainingDuration <= 0f) {
                isNitroActive = false
            }
        }

        if (boostPadCooldown > 0f) {
            boostPadCooldown -= dt
        }

        // Combo Timer countdown
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) {
                comboMultiplier = 1
            }
        }

        // Stunt Feedback display timer
        if (stuntFeedbackTimer > 0f) {
            stuntFeedbackTimer -= dt
            if (stuntFeedbackTimer <= 0f) {
                stuntFeedbackText = ""
            }
        }

        // Smooth Progressive Steering (Smooth ramp-up, snappy centering)
        val steerRate = if (abs(steeringInput) > 0.01f) 14.0f else 24.0f
        smoothedSteer += (steeringInput - smoothedSteer) * (steerRate * dt).coerceAtMost(1.0f)

        // Surface Detection, Ramps & Collisions
        val targetSurfaceY = resolveEnvironment(environmentObjects, forwardSpeed, forwardX, forwardZ, dt, soundManager)

        // Airborne & Gravity Simulation (Anti-Sky Flying with Hard Limits)
        if (isAirborne || posY > targetSurfaceY + 0.05f) {
            isAirborne = true
            airTimeSeconds += dt
            velY -= 20.0f * dt // Consistent 20 m/s^2 gravity
            posY += velY * dt
            if (posY > currentJumpApexY) {
                currentJumpApexY = posY
            }

            // Aerodynamic pitch alignment in mid-air
            val targetAirPitch = (velY * -0.7f).coerceIn(-20f, 20f)
            pitchAngle = pitchAngle * 0.90f + targetAirPitch * 0.10f
            rollAngle = rollAngle * 0.90f

            // HARD SAFETY LIMIT: Car can NEVER fly above 32m into space!
            if (posY > 32.0f) {
                posY = 32.0f
                if (velY > 0f) velY = 0f
            }

            // Landing check
            if (posY <= targetSurfaceY && velY <= 0.0f) {
                posY = targetSurfaceY
                velY = 0.0f
                val wasAirborne = isAirborne
                isAirborne = false

                if (wasAirborne && airTimeSeconds > 0.30f) {
                    // Stunt Landing Score Calculation!
                    val heightGain = (currentJumpApexY - targetSurfaceY).coerceAtLeast(0f)
                    val jumpScore = ((airTimeSeconds * 300f + heightGain * 30f).toInt() * comboMultiplier).coerceAtLeast(50)
                    totalScore += jumpScore
                    lastStuntScore = jumpScore
                    stuntFeedbackText = if (airTimeSeconds > 1.0f) {
                        String.format("🚀 MEGA AIR! %.1fs (+%d PTS)", airTimeSeconds, jumpScore)
                    } else {
                        String.format("✨ JUMP! (+%d PTS)", jumpScore)
                    }
                    stuntFeedbackTimer = 2.5f
                    comboMultiplier = (comboMultiplier + 1).coerceAtMost(8)
                    comboTimer = 6.0f
                    soundManager?.playLandingSound()
                }
            }
        } else {
            posY = targetSurfaceY
            velY = 0.0f
            isAirborne = false
        }

        // Tire Grip based on Surface & PSI
        var gripFactor = config.getTireGripFactor(currentSurface)
        if (handbrakeInput) {
            gripFactor *= 0.35f // Drift trigger!
            // Recharging Nitro while drifting!
            if (speedKmh > 20f) {
                nitroGauge = (nitroGauge + 18.0f * dt).coerceAtMost(100.0f)
            }
        }

        // Motor Acceleration Force + Nitro Boost
        var driveForce = 0.0f
        val nitroBoostFactor = if (isNitroActive) 2.2f else 1.0f

        if (batterySocPercent > 0.1f && (throttleInput > 0.01f || isNitroActive)) {
            val maxMotorTorque = config.maxTorqueNm * (if (config.motorLayout == "AWD") 1.25f else 1.0f) * nitroBoostFactor
            val maxMotorPowerWatt = config.maxPowerKw * 1000f * nitroBoostFactor

            val effectiveThrottle = if (isNitroActive) 1.0f else throttleInput
            val currentRpm = (speedKmh * 60f).coerceIn(100f, 18000f)
            val torqueByPowerLimit = (maxMotorPowerWatt / (currentRpm * 0.1047f)).coerceAtMost(maxMotorTorque)
            val engineForce = (torqueByPowerLimit * 8.5f * effectiveThrottle) / (config.wheelDiameterInches * 0.0254f / 2f)

            driveForce = engineForce.coerceAtMost(mass * 9.81f * gripFactor * (if (isNitroActive) 2.5f else 1.2f))

            val instantaneousPowerKw = (abs(engineForce * forwardSpeed) / 1000f).coerceIn(0f, config.maxPowerKw * nitroBoostFactor)
            motorPowerKwCurrent = instantaneousPowerKw
            val energyUsedKwh = (instantaneousPowerKw * (dt / 3600f))
            batterySocPercent = (batterySocPercent - (energyUsedKwh / config.batteryCapacityKwh * 100f)).coerceAtLeast(0f)
        } else {
            motorPowerKwCurrent = 0.0f
        }

        // Braking Force
        var brakeForce = 0.0f
        if (brakeInput > 0.01f) {
            if (forwardSpeed > 0.5f) {
                brakeForce = -brakeInput * mass * 9.5f * gripFactor
            } else if (brakeInput > 0.1f) {
                // Reverse Driving
                driveForce = -brakeInput * mass * 4.0f
            }
        }

        // Total Longitudinal Force
        val totalLongitudinalForce = driveForce + brakeForce

        // Aerodynamic Drag & Rolling Resistance
        val maxSpeedLimit = if (isNitroActive) 62.0f else 48.0f // ~223 km/h vs 172 km/h
        val CdA = 0.40f + (config.downforceKg * 0.0007f)
        val airDragForce = -0.5f * 1.225f * CdA * forwardSpeed * abs(forwardSpeed)
        val rollingResistanceForce = -config.getRollingResistance() * mass * 9.81f * (if (forwardSpeed >= 0) 1f else -1f)

        val netForwardForce = totalLongitudinalForce + airDragForce + rollingResistanceForce
        val forwardAccel = netForwardForce / mass

        // Steering & Yaw Dynamics (Speed-compensated stability)
        val maxSteerAngleDeg = (36f - (speedKmh * 0.12f)).coerceIn(14f, 36f)
        val targetSteerDeg = smoothedSteer * maxSteerAngleDeg

        val maxCorneringGripForce = mass * 9.81f * gripFactor
        val requiredCorneringForce = mass * (forwardSpeed * (targetSteerDeg * 0.024f))

        // Check if car is drifting
        isDrifting = abs(lateralSpeed) > 2.8f || (handbrakeInput && speedKmh > 15f)

        val actualCorneringForce = requiredCorneringForce.coerceIn(-maxCorneringGripForce, maxCorneringGripForce)
        val lateralAccel = -actualCorneringForce / mass

        // Angular Yaw Rate Update
        val steerYawRate = (forwardSpeed * sin(Math.toRadians(targetSteerDeg.toDouble())) / 2.6f).toFloat() * 57.2958f
        yawRate = yawRate * 0.82f + steerYawRate * 0.18f
        headingAngle += yawRate * dt

        // Update Velocities in World Coordinates
        var newForwardSpeed = (forwardSpeed + forwardAccel * dt + pendingBoostImpulse).coerceIn(-12f, maxSpeedLimit)
        pendingBoostImpulse = 0.0f
        val newLateralSpeed = (lateralSpeed + lateralAccel * dt) * (if (isDrifting) 0.94f else 0.40f)

        velX = newForwardSpeed * forwardX + newLateralSpeed * rightX
        velZ = newForwardSpeed * forwardZ + newLateralSpeed * rightZ

        // Water slowing down effect
        if (isInWater) {
            velX *= 0.85f
            velZ *= 0.85f
        }

        val finalForwardSpeed = velX * forwardX + velZ * forwardZ
        speedKmh = abs(finalForwardSpeed) * 3.6f

        // Update Position
        posX += velX * dt
        posZ += velZ * dt

        // Pitch & Roll Dynamics when on Ground
        if (!isAirborne && currentSurface != "RAMP") {
            val targetPitch = (-forwardAccel * 0.08f).coerceIn(-12.0f, 12.0f)
            pitchAngle = pitchAngle * 0.85f + targetPitch * 0.15f

            val targetRoll = (lateralAccel * 0.08f).coerceIn(-10.0f, 10.0f)
            rollAngle = rollAngle * 0.85f + targetRoll * 0.15f
        }

        // Wheel Rotations
        val wheelRotSpeed = (forwardSpeed / (config.wheelDiameterInches * 0.0254f / 2f)) * dt * 57.2958f
        for (i in 0..3) {
            wheelRotationDeg[i] = (wheelRotationDeg[i] + wheelRotSpeed) % 360f
            wheelSuspension[i] = (0.2f + sin((posX + posZ + i) * 2.0f) * 0.03f).coerceIn(0f, 1f)
        }

        // Update Collectible Items
        collectibleItems?.let { items ->
            for (item in items) {
                item.update(dt)
                if (item.checkCollection(posX, posY, posZ, 2.5f)) {
                    soundManager?.playItemChime()
                    when (item.type) {
                        ItemType.NITRO_BOOST -> {
                            nitroGauge = (nitroGauge + 40.0f).coerceAtMost(100.0f)
                            triggerNitroBoost()
                            val scoreAdd = 150 * comboMultiplier
                            totalScore += scoreAdd
                            stuntFeedbackText = String.format("⚡ NITRO BURST! (+%d)", scoreAdd)
                            stuntFeedbackTimer = 1.5f
                        }
                        ItemType.GOLD_GEM -> {
                            val scoreAdd = 250 * comboMultiplier
                            totalScore += scoreAdd
                            comboMultiplier = (comboMultiplier + 1).coerceAtMost(8)
                            comboTimer = 6.0f
                            stuntFeedbackText = String.format("💎 GOLD GEM! (+%d x%d)", scoreAdd, comboMultiplier)
                            stuntFeedbackTimer = 1.5f
                        }
                        ItemType.MEGA_STAR -> {
                            val scoreAdd = 600 * comboMultiplier
                            totalScore += scoreAdd
                            nitroGauge = 100.0f
                            comboMultiplier = (comboMultiplier + 2).coerceAtMost(8)
                            comboTimer = 8.0f
                            stuntFeedbackText = String.format("⭐ MEGA STAR! (+%d x%d)", scoreAdd, comboMultiplier)
                            stuntFeedbackTimer = 2.0f
                        }
                    }
                }
            }
        }
    }

    private fun resolveEnvironment(
        environmentObjects: List<EnvironmentObject>,
        forwardSpeed: Float,
        forwardX: Float,
        forwardZ: Float,
        dt: Float,
        soundManager: SoundManager?
    ): Float {
        var targetY = 0.0f
        currentSurface = "ASPHALT"
        isInWater = false

        val carRadius = 1.2f

        for (obj in environmentObjects) {
            val halfX = obj.sizeX / 2f + carRadius
            val halfZ = obj.sizeZ / 2f + carRadius

            val dx = posX - obj.posX
            val dz = posZ - obj.posZ

            if (abs(dx) < halfX && abs(dz) < halfZ) {
                when (obj.type) {
                    ObjectType.JUMP_RAMP -> {
                        // Stunt Launch Ramp (Smooth incline + launch apex)
                        val radY = Math.toRadians(-obj.rotationY.toDouble()).toFloat()
                        val localX = dx * cos(radY) - dz * sin(radY)
                        val localZ = dx * sin(radY) + dz * cos(radY)

                        val rampHalfX = obj.sizeX / 2f
                        val rampHalfZ = obj.sizeZ / 2f

                        if (abs(localX) <= rampHalfX && abs(localZ) <= rampHalfZ) {
                            // Progress from entrance (+localZ) to apex (-localZ)
                            val t = ((rampHalfZ - localZ) / obj.sizeZ).coerceIn(0f, 1f)
                            val rampSurfaceY = obj.posY + t * obj.sizeY

                            if (posY <= rampSurfaceY + 0.8f) {
                                targetY = rampSurfaceY
                                currentSurface = "RAMP"
                                pitchAngle = pitchAngle * 0.8f + (-18.0f) * 0.2f

                                // Check Launch at Apex
                                if (t >= 0.88f && forwardSpeed > 4.5f && !isAirborne) {
                                    isAirborne = true
                                    airTimeSeconds = 0.0f
                                    currentJumpApexY = posY
                                    velY = (forwardSpeed * 0.60f).coerceIn(8.5f, 24.0f)
                                    velX *= 1.08f
                                    velZ *= 1.08f
                                    stuntFeedbackText = "🚀 STUNT LAUNCH!"
                                    stuntFeedbackTimer = 1.5f
                                    soundManager?.playJumpLaunch()
                                }
                            }
                        }
                    }

                    ObjectType.BOOST_PAD -> {
                        // Dash Pad: +45 km/h instant thrust & Nitro recharge
                        if (boostPadCooldown <= 0f && forwardSpeed > 1f) {
                            boostPadCooldown = 1.0f
                            currentSurface = "BOOST"
                            pendingBoostImpulse += 14.0f // ~50 km/h boost
                            nitroGauge = (nitroGauge + 30.0f).coerceAtMost(100.0f)
                            stuntFeedbackText = "⚡ BOOST PAD!"
                            stuntFeedbackTimer = 1.2f
                            soundManager?.playJumpLaunch()
                        }
                    }

                    ObjectType.BARRIER, ObjectType.BUILDING, ObjectType.TREE -> {
                        if (obj.isCollidable && !isAirborne) {
                            // Robust Single-Pass AABB Pushout
                            val overlapX = halfX - abs(dx)
                            val overlapZ = halfZ - abs(dz)

                            if (overlapX < overlapZ) {
                                posX += if (dx > 0) overlapX else -overlapX
                                velX = 0f
                            } else {
                                posZ += if (dz > 0) overlapZ else -overlapZ
                                velZ = 0f
                            }
                            speedKmh *= 0.5f
                            soundManager?.triggerCollisionImpact()
                        }
                    }

                    ObjectType.SIDEWALK -> {
                        if (!isAirborne) {
                            currentSurface = "SIDEWALK"
                            targetY = 0.12f
                        }
                    }

                    ObjectType.RIVER, ObjectType.LAKE -> {
                        if (posY <= 0.2f) {
                            currentSurface = "WATER"
                            isInWater = true
                            targetY = -0.3f
                        }
                    }

                    ObjectType.GRASS -> {
                        if (!isAirborne) {
                            currentSurface = "DIRT"
                        }
                    }

                    else -> {}
                }
            }
        }

        return targetY
    }
}
