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

    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val mvpMatrix = FloatArray(16)

    fun updateProjection(width: Int, height: Int) {
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 60f, aspect, 0.2f, 1500f)
    }

    fun updateCamera(carX: Float, carY: Float, carZ: Float, carHeadingDeg: Float, dt: Float) {
        when (mode) {
            CameraMode.CHASE_CAM -> {
                val rad = Math.toRadians(carHeadingDeg.toDouble()).toFloat()
                val idealEyeX = carX - sin(rad) * 9.0f
                val idealEyeY = carY + 3.8f
                val idealEyeZ = carZ + cos(rad) * 9.0f

                // Smooth camera follow interpolation (Lerp)
                val alpha = (dt * 7.0f).coerceIn(0.05f, 0.40f)
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
