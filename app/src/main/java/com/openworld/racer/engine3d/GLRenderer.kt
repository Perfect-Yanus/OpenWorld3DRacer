package com.openworld.racer.engine3d

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.openworld.racer.audio.SoundManager
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.physics.RaycastVehicle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(
    private val context: Context,
    val vehicle: RaycastVehicle,
    private val soundManager: SoundManager? = null,
    private val listener: RenderListener? = null
) : GLSurfaceView.Renderer {

    interface RenderListener {
        fun onFrameUpdate(speedKmh: Float, powerKw: Float, socPercent: Float, surface: String, isDrifting: Boolean)
    }

    val camera = Camera3D()
    private val objects = WorldMapGenerator.generateOpenWorldObjects()

    private var programId = 0
    private var aPositionHandle = -1
    private var uMVPMatrixHandle = -1
    private var uColorHandle = -1

    private var lastTimeNs = System.nanoTime()

    private lateinit var cubeVertexBuffer: FloatBuffer
    private lateinit var cubeIndexBuffer: ShortBuffer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0.53f, 0.81f, 0.98f, 1.0f) // Sky Blue
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)

            initShaders()
            initCubeMeshData()

            vehicle.resetPosition(0f, 0.2f, 0f, 0f)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            GLES20.glViewport(0, 0, width, height)
            camera.updateProjection(width, height)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            val now = System.nanoTime()
            val dt = ((now - lastTimeNs) / 1_000_000_000.0f).coerceIn(0.005f, 0.05f)
            lastTimeNs = now

            // 1. Update Vehicle Physics
            vehicle.update(dt, objects)

            // 2. Update Sound Engine
            soundManager?.let {
                it.currentSpeedKmh = vehicle.speedKmh
                it.currentPowerKw = vehicle.motorPowerKwCurrent
                it.isDrifting = vehicle.isDrifting
                it.isInWater = vehicle.isInWater
            }

            // 3. Update Camera
            camera.updateCamera(vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.headingAngle, dt)

            // 4. Clear Canvas
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            if (programId != 0) {
                GLES20.glUseProgram(programId)
            }

            // 5. Render World Objects
            renderEnvironmentObjects()

            // 6. Render 3D EV Car
            renderElectricVehicle()

            // 7. Notify HUD UI
            listener?.onFrameUpdate(
                vehicle.speedKmh,
                vehicle.motorPowerKwCurrent,
                vehicle.batterySocPercent,
                vehicle.currentSurface,
                vehicle.isDrifting
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun renderEnvironmentObjects() {
        for (obj in objects) {
            val modelM = FloatArray(16)
            Matrix.setIdentityM(modelM, 0)
            Matrix.translateM(modelM, 0, obj.posX, obj.posY, obj.posZ)
            if (obj.rotationY != 0f) {
                Matrix.rotateM(modelM, 0, obj.rotationY, 0f, 1f, 0f)
            }
            Matrix.scaleM(modelM, 0, obj.sizeX, obj.sizeY, obj.sizeZ)

            drawCube(modelM, obj.color)
        }
    }

    private fun renderElectricVehicle() {
        val carConfig = vehicle.config

        // Base Car Model Matrix
        val carM = FloatArray(16)
        Matrix.setIdentityM(carM, 0)
        Matrix.translateM(carM, 0, vehicle.posX, vehicle.posY + 0.45f, vehicle.posZ)
        Matrix.rotateM(carM, 0, vehicle.headingAngle, 0f, 1f, 0f)
        Matrix.rotateM(carM, 0, vehicle.pitchAngle, 1f, 0f, 0f)
        Matrix.rotateM(carM, 0, vehicle.rollAngle, 0f, 0f, 1f)

        val customProf = carConfig.getSafeCustomProfile()

        if (carConfig.isCustomDrawnBody && customProf.isNotEmpty()) {
            // RENDER USER CUSTOM DRAWN 3D CAR BODY!
            val numSegments = customProf.size
            val carLength = 4.4f
            val segLength = carLength / numSegments
            val startZ = -carLength / 2f

            for (i in 0 until numSegments) {
                val h = customProf[i].coerceIn(0.15f, 1.0f) * 1.4f
                val sliceZ = startZ + i * segLength + segLength / 2f

                val segM = carM.clone()
                Matrix.translateM(segM, 0, 0f, h / 2f - 0.2f, sliceZ)
                Matrix.scaleM(segM, 0, 2.0f, h, segLength * 1.05f)

                val col = if (i in 4..9 && h > 0.8f) 0xFF151522.toInt() else carConfig.bodyColor
                drawCube(segM, col)
            }
        } else {
            // Render Standard Aerodynamic 3D Body
            val bodyM = carM.clone()
            Matrix.scaleM(bodyM, 0, 2.1f, 0.85f, 4.4f)
            drawCube(bodyM, carConfig.bodyColor)

            val roofM = carM.clone()
            Matrix.translateM(roofM, 0, 0f, 0.65f, -0.2f)
            Matrix.scaleM(roofM, 0, 1.8f, 0.65f, 2.2f)
            drawCube(roofM, 0xFF151522.toInt())
        }

        // Underfloor Battery Pack
        val battM = carM.clone()
        val battZOffset = carConfig.batteryPositionZ
        Matrix.translateM(battM, 0, 0f, -0.32f, battZOffset)
        Matrix.scaleM(battM, 0, 1.9f, 0.25f, 2.6f)
        drawCube(battM, 0xFF00E676.toInt()) // Green Battery Housing

        // Electric Motors
        if (carConfig.motorLayout == "FWD" || carConfig.motorLayout == "AWD") {
            val frontMotorM = carM.clone()
            Matrix.translateM(frontMotorM, 0, 0f, -0.1f, -1.4f)
            Matrix.scaleM(frontMotorM, 0, 0.9f, 0.45f, 0.8f)
            drawCube(frontMotorM, 0xFFFFEA00.toInt())
        }
        if (carConfig.motorLayout == "RWD" || carConfig.motorLayout == "AWD") {
            val rearMotorM = carM.clone()
            Matrix.translateM(rearMotorM, 0, 0f, -0.1f, 1.4f)
            Matrix.scaleM(rearMotorM, 0, 0.9f, 0.45f, 0.8f)
            drawCube(rearMotorM, 0xFFFFEA00.toInt())
        }

        // Headlights & Taillights
        val hlM1 = carM.clone()
        Matrix.translateM(hlM1, 0, -0.8f, 0.1f, -2.15f)
        Matrix.scaleM(hlM1, 0, 0.35f, 0.2f, 0.1f)
        drawCube(hlM1, 0xFFFFFFFF.toInt())

        val hlM2 = carM.clone()
        Matrix.translateM(hlM2, 0, 0.8f, 0.1f, -2.15f)
        Matrix.scaleM(hlM2, 0, 0.35f, 0.2f, 0.1f)
        drawCube(hlM2, 0xFFFFFFFF.toInt())

        val tlM = carM.clone()
        Matrix.translateM(tlM, 0, 0f, 0.15f, 2.18f)
        Matrix.scaleM(tlM, 0, 1.9f, 0.15f, 0.08f)
        drawCube(tlM, 0xFFFF1744.toInt())

        // 4 Wheels & Tires
        val wheelPositions = arrayOf(
            Pair(-1.15f, -1.35f), Pair(1.15f, -1.35f),
            Pair(-1.15f, 1.35f), Pair(1.15f, 1.35f)
        )

        val rimScale = carConfig.wheelDiameterInches * 0.045f

        for (i in 0..3) {
            val (wx, wz) = wheelPositions[i]
            val wheelM = carM.clone()
            val suspY = -0.2f - vehicle.wheelSuspension[i] * 0.1f
            Matrix.translateM(wheelM, 0, wx, suspY, wz)

            if (i < 2) {
                Matrix.rotateM(wheelM, 0, vehicle.steeringInput * 30f, 0f, 1f, 0f)
            }
            Matrix.rotateM(wheelM, 0, vehicle.wheelRotationDeg[i], 1f, 0f, 0f)
            Matrix.scaleM(wheelM, 0, 0.42f, rimScale, rimScale)

            drawCube(wheelM, 0xFF111111.toInt())

            val rimM = wheelM.clone()
            Matrix.scaleM(rimM, 0, 0.8f, 0.65f, 0.65f)
            drawCube(rimM, carConfig.rimColor)
        }
    }

    private fun drawCube(modelMatrix: FloatArray, colorHex: Int) {
        if (programId == 0 || uMVPMatrixHandle < 0 || uColorHandle < 0) return

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, camera.viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvp, 0, camera.projectionMatrix, 0, mvp, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvp, 0)

        val r = Color.red(colorHex) / 255.0f
        val g = Color.green(colorHex) / 255.0f
        val b = Color.blue(colorHex) / 255.0f
        val a = Color.alpha(colorHex) / 255.0f
        GLES20.glUniform4f(uColorHandle, r, g, b, a)

        val posHandle = if (aPositionHandle >= 0) aPositionHandle else 0
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, cubeVertexBuffer)
        GLES20.glEnableVertexAttribArray(posHandle)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, cubeIndexBuffer)
    }

    private fun initShaders() {
        val vertexShaderCode = """
            attribute vec4 aPosition;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES20.glCreateProgram().also {
            GLES20.glBindAttribLocation(it, 0, "aPosition")
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uColorHandle = GLES20.glGetUniformLocation(programId, "uColor")
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }

    private fun initCubeMeshData() {
        val vertices = floatArrayOf(
            -0.5f, -0.5f,  0.5f,
             0.5f, -0.5f,  0.5f,
             0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f
        )

        val indices = shortArrayOf(
            0, 1, 2,  0, 2, 3,
            1, 5, 6,  1, 6, 2,
            5, 4, 7,  5, 7, 6,
            4, 0, 3,  4, 3, 7,
            3, 2, 6,  3, 6, 7,
            4, 5, 1,  4, 1, 0
        )

        cubeVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        cubeVertexBuffer.position(0)

        cubeIndexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
        cubeIndexBuffer.position(0)
    }
}
