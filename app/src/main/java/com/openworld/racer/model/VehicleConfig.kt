package com.openworld.racer.model

import java.io.Serializable
import kotlin.math.pow

data class VehicleConfig(
    var isEasyMode: Boolean = true,
    var presetName: String = "City Commuter EV",

    // Custom Car Shape Drawing Profile (16 Normalized Height Points 0.0 ~ 1.0)
    var isCustomDrawnBody: Boolean = false,
    var customProfile: FloatArray = floatArrayOf(
        0.35f, 0.45f, 0.55f, 0.70f, 0.95f, 1.00f, 1.00f, 0.95f,
        0.90f, 0.85f, 0.65f, 0.55f, 0.50f, 0.45f, 0.40f, 0.35f
    ),

    // Motor Tuning
    var motorLayout: String = "RWD", // "FWD", "RWD", "AWD"
    var motorCount: Int = 1, // 1, 2, 4
    var maxPowerKw: Float = 150f, // 100 ~ 800 kW
    var maxTorqueNm: Float = 310f, // 200 ~ 1200 Nm

    // Battery Tuning
    var batteryCapacityKwh: Float = 60f, // 40 ~ 140 kWh
    var batteryWeightKg: Float = 450f, // 300 ~ 900 kg
    var batteryPositionZ: Float = 0.0f, // -0.8f (Rear) ~ 0.8f (Front)

    // Wheel & Tire Tuning
    var wheelDiameterInches: Float = 18f, // 16 ~ 22 inches
    var tireWidthMm: Float = 225f, // 195 ~ 325 mm
    var tirePressurePsi: Float = 33f, // 20 ~ 50 PSI

    // Suspension & Chassis
    var suspensionStiffness: Float = 0.5f, // 0.2 (Soft) ~ 1.0 (Stiff)
    var rideHeightCm: Float = 15f, // 10 ~ 25 cm
    var downforceKg: Float = 50f, // 0 ~ 300 kg @ 100km/h

    // Appearance
    var bodyColor: Int = 0xFF00E5FF.toInt(), // Neon Blue default
    var rimColor: Int = 0xFFCCCCCC.toInt()
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VehicleConfig
        return customProfile.contentEquals(other.customProfile)
    }

    override fun hashCode(): Int {
        return customProfile.contentHashCode()
    }

    // Calculate Total Vehicle Weight in kg
    fun getTotalMassKg(): Float {
        val baseChassisMass = 750f
        val motorMass = motorCount * 65f
        val rimMass = 4 * (wheelDiameterInches * 0.8f)
        return baseChassisMass + motorMass + batteryWeightKg + rimMass
    }

    // Calculate Center of Mass Z (-Rear / +Front)
    fun getCenterOfMassZ(): Float {
        val totalMass = getTotalMassKg()
        val batteryContribution = batteryWeightKg * batteryPositionZ
        val motorContribution = when (motorLayout) {
            "FWD" -> 0.8f * 130f
            "RWD" -> -0.8f * 130f
            else -> 0.0f
        }
        return (batteryContribution + motorContribution) / totalMass
    }

    // Tire Cornering & Acceleration Grip Coefficient (0.7 ~ 1.6)
    fun getTireGripFactor(surfaceType: String = "ASPHALT"): Float {
        val widthFactor = tireWidthMm / 225f
        val psiFactor = if (surfaceType == "DIRT") {
            1.2f - ((tirePressurePsi - 24f).pow(2) / 400f)
        } else {
            1.3f - ((tirePressurePsi - 33f).pow(2) / 500f)
        }
        val baseGrip = if (surfaceType == "DIRT") 0.75f else 1.15f
        return (baseGrip * widthFactor * psiFactor.coerceIn(0.6f, 1.3f)).coerceIn(0.5f, 1.8f)
    }

    // Rolling resistance coefficient
    fun getRollingResistance(): Float {
        return 0.015f + (35f - tirePressurePsi.coerceIn(20f, 50f)) * 0.0003f
    }

    // Estimated 0-100 km/h acceleration time
    fun getZeroToHundredSec(): Float {
        val mass = getTotalMassKg()
        val torque = maxTorqueNm * (if (motorLayout == "AWD") 1.2f else 1.0f)
        val grip = getTireGripFactor("ASPHALT")
        val maxForceByTraction = mass * 9.81f * grip
        val forceByTorque = (torque * 8.0f) / (wheelDiameterInches * 0.0254f / 2f)
        val effectiveForce = minOf(maxForceByTraction, forceByTorque)
        val accel = effectiveForce / mass
        return (27.78f / accel).coerceIn(1.9f, 12.0f)
    }

    // Top Speed in km/h
    fun getTopSpeedKmh(): Float {
        val powerWatt = maxPowerKw * 1000f
        val CdA = 0.55f + (downforceKg * 0.0005f)
        val maxVelMs = (powerWatt / (0.5f * 1.225f * CdA)).pow(1f / 3f)
        return (maxVelMs * 3.6f).coerceIn(120f, 380f)
    }

    // Estimated Battery Driving Range in km
    fun getEstimatedRangeKm(): Float {
        val mass = getTotalMassKg()
        val rollingCoeff = getRollingResistance()
        val energyPerKmWh = (mass * rollingCoeff * 9.81f + 120f) * 0.65f
        return (batteryCapacityKwh / (energyPerKmWh / 1000f)).coerceIn(150f, 850f)
    }

    fun applyEasyPreset(preset: String) {
        this.isEasyMode = true
        this.presetName = preset
        this.isCustomDrawnBody = false
        when (preset) {
            "City Commuter EV" -> {
                motorLayout = "FWD"
                motorCount = 1
                maxPowerKw = 130f
                maxTorqueNm = 290f
                batteryCapacityKwh = 55f
                batteryWeightKg = 380f
                batteryPositionZ = 0.1f
                wheelDiameterInches = 17f
                tireWidthMm = 215f
                tirePressurePsi = 35f
                suspensionStiffness = 0.4f
                rideHeightCm = 16f
                downforceKg = 20f
                bodyColor = 0xFF00E5FF.toInt()
            }
            "Sport Dual Motor EV" -> {
                motorLayout = "AWD"
                motorCount = 2
                maxPowerKw = 350f
                maxTorqueNm = 650f
                batteryCapacityKwh = 85f
                batteryWeightKg = 520f
                batteryPositionZ = 0.0f
                wheelDiameterInches = 19f
                tireWidthMm = 265f
                tirePressurePsi = 33f
                suspensionStiffness = 0.7f
                rideHeightCm = 13f
                downforceKg = 120f
                bodyColor = 0xFFFF1744.toInt()
            }
            "Off-Road Beast 4WD" -> {
                motorLayout = "AWD"
                motorCount = 2
                maxPowerKw = 320f
                maxTorqueNm = 720f
                batteryCapacityKwh = 100f
                batteryWeightKg = 600f
                batteryPositionZ = -0.1f
                wheelDiameterInches = 18f
                tireWidthMm = 285f
                tirePressurePsi = 24f
                suspensionStiffness = 0.35f
                rideHeightCm = 22f
                downforceKg = 40f
                bodyColor = 0xFFFF9100.toInt()
            }
            "Long-Range Touring EV" -> {
                motorLayout = "RWD"
                motorCount = 1
                maxPowerKw = 220f
                maxTorqueNm = 440f
                batteryCapacityKwh = 130f
                batteryWeightKg = 720f
                batteryPositionZ = -0.2f
                wheelDiameterInches = 18f
                tireWidthMm = 235f
                tirePressurePsi = 38f
                suspensionStiffness = 0.5f
                rideHeightCm = 15f
                downforceKg = 30f
                bodyColor = 0xFF00E676.toInt()
            }
            "Hypercar Quad EV" -> {
                motorLayout = "AWD"
                motorCount = 4
                maxPowerKw = 750f
                maxTorqueNm = 1250f
                batteryCapacityKwh = 110f
                batteryWeightKg = 650f
                batteryPositionZ = -0.1f
                wheelDiameterInches = 21f
                tireWidthMm = 325f
                tirePressurePsi = 32f
                suspensionStiffness = 0.9f
                rideHeightCm = 10f
                downforceKg = 250f
                bodyColor = 0xFFFFEA00.toInt()
            }
        }
    }
}
