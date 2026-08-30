package com.openworld.racer.engine3d

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.openworld.racer.audio.SoundManager
import com.openworld.racer.model.EnvironmentObject
import com.openworld.racer.model.ObjectType
import com.openworld.racer.physics.RaycastVehicle
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
    private var aNormalHandle = -1
    private var uMVPMatrixHandle = -1
    private var uModelMatrixHandle = -1
    private var uColorHandle = -1
    private var uLightDirHandle = -1

    private var lastTimeNs = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0.48f, 0.74f, 0.95f, 1.0f) // Vibrant Sky Blue
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)

            initShaders()
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

            // 5. Render World Infrastructure & Buildings
            renderEnvironmentObjects()

            // 6. Render Aerodynamic 3D EV Sports Car
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

            when (obj.type) {
                ObjectType.TREE -> {
                    // Trunk (3D Wood Cylinder)
                    val trunkM = modelM.clone()
                    Matrix.scaleM(trunkM, 0, 0.7f, obj.sizeY * 0.4f, 0.7f)
                    drawCylinder(trunkM, 0xFF4E342E.toInt())

                    // Lower Foliage Cone
                    val foliage1M = modelM.clone()
                    Matrix.translateM(foliage1M, 0, 0f, obj.sizeY * 0.25f, 0f)
                    Matrix.scaleM(foliage1M, 0, obj.sizeX, obj.sizeY * 0.6f, obj.sizeZ)
                    drawCone(foliage1M, obj.color)

                    // Upper Foliage Cone
                    val foliage2M = modelM.clone()
                    Matrix.translateM(foliage2M, 0, 0f, obj.sizeY * 0.55f, 0f)
                    Matrix.scaleM(foliage2M, 0, obj.sizeX * 0.7f, obj.sizeY * 0.5f, obj.sizeZ * 0.7f)
                    drawCone(foliage2M, 0xFF388E3C.toInt())
                }

                ObjectType.LAMPPOST -> {
                    // 3D Steel Light Pole Cylinder
                    val poleM = modelM.clone()
                    Matrix.scaleM(poleM, 0, 0.35f, obj.sizeY, 0.35f)
                    drawCylinder(poleM, obj.color)

                    // Lamp Light Fixture Head
                    val lampHeadM = modelM.clone()
                    Matrix.translateM(lampHeadM, 0, 0.8f, obj.sizeY / 2f, 0f)
                    Matrix.scaleM(lampHeadM, 0, 1.4f, 0.35f, 0.6f)
                    drawCube(lampHeadM, 0xFFFFF9C4.toInt()) // Glowing Yellow LED
                }

                ObjectType.BUILDING -> {
                    Matrix.scaleM(modelM, 0, obj.sizeX, obj.sizeY, obj.sizeZ)
                    drawCube(modelM, obj.color)

                    // Window Grid Highlight Line
                    val windowM = modelM.clone()
                    Matrix.scaleM(windowM, 0, 1.01f, 0.08f, 1.01f)
                    drawCube(windowM, 0xFF80DEEA.toInt())
                }

                else -> {
                    Matrix.scaleM(modelM, 0, obj.sizeX, obj.sizeY, obj.sizeZ)
                    drawCube(modelM, obj.color)
                }
            }
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
            // User Custom Drawn Silhouette 3D Body
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

                val col = if (i in 4..9 && h > 0.8f) 0xFF0D1B2A.toInt() else carConfig.bodyColor
                drawCube(segM, col)
            }
        } else {
            // Sleek Aerodynamic 3D EV Sports Car Body
            // 1. Lower Body Main Chassis
            val bodyM = carM.clone()
            Matrix.scaleM(bodyM, 0, 2.1f, 0.45f, 4.4f)
            drawCube(bodyM, carConfig.bodyColor)

            // 2. Front Nose Hood Slope (Wedge)
            val hoodM = carM.clone()
            Matrix.translateM(hoodM, 0, 0f, 0.15f, -1.6f)
            Matrix.scaleM(hoodM, 0, 2.05f, 0.40f, 1.2f)
            drawWedge(hoodM, carConfig.bodyColor)

            // 3. Slanted Glass Windshield (Wedge)
            val windshieldM = carM.clone()
            Matrix.translateM(windshieldM, 0, 0f, 0.50f, -0.6f)
            Matrix.scaleM(windshieldM, 0, 1.85f, 0.55f, 1.0f)
            drawWedge(windshieldM, 0xFF0A192F.toInt()) // Tinted Dark Blue Glass

            // 4. Roof Cabin & Pillars
            val roofM = carM.clone()
            Matrix.translateM(roofM, 0, 0f, 0.65f, 0.2f)
            Matrix.scaleM(roofM, 0, 1.80f, 0.50f, 1.5f)
            drawCube(roofM, 0xFF111625.toInt()) // Gloss Black Roof

            // 5. Rear Hatch Slope (Wedge rotated 180 deg)
            val rearSlopeM = carM.clone()
            Matrix.translateM(rearSlopeM, 0, 0f, 0.50f, 1.45f)
            Matrix.rotateM(rearSlopeM, 0, 180f, 0f, 1f, 0f)
            Matrix.scaleM(rearSlopeM, 0, 1.80f, 0.50f, 1.0f)
            drawWedge(rearSlopeM, carConfig.bodyColor)

            // 6. Front Bumper Lip Splitter
            val splitterM = carM.clone()
            Matrix.translateM(splitterM, 0, 0f, -0.18f, -2.22f)
            Matrix.scaleM(splitterM, 0, 2.15f, 0.12f, 0.25f)
            drawCube(splitterM, 0xFF090A0F.toInt())
        }

        // Underfloor Battery Pack
        val battM = carM.clone()
        val battZOffset = carConfig.batteryPositionZ
        Matrix.translateM(battM, 0, 0f, -0.28f, battZOffset)
        Matrix.scaleM(battM, 0, 1.95f, 0.20f, 2.6f)
        drawCube(battM, 0xFF00E676.toInt()) // Green Energy Pack

        // Electric Motors
        if (carConfig.motorLayout == "FWD" || carConfig.motorLayout == "AWD") {
            val frontMotorM = carM.clone()
            Matrix.translateM(frontMotorM, 0, 0f, -0.1f, -1.4f)
            Matrix.scaleM(frontMotorM, 0, 0.85f, 0.40f, 0.75f)
            drawCube(frontMotorM, 0xFFFFEA00.toInt())
        }
        if (carConfig.motorLayout == "RWD" || carConfig.motorLayout == "AWD") {
            val rearMotorM = carM.clone()
            Matrix.translateM(rearMotorM, 0, 0f, -0.1f, 1.4f)
            Matrix.scaleM(rearMotorM, 0, 0.85f, 0.40f, 0.75f)
            drawCube(rearMotorM, 0xFFFFEA00.toInt())
        }

        // Headlights (Cyan LED Strips) & Rear Lightbar (Glowing Red)
        val hlM1 = carM.clone()
        Matrix.translateM(hlM1, 0, -0.75f, 0.12f, -2.18f)
        Matrix.scaleM(hlM1, 0, 0.40f, 0.12f, 0.10f)
        drawCube(hlM1, 0xFF00E5FF.toInt())

        val hlM2 = carM.clone()
        Matrix.translateM(hlM2, 0, 0.75f, 0.12f, -2.18f)
        Matrix.scaleM(hlM2, 0, 0.40f, 0.12f, 0.10f)
        drawCube(hlM2, 0xFF00E5FF.toInt())

        val tlM = carM.clone()
        Matrix.translateM(tlM, 0, 0f, 0.15f, 2.20f)
        Matrix.scaleM(tlM, 0, 1.95f, 0.12f, 0.08f)
        drawCube(tlM, 0xFFFF1744.toInt())

        // 4 Round 16-Sided 3D Cylindrical Wheels & Alloy Rims
        val wheelPositions = arrayOf(
            Pair(-1.15f, -1.35f), Pair(1.15f, -1.35f),
            Pair(-1.15f, 1.35f), Pair(1.15f, 1.35f)
        )

        val wheelDiameterScale = carConfig.wheelDiameterInches * 0.045f

        for (i in 0..3) {
            val (wx, wz) = wheelPositions[i]
            val wheelM = carM.clone()
            val suspY = -0.2f - vehicle.wheelSuspension[i] * 0.1f
            Matrix.translateM(wheelM, 0, wx, suspY, wz)

            if (i < 2) {
                Matrix.rotateM(wheelM, 0, vehicle.steeringInput * 30f, 0f, 1f, 0f)
            }
            Matrix.rotateM(wheelM, 0, vehicle.wheelRotationDeg[i], 1f, 0f, 0f)
            // Rotate Cylinder so its axis aligns with X (left-right wheel axis)
            Matrix.rotateM(wheelM, 0, 90f, 0f, 0f, 1f)

            // Outer Rubber Tire (3D Cylinder)
            val tireM = wheelM.clone()
            Matrix.scaleM(tireM, 0, wheelDiameterScale, 0.42f, wheelDiameterScale)
            drawCylinder(tireM, 0xFF181818.toInt()) // Dark Rubber

            // Inner Alloy Rim (3D Metallic Cylinder)
            val rimM = wheelM.clone()
            Matrix.scaleM(rimM, 0, wheelDiameterScale * 0.72f, 0.45f, wheelDiameterScale * 0.72f)
            drawCylinder(rimM, carConfig.rimColor)

            // Brake Disc Accent (Red Caliper)
            val brakeM = wheelM.clone()
            Matrix.scaleM(brakeM, 0, wheelDiameterScale * 0.50f, 0.30f, wheelDiameterScale * 0.50f)
            drawCylinder(brakeM, 0xFFFF3333.toInt())
        }
    }

    private fun drawCube(modelMatrix: FloatArray, colorHex: Int) {
        drawMesh(MeshFactory.cubeMesh, modelMatrix, colorHex)
    }

    private fun drawCylinder(modelMatrix: FloatArray, colorHex: Int) {
        drawMesh(MeshFactory.cylinderMesh, modelMatrix, colorHex)
    }

    private fun drawWedge(modelMatrix: FloatArray, colorHex: Int) {
        drawMesh(MeshFactory.wedgeMesh, modelMatrix, colorHex)
    }

    private fun drawCone(modelMatrix: FloatArray, colorHex: Int) {
        drawMesh(MeshFactory.coneMesh, modelMatrix, colorHex)
    }

    private fun drawMesh(mesh: MeshFactory.MeshData, modelMatrix: FloatArray, colorHex: Int) {
        if (programId == 0 || uMVPMatrixHandle < 0 || uColorHandle < 0) return

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, camera.viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvp, 0, camera.projectionMatrix, 0, mvp, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uLightDirHandle, 0.4f, 0.85f, 0.35f) // Sun light direction

        val r = Color.red(colorHex) / 255.0f
        val g = Color.green(colorHex) / 255.0f
        val b = Color.blue(colorHex) / 255.0f
        val a = Color.alpha(colorHex) / 255.0f
        GLES20.glUniform4f(uColorHandle, r, g, b, a)

        val posHandle = if (aPositionHandle >= 0) aPositionHandle else 0
        val normalHandle = if (aNormalHandle >= 0) aNormalHandle else 1

        val stride = 6 * 4 // 6 floats per vertex (X,Y,Z, Nx,Ny,Nz)

        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(posHandle)

        mesh.vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(normalHandle)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
    }

    private fun initShaders() {
        val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform vec3 uLightDir;
            varying float vLightFactor;

            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vec3 normal = normalize(mat3(uModelMatrix) * aNormal);
                float diffuse = max(dot(normal, uLightDir), 0.0);
                vLightFactor = 0.40 + 0.60 * diffuse;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 uColor;
            varying float vLightFactor;

            void main() {
                gl_FragColor = vec4(uColor.rgb * vLightFactor, uColor.a);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES20.glCreateProgram().also {
            GLES20.glBindAttribLocation(it, 0, "aPosition")
            GLES20.glBindAttribLocation(it, 1, "aNormal")
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(programId, "aNormal")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(programId, "uColor")
        uLightDirHandle = GLES20.glGetUniformLocation(programId, "uLightDir")
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }
}
