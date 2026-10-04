package com.openworld.racer.model

import kotlin.math.sin

enum class ItemType {
    NITRO_BOOST,    // Blue/Cyan fuel canister - refills nitro + triggers speed burst
    GOLD_GEM,       // Golden spinning diamond - awards 200 points + combo
    MEGA_STAR       // Glowing star - awards 500 points + instant nitro refill
}

data class CollectibleItem(
    val id: String,
    val type: ItemType,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    var isCollected: Boolean = false,
    var respawnTimer: Float = 0f,
    val respawnDuration: Float = 8f, // Respawns after 8s for continuous stunt fun
    var currentRotationY: Float = 0f,
    var bobbingPhase: Float = 0f
) {
    fun getRenderY(): Float {
        // Floating bobbing effect
        return posY + sin(bobbingPhase) * 0.35f
    }

    fun update(dt: Float) {
        currentRotationY = (currentRotationY + 140f * dt) % 360f
        bobbingPhase = (bobbingPhase + 3.2f * dt) % (2.0f * Math.PI.toFloat())

        if (isCollected) {
            respawnTimer -= dt
            if (respawnTimer <= 0f) {
                isCollected = false
            }
        }
    }

    fun checkCollection(carX: Float, carY: Float, carZ: Float, collectionRadius: Float = 2.4f): Boolean {
        if (isCollected) return false
        val dx = carX - posX
        val dy = carY - getRenderY()
        val dz = carZ - posZ
        val distSq = dx * dx + dy * dy + dz * dz
        val radius = collectionRadius + 0.6f
        if (distSq <= radius * radius) {
            isCollected = true
            respawnTimer = respawnDuration
            return true
        }
        return false
    }
}
