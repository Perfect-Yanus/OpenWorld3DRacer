package com.openworld.racer.model

enum class GameMode {
    FREE_RIDE,
    POLICE_CHASE,
    TIME_ATTACK
}

object GameModeManager {
    var currentMode: GameMode = GameMode.FREE_RIDE
        private set

    fun setMode(mode: GameMode) {
        currentMode = mode
        if (mode != GameMode.POLICE_CHASE) {
            resetWantedLevel()
            resetHealth()
        }
        if (mode != GameMode.TIME_ATTACK) {
            stopChase()
        }
    }

    fun getMode(): GameMode = currentMode
    fun isPoliceActive(): Boolean = currentMode == GameMode.POLICE_CHASE
    fun isTimeAttack(): Boolean = currentMode == GameMode.TIME_ATTACK

    // Wanted Level System (0-5 stars)
    var wantedLevel: Int = 0
        private set

    fun increaseWantedLevel() {
        wantedLevel = (wantedLevel + 1).coerceAtMost(5)
    }

    fun decreaseWantedLevel() {
        wantedLevel = (wantedLevel - 1).coerceAtLeast(0)
    }

    fun resetWantedLevel() {
        wantedLevel = 0
    }

    // Health System
    var health: Float = 100.0f
        private set

    fun damage(amount: Float) {
        health = (health - amount).coerceAtLeast(0f)
    }

    fun repair(amount: Float) {
        health = (health + amount).coerceAtMost(100f)
    }

    fun isBusted(): Boolean = health <= 0f

    fun resetHealth() {
        health = 100f
    }

    // Chase Timer
    var chaseTime: Float = 0f
        private set
    private var isChasing = false

    fun startChase() {
        if (!isChasing) {
            isChasing = true
            chaseTime = 0f
        }
    }

    fun stopChase() {
        isChasing = false
    }

    fun updateChaseTimer(dt: Float) {
        if (isChasing) {
            chaseTime += dt
        }
    }

    fun resetChase() {
        chaseTime = 0f
        isChasing = false
    }

    // Score tracking
    var sessionScore: Int = 0
        private set

    fun addScore(points: Int) {
        sessionScore += points
    }

    fun resetSessionScore() {
        sessionScore = 0
    }
}
