package com.openworld.racer.ui

import android.content.Intent
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openworld.racer.databinding.ActivityGarageBinding
import com.openworld.racer.engine3d.CameraMode
import com.openworld.racer.engine3d.GLRenderer
import com.openworld.racer.model.GameSaveManager
import com.openworld.racer.model.VehicleConfig
import com.openworld.racer.physics.RaycastVehicle
import java.util.Locale

class GarageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGarageBinding
    private lateinit var saveManager: GameSaveManager
    private lateinit var currentConfig: VehicleConfig

    private lateinit var vehicle: RaycastVehicle
    private lateinit var renderer: GLRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGarageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saveManager = GameSaveManager(this)
        currentConfig = saveManager.loadVehicleConfig()

        vehicle = RaycastVehicle(currentConfig)
        renderer = GLRenderer(this, vehicle)
        renderer.camera.mode = CameraMode.FREE_ORBIT

        binding.glSurfaceGarage.setEGLContextClientVersion(2)
        binding.glSurfaceGarage.setRenderer(renderer)
        binding.glSurfaceGarage.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        setupModeToggle()
        setupEasyPresetButtons()
        setupExpertSliders()
        updateStatsUI()

        binding.btnDrawCustomBody.setOnClickListener {
            val intent = Intent(this, CustomCarDrawActivity::class.java)
            startActivity(intent)
        }

        binding.btnDriveNow.setOnClickListener {
            saveManager.saveVehicleConfig(currentConfig)
            val intent = Intent(this, DriveActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupModeToggle() {
        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbEasyMode.id) {
                currentConfig.isEasyMode = true
                binding.layoutEasyMode.visibility = View.VISIBLE
                binding.layoutExpertMode.visibility = View.GONE
            } else {
                currentConfig.isEasyMode = false
                binding.layoutEasyMode.visibility = View.GONE
                binding.layoutExpertMode.visibility = View.VISIBLE
            }
            updateStatsUI()
        }
    }

    private fun setupEasyPresetButtons() {
        binding.btnPresetCity.setOnClickListener {
            currentConfig.applyEasyPreset("City Commuter EV")
            updateStatsUI()
            Toast.makeText(this, "City Commuter EV Preset Applied", Toast.LENGTH_SHORT).show()
        }
        binding.btnPresetSport.setOnClickListener {
            currentConfig.applyEasyPreset("Sport Dual Motor EV")
            updateStatsUI()
            Toast.makeText(this, "Sport Dual Motor EV Preset Applied", Toast.LENGTH_SHORT).show()
        }
        binding.btnPresetOffroad.setOnClickListener {
            currentConfig.applyEasyPreset("Off-Road Beast 4WD")
            updateStatsUI()
            Toast.makeText(this, "Off-Road Beast 4WD Preset Applied", Toast.LENGTH_SHORT).show()
        }
        binding.btnPresetLongRange.setOnClickListener {
            currentConfig.applyEasyPreset("Long-Range Touring EV")
            updateStatsUI()
            Toast.makeText(this, "Long-Range Touring EV Preset Applied", Toast.LENGTH_SHORT).show()
        }
        binding.btnPresetHypercar.setOnClickListener {
            currentConfig.applyEasyPreset("Hypercar Quad EV")
            updateStatsUI()
            Toast.makeText(this, "Hypercar Quad EV Preset Applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupExpertSliders() {
        binding.rgMotorLayout.setOnCheckedChangeListener { _, checkedId ->
            currentConfig.motorLayout = when (checkedId) {
                binding.rbFWD.id -> "FWD"
                binding.rbRWD.id -> "RWD"
                else -> "AWD"
            }
            currentConfig.motorCount = if (currentConfig.motorLayout == "AWD") 2 else 1
            updateStatsUI()
        }

        binding.sbPower.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentConfig.maxPowerKw = 100f + progress.toFloat()
                currentConfig.maxTorqueNm = currentConfig.maxPowerKw * 1.8f
                binding.tvLabelPower.text = "Max Motor Power: ${currentConfig.maxPowerKw.toInt()} kW"
                updateStatsUI()
            }
        })

        binding.sbBatteryCapacity.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentConfig.batteryCapacityKwh = 40f + progress.toFloat()
                currentConfig.batteryWeightKg = 300f + (progress * 6.0f)
                binding.tvLabelBatteryCapacity.text =
                    "Capacity: ${currentConfig.batteryCapacityKwh.toInt()} kWh (Weight: ${currentConfig.batteryWeightKg.toInt()} kg)"
                updateStatsUI()
            }
        })

        binding.sbBatteryPos.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentConfig.batteryPositionZ = (progress - 50) / 62.5f
                val posStr = when {
                    currentConfig.batteryPositionZ < -0.2f -> "Rear Bias"
                    currentConfig.batteryPositionZ > 0.2f -> "Front Bias"
                    else -> "50:50 Balanced Center"
                }
                binding.tvLabelBatteryPos.text = "Battery Position (CoG Balance): $posStr"
                updateStatsUI()
            }
        })

        binding.sbTirePsi.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentConfig.tirePressurePsi = 20f + progress.toFloat()
                binding.tvLabelTirePsi.text = "Tire Pressure: ${currentConfig.tirePressurePsi.toInt()} PSI"
                updateStatsUI()
            }
        })

        binding.sbTireWidth.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentConfig.tireWidthMm = 195f + progress.toFloat()
                binding.tvLabelTireWidth.text = "Tire Width: ${currentConfig.tireWidthMm.toInt()} mm"
                updateStatsUI()
            }
        })
    }

    private fun updateStatsUI() {
        vehicle.config = currentConfig

        val accelSec = currentConfig.getZeroToHundredSec()
        val topSpeedKmh = currentConfig.getTopSpeedKmh()
        val rangeKm = currentConfig.getEstimatedRangeKm()
        val massKg = currentConfig.getTotalMassKg()

        binding.tvStatAccel.text = String.format(Locale.getDefault(), "0-100: %.1fs", accelSec)
        binding.tvStatSpeed.text = String.format(Locale.getDefault(), "Top: %.0f km/h", topSpeedKmh)
        binding.tvStatRange.text = String.format(Locale.getDefault(), "Range: %.0f km", rangeKm)
        binding.tvStatMass.text = String.format(Locale.getDefault(), "Mass: %,d kg", massKg.toInt())
    }

    abstract class SimpleSeekListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceGarage.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceGarage.onPause()
    }
}
