package com.openworld.racer.physics

import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ObjectType
import com.openworld.racer.model.VehicleConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
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
    var currentSurface = "ASPHALT" // "ASPHALT", "SIDEWALK", "DIRT", "WATER"

    // Suspension Travel per Wheel (0.0 = fully extended, 1.0 = compressed)
    val wheelSuspension = FloatArray(4) { 0.0f } // FL, FR, RL, RR
    val wheelRotationDeg = FloatArray(4) { 0.0f }

    // Controls
    var throttleInput = 0.0f // 0.0 ~ 1.0
    var brakeInput = 0.0f // 0.0 ~ 1.0
    var steeringInput = 0.0f // -1.0 (Left) ~ +1.0 (Right)
    var handbrakeInput = false

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
    }

    fun update(dt: Float, environmentObjects: List<EnvironmentObject>) {
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

        // Check Terrain Surface & Resolve Collisions
        val targetSurfaceY = resolveCollisionsAndDetectSurface(environmentObjects)

        // Apply Gravity & Ground Clamping
        if (posY > targetSurfaceY) {
            velY -= 18.0f * dt // 18 m/s^2 gravity
        }
        posY += velY * dt
        if (posY <= targetSurfaceY) {
            posY = targetSurfaceY
            velY = 0.0f
        }

        // Tire Grip based on Surface & PSI
        var gripFactor = config.getTireGripFactor(currentSurface)
        if (handbrakeInput) {
            gripFactor *= 0.35f // Handbrake triggers rear slip/drift!
        }

        // Motor Acceleration Force
        var driveForce = 0.0f
        if (batterySocPercent > 0.1f && throttleInput > 0.01f) {
            val maxMotorTorque = config.maxTorqueNm * (if (config.motorLayout == "AWD") 1.25f else 1.0f)
            val maxMotorPowerWatt = config.maxPowerKw * 1000f

            // Motor Torque-RPM Curve simulation
            val currentRpm = (speedKmh * 60f).coerceIn(100f, 16000f)
            val torqueByPowerLimit = (maxMotorPowerWatt / (currentRpm * 0.1047f)).coerceAtMost(maxMotorTorque)
            val engineForce = (torqueByPowerLimit * 8.0f * throttleInput) / (config.wheelDiameterInches * 0.0254f / 2f)

            driveForce = engineForce.coerceAtMost(mass * 9.81f * gripFactor * 1.2f)

            // Battery Consumption
            val instantaneousPowerKw = (abs(engineForce * forwardSpeed) / 1000f).coerceIn(0f, config.maxPowerKw)
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
                brakeForce = -brakeInput * mass * 8.5f * gripFactor
            } else if (brakeInput > 0.1f) {
                // Reverse Driving
                driveForce = -brakeInput * mass * 3.5f
            }
        }

        // Total Longitudinal Force
        val totalLongitudinalForce = driveForce + brakeForce

        // Aerodynamic Drag & Rolling Resistance
        val CdA = 0.45f + (config.downforceKg * 0.0008f)
        val airDragForce = -0.5f * 1.225f * CdA * forwardSpeed * abs(forwardSpeed)
        val rollingResistanceForce = -config.getRollingResistance() * mass * 9.81f * (if (forwardSpeed >= 0) 1f else -1f)

        val netForwardForce = totalLongitudinalForce + airDragForce + rollingResistanceForce
        val forwardAccel = netForwardForce / mass

        // Steering & Yaw Dynamics
        val maxSteerAngleDeg = (38f - (speedKmh * 0.15f)).coerceAtLeast(12f)
        val targetSteerDeg = steeringInput * maxSteerAngleDeg

        // Cornering Lateral Force (Centripetal friction)
        val maxCorneringGripForce = mass * 9.81f * gripFactor
        val requiredCorneringForce = mass * (forwardSpeed * (targetSteerDeg * 0.025f))

        // Check if car is drifting
        isDrifting = abs(lateralSpeed) > 3.2f || (handbrakeInput && speedKmh > 15f)

        val actualCorneringForce = requiredCorneringForce.coerceIn(-maxCorneringGripForce, maxCorneringGripForce)
        val lateralAccel = -actualCorneringForce / mass

        // Angular Yaw Rate Update
        val steerYawRate = (forwardSpeed * sin(Math.toRadians(targetSteerDeg.toDouble())) / 2.6f).toFloat() * 57.2958f
        yawRate = yawRate * 0.85f + steerYawRate * 0.15f
        headingAngle += yawRate * dt

        // Update Velocities in World Coordinates
        val newForwardSpeed = (forwardSpeed + forwardAccel * dt)
        val newLateralSpeed = (lateralSpeed + lateralAccel * dt) * (if (isDrifting) 0.94f else 0.40f)

        velX = newForwardSpeed * forwardX + newLateralSpeed * rightX
        velZ = newForwardSpeed * forwardZ + newLateralSpeed * rightZ

        // Water slowing down effect
        if (isInWater) {
            velX *= 0.82f
            velZ *= 0.82f
        }

        // Update Position
        posX += velX * dt
        posZ += velZ * dt

        // STABLE Pitch & Roll Angle Simulation (No sky flying!)
        val targetPitch = (-forwardAccel * 0.10f).coerceIn(-10.0f, 10.0f)
        pitchAngle = pitchAngle * 0.85f + targetPitch * 0.15f

        val targetRoll = (lateralAccel * 0.08f).coerceIn(-8.0f, 8.0f)
        rollAngle = rollAngle * 0.85f + targetRoll * 0.15f

        // Wheel Rotations
        val wheelRotSpeed = (forwardSpeed / (config.wheelDiameterInches * 0.0254f / 2f)) * dt * 57.2958f
        for (i in 0..3) {
            wheelRotationDeg[i] = (wheelRotationDeg[i] + wheelRotSpeed) % 360f
            wheelSuspension[i] = (0.2f + sin((posX + posZ + i) * 2.0f) * 0.03f).coerceIn(0f, 1f)
        }
    }

    private fun resolveCollisionsAndDetectSurface(environmentObjects: List<EnvironmentObject>): Float {
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
                    ObjectType.BUILDING, ObjectType.TREE, ObjectType.LAMPPOST, ObjectType.SIGNBOARD -> {
                        if (obj.isCollidable) {
                            // AABB Push-Out Collision Resolution (Prevents clipping & flying!)
                            val overlapX = halfX - abs(dx)
                            val overlapZ = halfZ - abs(dz)

                            if (overlapX < overlapZ) {
                                posX += if (dx > 0) overlapX else -overlapX
                                velX = 0f
                            } else {
                                posZ += if (dz > 0) overlapZ else -overlapZ
                                velZ = 0f
                            }
                            speedKmh *= 0.4f
                        }
                    }
                    ObjectType.SIDEWALK -> {
                        currentSurface = "SIDEWALK"
                        targetY = 0.12f // Curb height
                    }
                    ObjectType.SPEEDBUMP -> {
                        targetY = 0.08f // Bump height
                    }
                    ObjectType.RIVER, ObjectType.LAKE -> {
                        currentSurface = "WATER"
                        isInWater = true
                        targetY = -0.3f // Water level
                    }
                    ObjectType.GRASS -> {
                        currentSurface = "DIRT"
                    }
                    else -> {}
                }
            }
        }

        return targetY
    }
}
