package com.openworld.racer.engine3d

import android.content.Context
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ObjectType
import com.openworld.racer.model.VehicleConfig
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
    private val listener: RenderListener? = null
) : GLSurfaceView.Renderer {

    interface RenderListener {
        fun onFrameUpdate(speedKmh: Float, powerKw: Float, socPercent: Float, surface: String, isDrifting: Boolean)
    }

    val camera = Camera3D()
    private val objects = WorldMapGenerator.generateOpenWorldObjects()

    private var programId = 0
    private var uMVPMatrixHandle = 0
    private var uColorHandle = 0
    private var uLightDirHandle = 0

    private var lastTimeNs = System.nanoTime()

    // 3D Cube Mesh Buffers
    private lateinit var cubeVertexBuffer: FloatBuffer
    private lateinit var cubeIndexBuffer: ShortBuffer

    // 3D Cylinder Mesh Buffers (for Wheels/Tires & Trees)
    private lateinit var cylinderVertexBuffer: FloatBuffer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.53f, 0.81f, 0.98f, 1.0f) // Sky Blue
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        initShaders()
        initCubeMeshData()
        initCylinderMeshData()

        vehicle.resetPosition(0f, 0.2f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.updateProjection(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastTimeNs) / 1_000_000_000.0f).coerceIn(0.005f, 0.05f)
        lastTimeNs = now

        // 1. Update Vehicle Physics
        vehicle.update(dt, objects)

        // 2. Update Camera
        camera.updateCamera(vehicle.posX, vehicle.posY, vehicle.posZ, vehicle.headingAngle, dt)

        // 3. Clear Canvas
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(programId)

        // Directional Sun Light
        GLES30.glUniform3f(uLightDirHandle, 0.5f, 0.9f, 0.4f)

        // 4. Render World Objects
        renderEnvironmentObjects()

        // 5. Render 3D EV Car & Components
        renderElectricVehicle()

        // 6. Notify HUD UI
        listener?.onFrameUpdate(
            vehicle.speedKmh,
            vehicle.motorPowerKwCurrent,
            vehicle.batterySocPercent,
            vehicle.currentSurface,
            vehicle.isDrifting
        )
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

        // 1. Car Aerodynamic Main Body
        val bodyM = carM.clone()
        Matrix.scaleM(bodyM, 0, 2.1f, 0.85f, 4.4f)
        drawCube(bodyM, carConfig.bodyColor)

        // 2. Cabin Roof Glass Window
        val roofM = carM.clone()
        Matrix.translateM(roofM, 0, 0f, 0.65f, -0.2f)
        Matrix.scaleM(roofM, 0, 1.8f, 0.65f, 2.2f)
        drawCube(roofM, 0xFF151522.toInt())

        // 3. Underfloor Battery Pack (Visible Battery Block underneath)
        val battM = carM.clone()
        val battZOffset = carConfig.batteryPositionZ
        Matrix.translateM(battM, 0, 0f, -0.32f, battZOffset)
        Matrix.scaleM(battM, 0, 1.9f, 0.25f, 2.6f)
        drawCube(battM, 0xFF00E676.toInt()) // Green Battery Housing

        // 4. Electric Motors (Front & Rear Motors)
        if (carConfig.motorLayout == "FWD" || carConfig.motorLayout == "AWD") {
            val frontMotorM = carM.clone()
            Matrix.translateM(frontMotorM, 0, 0f, -0.1f, -1.4f)
            Matrix.scaleM(frontMotorM, 0, 0.9f, 0.45f, 0.8f)
            drawCube(frontMotorM, 0xFFFFEA00.toInt()) // Yellow Motor
        }
        if (carConfig.motorLayout == "RWD" || carConfig.motorLayout == "AWD") {
            val rearMotorM = carM.clone()
            Matrix.translateM(rearMotorM, 0, 0f, -0.1f, 1.4f)
            Matrix.scaleM(rearMotorM, 0, 0.9f, 0.45f, 0.8f)
            drawCube(rearMotorM, 0xFFFFEA00.toInt()) // Yellow Motor
        }

        // 5. Headlights & Taillights
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

        // 6. 4 Wheels & Tires
        val wheelPositions = arrayOf(
            Pair(-1.15f, -1.35f), // Front Left
            Pair(1.15f, -1.35f),  // Front Right
            Pair(-1.15f, 1.35f),  // Rear Left
            Pair(1.15f, 1.35f)    // Rear Right
        )

        val rimScale = carConfig.wheelDiameterInches * 0.045f

        for (i in 0..3) {
            val (wx, wz) = wheelPositions[i]
            val wheelM = carM.clone()
            val suspY = -0.2f - vehicle.wheelSuspension[i] * 0.1f
            Matrix.translateM(wheelM, 0, wx, suspY, wz)

            if (i < 2) {
                // Front steering angle
                Matrix.rotateM(wheelM, 0, vehicle.steeringInput * 30f, 0f, 1f, 0f)
            }
            Matrix.rotateM(wheelM, 0, vehicle.wheelRotationDeg[i], 1f, 0f, 0f)
            Matrix.scaleM(wheelM, 0, 0.42f, rimScale, rimScale)

            // Draw Rubber Tire (Black)
            drawCube(wheelM, 0xFF111111.toInt())

            // Draw Inner Metallic Rim
            val rimM = wheelM.clone()
            Matrix.scaleM(rimM, 0, 0.8f, 0.65f, 0.65f)
            drawCube(rimM, carConfig.rimColor)
        }
    }

    private fun drawCube(modelMatrix: FloatArray, colorHex: Int) {
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, camera.viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvp, 0, camera.projectionMatrix, 0, mvp, 0)

        GLES30.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvp, 0)

        val r = Color.red(colorHex) / 255.0f
        val g = Color.green(colorHex) / 255.0f
        val b = Color.blue(colorHex) / 255.0f
        val a = Color.alpha(colorHex) / 255.0f
        GLES30.glUniform4f(uColorHandle, r, g, b, a)

        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, cubeVertexBuffer)
        GLES30.glEnableVertexAttribArray(0)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 36, GLES30.GL_UNSIGNED_SHORT, cubeIndexBuffer)
    }

    private fun initShaders() {
        val vertexShaderCode = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fragmentShaderCode = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }

        uMVPMatrixHandle = GLES30.glGetUniformLocation(programId, "uMVPMatrix")
        uColorHandle = GLES30.glGetUniformLocation(programId, "uColor")
        uLightDirHandle = GLES30.glGetUniformLocation(programId, "uLightDir")
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, code)
            GLES30.glCompileShader(shader)
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
            0, 1, 2,  0, 2, 3, // Front
            1, 5, 6,  1, 6, 2, // Right
            5, 4, 7,  5, 7, 6, // Back
            4, 0, 3,  4, 3, 7, // Left
            3, 2, 6,  3, 6, 7, // Top
            4, 5, 1,  4, 1, 0  // Bottom
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

    private fun initCylinderMeshData() {
        val cylVertices = FloatArray(36 * 3)
        cylinderVertexBuffer = ByteBuffer.allocateDirect(cylVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cylVertices)
        cylinderVertexBuffer.position(0)
    }
}
