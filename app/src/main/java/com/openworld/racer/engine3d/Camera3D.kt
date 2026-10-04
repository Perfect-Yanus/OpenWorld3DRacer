package com.openworld.racer.engine3d

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

enum class CameraMode {
    CHASE_CAM,
    COCKPIT_CAM,
    FREE_ORBIT
}

class Camera3D {

    var mode = CameraMode.CHASE_CAM

    // Camera Position
    var eyeX = 0f
    var eyeY = 5f
    var eyeZ = 12f

    // Camera Target
    var targetX = 0f
    var targetY = 1f
    var targetZ = 0f

    // Orbit Angles for Garage / Orbit Cam
    var orbitAngleY = 45f
    var orbitPitch = 20f
    var orbitDistance = 7.5f

    // Dynamic FOV for speed & stunts
    var currentFov = 60.0f
    private var viewWidth = 1
    private var viewHeight = 1

    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val mvpMatrix = FloatArray(16)

    fun updateProjection(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, currentFov, aspect, 0.2f, 1500f)
    }

    fun updateCamera(
        carX: Float,
        carY: Float,
        carZ: Float,
        carHeadingDeg: Float,
        dt: Float,
        isAirborne: Boolean = false,
        isNitro: Boolean = false,
        airTime: Float = 0f
    ) {
        // Dynamic FOV interpolation
        val targetFov = if (isNitro) 74.0f else (if (isAirborne) 66.0f else 60.0f)
        currentFov += (targetFov - currentFov) * (dt * 6.0f).coerceAtMost(1.0f)
        val aspect = viewWidth.toFloat() / viewHeight.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, currentFov, aspect, 0.2f, 1500f)

        when (mode) {
            CameraMode.CHASE_CAM -> {
                val rad = Math.toRadians(carHeadingDeg.toDouble()).toFloat()

                // When airborne or in nitro, dynamically adjust camera distance & height
                val chaseDist = if (isAirborne) (10.5f + (airTime * 1.5f).coerceAtMost(3.5f)) else (if (isNitro) 9.8f else 8.5f)
                val chaseHeight = if (isAirborne) 4.6f else 3.4f

                val idealEyeX = carX - sin(rad) * chaseDist
                val idealEyeY = carY + chaseHeight
                val idealEyeZ = carZ + cos(rad) * chaseDist

                // Smooth camera follow interpolation (Lerp)
                val alpha = (dt * 8.0f).coerceIn(0.06f, 0.45f)
                eyeX += (idealEyeX - eyeX) * alpha
                eyeY += (idealEyeY - eyeY) * alpha
                eyeZ += (idealEyeZ - eyeZ) * alpha

                targetX = carX
                targetY = carY + 1.2f
                targetZ = carZ
            }
            CameraMode.COCKPIT_CAM -> {
                val rad = Math.toRadians(carHeadingDeg.toDouble()).toFloat()
                eyeX = carX + sin(rad) * 0.4f
                eyeY = carY + 1.3f
                eyeZ = carZ - cos(rad) * 0.4f

                targetX = carX + sin(rad) * 20f
                targetY = carY + 1.0f
                targetZ = carZ - cos(rad) * 20f
            }
            CameraMode.FREE_ORBIT -> {
                val radY = Math.toRadians(orbitAngleY.toDouble()).toFloat()
                val radP = Math.toRadians(orbitPitch.toDouble()).toFloat()

                eyeX = carX + (orbitDistance * cos(radP) * sin(radY)).toFloat()
                eyeY = carY + (orbitDistance * sin(radP)).toFloat()
                eyeZ = carZ + (orbitDistance * cos(radP) * cos(radY)).toFloat()

                targetX = carX
                targetY = carY + 0.8f
                targetZ = carZ
            }
        }

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            targetX, targetY, targetZ,
            0f, 1f, 0f
        )
    }
}
