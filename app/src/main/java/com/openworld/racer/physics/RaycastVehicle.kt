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

        // Check Terrain Surface & Collisions
        currentSurface = detectSurfaceAndCollision(environmentObjects)

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

            // Battery Consumption (Power Output in kW * time)
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
        val CdA = 0.55f + (config.downforceKg * 0.001f)
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

        // Suspension pitch and roll simulation
        val CoGZ = config.getCenterOfMassZ()
        pitchAngle = pitchAngle * 0.9f + (-forwardAccel * 0.35f + CoGZ * 2.0f) * 0.1f
        rollAngle = rollAngle * 0.9f + (lateralAccel * 0.40f) * 0.1f

        // Wheel Rotations
        val wheelRotSpeed = (forwardSpeed / (config.wheelDiameterInches * 0.0254f / 2f)) * dt * 57.2958f
        for (i in 0..3) {
            wheelRotationDeg[i] = (wheelRotationDeg[i] + wheelRotSpeed) % 360f
            // Suspension compression bouncy effect
            wheelSuspension[i] = (0.2f + sin((posX + posZ + i) * 2.0f) * 0.05f).coerceIn(0f, 1f)
        }
    }

    private fun detectSurfaceAndCollision(environmentObjects: List<EnvironmentObject>): String {
        var surface = "ASPHALT"
        isInWater = false

        // Car collision radius ~ 1.5 meters
        val carRadius = 1.5f

        for (obj in environmentObjects) {
            if (obj.intersectsBoundingBox(posX, posY, posZ, carRadius)) {
                when (obj.type) {
                    ObjectType.BUILDING, ObjectType.SIGNBOARD, ObjectType.LAMPPOST, ObjectType.TREE -> {
                        // Bounce off solid collision object
                        velX = -velX * 0.4f
                        velZ = -velZ * 0.4f
                        speedKmh *= 0.3f
                    }
                    ObjectType.SIDEWALK -> {
                        surface = "SIDEWALK"
                        posY = 0.25f // Elevation step for curb
                    }
                    ObjectType.SPEEDBUMP -> {
                        posY = 0.15f
                    }
                    ObjectType.RIVER, ObjectType.LAKE -> {
                        surface = "WATER"
                        isInWater = true
                        posY = -0.4f
                    }
                    ObjectType.GRASS -> {
                        surface = "DIRT"
                    }
                    else -> {}
                }
            }
        }
        if (surface == "ASPHALT" && posY > 0.05f) {
            posY = max(0.0f, posY - 0.05f)
        }
        return surface
    }
}
