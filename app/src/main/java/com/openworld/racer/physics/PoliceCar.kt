package com.openworld.racer.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class PoliceState {
    PATROL,
    PURSUIT,
    BLOCK,
    RAM
}

class PoliceCar(
    var posX: Float = 0f,
    var posY: Float = 0f,
    var posZ: Float = 0f,
    var headingAngle: Float = 0f
) {
    var speedKmh: Float = 0f
    var state: PoliceState = PoliceState.PATROL
    var sirenPhase: Float = 0f
    var targetPosX: Float = 0f
    var targetPosZ: Float = 0f
    var patrolAngle: Float = 0f
    var ramCooldown: Float = 0f
    var blockTimer: Float = 0f

    val maxSpeedKmh: Float = 200f
    val acceleration: Float = 15f
    val rammingDamage: Float = 8f

    fun update(dt: Float, targetVehicle: RaycastVehicle?) {
        sirenPhase += dt * 8f
        if (ramCooldown > 0f) ramCooldown -= dt
        if (blockTimer > 0f) blockTimer -= dt

        when (state) {
            PoliceState.PATROL -> updatePatrol(dt)
            PoliceState.PURSUIT -> updatePursuit(dt, targetVehicle)
            PoliceState.BLOCK -> updateBlock(dt, targetVehicle)
            PoliceState.RAM -> updateRam(dt, targetVehicle)
        }

        // Update position based on speed and heading
        val rad = Math.toRadians(headingAngle.toDouble()).toFloat()
        val speedMs = speedKmh / 3.6f
        posX += sin(rad) * speedMs * dt
        posZ -= cos(rad) * speedMs * dt
    }

    private fun updatePatrol(dt: Float) {
        patrolAngle += dt * 30f
        val rad = Math.toRadians(patrolAngle.toDouble()).toFloat()
        targetPosX = sin(rad) * 100f
        targetPosZ = cos(rad) * 100f

        val dx = targetPosX - posX
        val dz = targetPosZ - posZ
        val targetHeading = Math.toDegrees(Math.atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        headingAngle = lerpAngle(headingAngle, targetHeading, dt * 2f)

        speedKmh = (speedKmh + acceleration * dt).coerceAtMost(80f)
    }

    private fun updatePursuit(dt: Float, target: RaycastVehicle?) {
        if (target == null) {
            state = PoliceState.PATROL
            return
        }

        val dx = target.posX - posX
        val dz = target.posZ - posZ
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)

        // Target heading towards player
        val targetHeading = Math.toDegrees(Math.atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        headingAngle = lerpAngle(headingAngle, targetHeading, dt * 3f)

        // Match player speed + bonus
        val targetSpeed = target.speedKmh + 20f
        speedKmh = (speedKmh + acceleration * dt).coerceAtMost(targetSpeed.coerceAtMost(maxSpeedKmh))

        // Transition to RAM if close
        if (dist < 15f && ramCooldown <= 0f) {
            state = PoliceState.RAM
        } else if (dist < 40f && blockTimer <= 0f && Random.nextFloat() < 0.3f) {
            state = PoliceState.BLOCK
            blockTimer = 3f
        }
    }

    private fun updateBlock(dt: Float, target: RaycastVehicle?) {
        if (target == null || blockTimer <= 0f) {
            state = PoliceState.PURSUIT
            return
        }

        // Get ahead of target and slow down
        val rad = Math.toRadians(target.headingAngle.toDouble()).toFloat()
        val aheadDist = 25f
        targetPosX = target.posX + sin(rad) * aheadDist
        targetPosZ = target.posZ - cos(rad) * aheadDist

        val dx = targetPosX - posX
        val dz = targetPosZ - posZ
        val targetHeading = Math.toDegrees(Math.atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        headingAngle = lerpAngle(headingAngle, targetHeading, dt * 4f)

        // Slow down to block
        speedKmh = (speedKmh - acceleration * 0.5f * dt).coerceAtLeast(target.speedKmh * 0.7f)
    }

    private fun updateRam(dt: Float, target: RaycastVehicle?) {
        if (target == null) {
            state = PoliceState.PURSUIT
            return
        }

        // Aggressive ramming
        val dx = target.posX - posX
        val dz = target.posZ - posZ
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)

        val targetHeading = Math.toDegrees(Math.atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        headingAngle = lerpAngle(headingAngle, targetHeading, dt * 5f)

        speedKmh = (speedKmh + acceleration * 1.5f * dt).coerceAtMost(maxSpeedKmh)

        // Check ram hit
        if (dist < 3.5f && ramCooldown <= 0f) {
            ramCooldown = 2f
            // Damage is applied externally via callback
        }

        // Return to pursuit if too far
        if (dist > 30f) {
            state = PoliceState.PURSUIT
        }
    }

    fun getSirenColor(): Int {
        return if (sin(sirenPhase) > 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
    }

    private fun lerpAngle(current: Float, target: Float, t: Float): Float {
        var diff = target - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return (current + diff * t.coerceIn(0f, 1f)) % 360f
    }
}

object PoliceManager {
    private val policeCars = mutableListOf<PoliceCar>()
    private var spawnTimer = 0f
    private var maxPoliceCars = 3

    fun spawnPolice(car: PoliceCar) {
        if (policeCars.size < maxPoliceCars) {
            policeCars.add(car)
        }
    }

    fun updateAll(dt: Float, targetVehicle: RaycastVehicle?) {
        spawnTimer += dt

        // Spawn new police cars periodically during chase
        if (targetVehicle != null && spawnTimer > 5f && policeCars.size < maxPoliceCars) {
            spawnTimer = 0f
            val angle = Random.nextFloat() * 360f
            val dist = 80f + Random.nextFloat() * 40f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val spawnX = targetVehicle.posX + sin(rad) * dist
            val spawnZ = targetVehicle.posZ + cos(rad) * dist
            spawnPolice(PoliceCar(spawnX, 0f, spawnZ, angle).apply {
                state = PoliceState.PURSUIT
                speedKmh = 100f
            })
        }

        for (car in policeCars) {
            car.update(dt, targetVehicle)
        }
    }

    fun getActiveCount(): Int = policeCars.size

    fun getPoliceCars(): List<PoliceCar> = policeCars.toList()

    fun clearAll() {
        policeCars.clear()
        spawnTimer = 0f
    }

    fun setMaxPolice(count: Int) {
        maxPoliceCars = count.coerceIn(1, 6)
    }

    // Check if any police car is close enough to damage player
    fun checkRamDamage(targetX: Float, targetZ: Float): Float {
        var totalDamage = 0f
        for (car in policeCars) {
            val dx = car.posX - targetX
            val dz = car.posZ - targetZ
            val dist = kotlin.math.sqrt(dx * dx + dz * dz)
            if (dist < 4f) {
                totalDamage += car.rammingDamage
            }
        }
        return totalDamage
    }
}
