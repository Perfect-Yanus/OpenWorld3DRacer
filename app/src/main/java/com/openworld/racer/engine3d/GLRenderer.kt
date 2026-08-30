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
            // High-End Commercial EV Supercar Body
            // 1. Lower Monocoque Chassis
            val bodyM = carM.clone()
            Matrix.scaleM(bodyM, 0, 2.25f, 0.40f, 4.6f)
            drawCube(bodyM, carConfig.bodyColor)

            // 2. Muscular Flared Overfenders (Wide-Body Wheel Arches)
            val fenderPositions = arrayOf(
                Pair(-1.18f, -1.35f), Pair(1.18f, -1.35f),
                Pair(-1.18f, 1.35f), Pair(1.18f, 1.35f)
            )
            for ((fx, fz) in fenderPositions) {
                val fM = carM.clone()
                Matrix.translateM(fM, 0, fx, 0.05f, fz)
                Matrix.scaleM(fM, 0, 0.35f, 0.42f, 1.15f)
                drawCube(fM, carConfig.bodyColor)
            }

            // 3. Front Nose Hood Slope (Wedge) & Air Vents
            val hoodM = carM.clone()
            Matrix.translateM(hoodM, 0, 0f, 0.15f, -1.7f)
            Matrix.scaleM(hoodM, 0, 2.15f, 0.38f, 1.3f)
            drawWedge(hoodM, carConfig.bodyColor)

            // 4. Slanted Glass Windshield (Wedge)
            val windshieldM = carM.clone()
            Matrix.translateM(windshieldM, 0, 0f, 0.52f, -0.65f)
            Matrix.scaleM(windshieldM, 0, 1.90f, 0.58f, 1.1f)
            drawWedge(windshieldM, 0xFF0B132B.toInt()) // Tinted Dark Blue Glass

            // 5. Panoramic Roof Cockpit & Carbon Pillars
            val roofM = carM.clone()
            Matrix.translateM(roofM, 0, 0f, 0.68f, 0.2f)
            Matrix.scaleM(roofM, 0, 1.85f, 0.52f, 1.6f)
            drawCube(roofM, 0xFF070A10.toInt()) // Gloss Black Canopy

            // 6. Fastback Rear Sloped Glass Window
            val rearSlopeM = carM.clone()
            Matrix.translateM(rearSlopeM, 0, 0f, 0.52f, 1.55f)
            Matrix.rotateM(rearSlopeM, 0, 180f, 0f, 1f, 0f)
            Matrix.scaleM(rearSlopeM, 0, 1.85f, 0.52f, 1.1f)
            drawWedge(rearSlopeM, carConfig.bodyColor)

            // 7. Aerodynamic Side Winglet Mirrors
            val mirrorLeft = carM.clone()
            Matrix.translateM(mirrorLeft, 0, -1.25f, 0.55f, -0.6f)
            Matrix.scaleM(mirrorLeft, 0, 0.25f, 0.12f, 0.18f)
            drawCube(mirrorLeft, 0xFF1E293B.toInt())

            val mirrorRight = carM.clone()
            Matrix.translateM(mirrorRight, 0, 1.25f, 0.55f, -0.6f)
            Matrix.scaleM(mirrorRight, 0, 0.25f, 0.12f, 0.18f)
            drawCube(mirrorRight, 0xFF1E293B.toInt())

            // 8. Front Bumper Carbon Splitter & Air Intakes
            val splitterM = carM.clone()
            Matrix.translateM(splitterM, 0, 0f, -0.18f, -2.32f)
            Matrix.scaleM(splitterM, 0, 2.30f, 0.10f, 0.30f)
            drawCube(splitterM, 0xFF0F141C.toInt())

            // 9. Active Rear Wing / Spoiler
            val wingStanchionL = carM.clone()
            Matrix.translateM(wingStanchionL, 0, -0.7f, 0.65f, 2.1f)
            Matrix.scaleM(wingStanchionL, 0, 0.08f, 0.35f, 0.15f)
            drawCube(wingStanchionL, 0xFF0B0F19.toInt())

            val wingStanchionR = carM.clone()
            Matrix.translateM(wingStanchionR, 0, 0.7f, 0.65f, 2.1f)
            Matrix.scaleM(wingStanchionR, 0, 0.08f, 0.35f, 0.15f)
            drawCube(wingStanchionR, 0xFF0B0F19.toInt())

            val wingBlade = carM.clone()
            Matrix.translateM(wingBlade, 0, 0f, 0.82f, 2.1f)
            Matrix.scaleM(wingBlade, 0, 2.20f, 0.08f, 0.40f)
            drawCube(wingBlade, 0xFF0B0F19.toInt())
        }

        // Underfloor Battery Pack
        val battM = carM.clone()
        val battZOffset = carConfig.batteryPositionZ
        Matrix.translateM(battM, 0, 0f, -0.28f, battZOffset)
        Matrix.scaleM(battM, 0, 2.05f, 0.20f, 2.7f)
        drawCube(battM, 0xFF00E676.toInt()) // Green Energy Pack

        // Electric Motors
        if (carConfig.motorLayout == "FWD" || carConfig.motorLayout == "AWD") {
            val frontMotorM = carM.clone()
            Matrix.translateM(frontMotorM, 0, 0f, -0.1f, -1.4f)
            Matrix.scaleM(frontMotorM, 0, 0.90f, 0.40f, 0.80f)
            drawCube(frontMotorM, 0xFFFFEA00.toInt())
        }
        if (carConfig.motorLayout == "RWD" || carConfig.motorLayout == "AWD") {
            val rearMotorM = carM.clone()
            Matrix.translateM(rearMotorM, 0, 0f, -0.1f, 1.4f)
            Matrix.scaleM(rearMotorM, 0, 0.90f, 0.40f, 0.80f)
            drawCube(rearMotorM, 0xFFFFEA00.toInt())
        }

        // Laser Matrix Headlights (Cyan LED) & Continuous Rear Lightbar (Neon Red)
        val hlM1 = carM.clone()
        Matrix.translateM(hlM1, 0, -0.80f, 0.14f, -2.28f)
        Matrix.scaleM(hlM1, 0, 0.45f, 0.10f, 0.12f)
        drawCube(hlM1, 0xFF00F0FF.toInt())

        val hlM2 = carM.clone()
        Matrix.translateM(hlM2, 0, 0.80f, 0.14f, -2.28f)
        Matrix.scaleM(hlM2, 0, 0.45f, 0.10f, 0.12f)
        drawCube(hlM2, 0xFF00F0FF.toInt())

        val tlM = carM.clone()
        Matrix.translateM(tlM, 0, 0f, 0.18f, 2.30f)
        Matrix.scaleM(tlM, 0, 2.10f, 0.10f, 0.08f)
        drawCube(tlM, 0xFFFF0055.toInt())

        // 4 Round 16-Sided 3D Cylindrical Wheels, Rims & Brembo Calipers
        val wheelPositions = arrayOf(
            Pair(-1.22f, -1.35f), Pair(1.22f, -1.35f),
            Pair(-1.22f, 1.35f), Pair(1.22f, 1.35f)
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
            Matrix.scaleM(tireM, 0, wheelDiameterScale, 0.44f, wheelDiameterScale)
            drawCylinder(tireM, 0xFF121214.toInt()) // Dark Rubber

            // Inner Metallic Alloy Rim (3D Cylinder)
            val rimM = wheelM.clone()
            Matrix.scaleM(rimM, 0, wheelDiameterScale * 0.74f, 0.46f, wheelDiameterScale * 0.74f)
            drawCylinder(rimM, carConfig.rimColor)

            // Steel Brake Disc
            val discM = wheelM.clone()
            Matrix.scaleM(discM, 0, wheelDiameterScale * 0.55f, 0.25f, wheelDiameterScale * 0.55f)
            drawCylinder(discM, 0xFF64748B.toInt())

            // High-Performance Red Brembo Brake Caliper Accent
            val caliperM = wheelM.clone()
            Matrix.translateM(caliperM, 0, 0.20f, 0.0f, 0.0f)
            Matrix.scaleM(caliperM, 0, wheelDiameterScale * 0.35f, 0.32f, wheelDiameterScale * 0.35f)
            drawCylinder(caliperM, 0xFFEF4444.toInt())
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
