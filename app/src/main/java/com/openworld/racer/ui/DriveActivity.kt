package com.openworld.racer.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
        // Progressive Smooth Steering
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

        // Gas Accelerator
        binding.btnGas.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.throttleInput = 1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.throttleInput = 0.0f
            }
            true
        }

        // Brake / Reverse
        binding.btnBrake.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.brakeInput = 1.0f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.brakeInput = 0.0f
            }
            true
        }

        // Drift Powerslide
        binding.btnHandbrake.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.handbrakeInput = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.handbrakeInput = false
            }
            true
        }

        // NITRO Turbo Boost
        binding.btnNitro.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> vehicle.nitroButtonInput = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vehicle.nitroButtonInput = false
            }
            true
        }
    }

    override fun onFrameUpdate(
        speedKmh: Float,
        powerKw: Float,
        socPercent: Float,
        surface: String,
        isDrifting: Boolean,
        nitroGauge: Float,
        isNitroActive: Boolean,
        totalScore: Int,
        comboMultiplier: Int,
        isAirborne: Boolean,
        airTimeSeconds: Float,
        stuntFeedbackText: String
    ) {
        runOnUiThread {
            // Speedometer with dynamic high-speed glow
            binding.tvSpeedometer.text = String.format(Locale.getDefault(), "%.0f km/h", speedKmh)
            binding.tvSpeedometer.setTextColor(
                when {
                    isNitroActive -> Color.parseColor("#FF007F") // Neon Magenta in Nitro
                    speedKmh > 120f -> Color.parseColor("#FFD600") // Neon Amber
                    else -> Color.parseColor("#00E5FF") // Cyan
                }
            )

            // Score & Combo
            binding.tvScore.text = String.format(Locale.getDefault(), "SCORE: %,d", totalScore)
            binding.tvCombo.text = if (comboMultiplier > 1) {
                String.format(Locale.getDefault(), "🔥 COMBO x%d!", comboMultiplier)
            } else {
                "COMBO: x1"
            }
            binding.tvCombo.setTextColor(if (comboMultiplier > 1) Color.parseColor("#FF007F") else Color.parseColor("#94A3B8"))

            // Nitro Bar
            binding.pbNitro.progress = nitroGauge.toInt()
            binding.tvNitroLabel.text = if (isNitroActive) {
                "🔥 NITRO BURST!"
            } else {
                String.format(Locale.getDefault(), "⚡ NITRO %.0f%%", nitroGauge)
            }
            binding.tvNitroLabel.setTextColor(if (isNitroActive) Color.parseColor("#FF007F") else Color.parseColor("#00FF88"))

            // Nitro Button Readiness Glow
            binding.btnNitro.alpha = if (nitroGauge >= 20.0f || isNitroActive) 1.0f else 0.45f

            // Battery %
            binding.tvBatterySoc.text = String.format(Locale.getDefault(), "🔋 %.0f%%", socPercent)

            // Surface Indicator
            binding.tvSurfaceType.text = when (surface) {
                "RAMP" -> "🚀 STUNT RAMP"
                "BOOST" -> "⚡ BOOST PAD"
                "SIDEWALK" -> "🏙️ SIDEWALK"
                "DIRT" -> "🪵 DIRT / OFFROAD"
                "WATER" -> "🌊 WATER / RIVER"
                else -> "🛣️ ASPHALT"
            }

            // Stunt / Airtime Banner Popup
            if (stuntFeedbackText.isNotEmpty()) {
                binding.tvStuntBanner.text = stuntFeedbackText
                binding.tvStuntBanner.visibility = View.VISIBLE
            } else {
                binding.tvStuntBanner.visibility = View.GONE
            }
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
