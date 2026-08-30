package com.openworld.racer.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.openworld.racer.audio.SoundManager
import com.openworld.racer.databinding.ActivityDriveBinding
import com.openworld.racer.engine3d.CameraMode
import com.openworld.racer.engine3d.GLRenderer
import com.openworld.racer.model.GameSaveManager
import com.openworld.racer.model.VehicleConfig
import com.openworld.racer.physics.RaycastVehicle
import java.util.Locale

class DriveActivity : AppCompatActivity(), GLRenderer.RenderListener {

    private lateinit var binding: ActivityDriveBinding
    private lateinit var saveManager: GameSaveManager
    private lateinit var currentConfig: VehicleConfig
    private val soundManager = SoundManager()

    private lateinit var vehicle: RaycastVehicle
    private lateinit var renderer: GLRenderer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saveManager = GameSaveManager(this)
        currentConfig = saveManager.loadVehicleConfig()

        vehicle = RaycastVehicle(currentConfig)
        renderer = GLRenderer(this, vehicle, soundManager = soundManager, listener = this)

        binding.glSurfaceDrive.setEGLContextClientVersion(2)
        binding.glSurfaceDrive.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.glSurfaceDrive.setRenderer(renderer)
        binding.glSurfaceDrive.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        setupTouchControls()

        // Camera Switch Button
        binding.btnCamSwitch.setOnClickListener {
            renderer.camera.mode = when (renderer.camera.mode) {
                CameraMode.CHASE_CAM -> {
                    binding.btnCamSwitch.text = "📷 COCKPIT"
                    CameraMode.COCKPIT_CAM
                }
                CameraMode.COCKPIT_CAM -> {
                    binding.btnCamSwitch.text = "📷 ORBIT"
                    CameraMode.FREE_ORBIT
                }
                CameraMode.FREE_ORBIT -> {
                    binding.btnCamSwitch.text = "📷 CHASE"
                    CameraMode.CHASE_CAM
                }
            }
        }

        // Reset Car Button
        binding.btnResetCar.setOnClickListener {
            vehicle.resetPosition(0f, 0.2f, 0f, 0f)
        }

        // Return to Garage
        binding.btnReturnGarage.setOnClickListener {
            val intent = Intent(this, GarageActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchControls() {
        binding.btnSteerLeft.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.steeringInput = -1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.steeringInput = 0.0f
            }
            true
        }

        binding.btnSteerRight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.steeringInput = 1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.steeringInput = 0.0f
            }
            true
        }

        binding.btnGas.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.throttleInput = 1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.throttleInput = 0.0f
            }
            true
        }

        binding.btnBrake.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.brakeInput = 1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.brakeInput = 0.0f
            }
            true
        }

        binding.btnHandbrake.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.handbrakeInput = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.handbrakeInput = false
            }
            true
        }
    }

    override fun onFrameUpdate(
        speedKmh: Float,
        powerKw: Float,
        socPercent: Float,
        surface: String,
        isDrifting: Boolean
    ) {
        runOnUiThread {
            binding.tvSpeedometer.text = String.format(Locale.getDefault(), "%.0f km/h", speedKmh)
            binding.tvPowerKw.text = String.format(Locale.getDefault(), "%.0f kW", powerKw)
            binding.tvBatterySoc.text = String.format(Locale.getDefault(), "🔋 %.0f%%", socPercent)

            binding.tvSurfaceType.text = when (surface) {
                "SIDEWALK" -> "🏙️ SIDEWALK"
                "DIRT" -> "🪵 DIRT / OFFROAD"
                "WATER" -> "🌊 WATER / RIVER"
                else -> "🛣️ ASPHALT"
            }

            binding.tvDriftIndicator.visibility = if (isDrifting) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceDrive.onResume()
        soundManager.startAudio()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceDrive.onPause()
        soundManager.stopAudio()
    }
}
