package com.openworld.racer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openworld.racer.databinding.ActivityCustomCarDrawBinding
import com.openworld.racer.model.GameSaveManager

class CustomCarDrawActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomCarDrawBinding
    private lateinit var saveManager: GameSaveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomCarDrawBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saveManager = GameSaveManager(this)
        val config = saveManager.loadVehicleConfig()

        // Load existing drawn points safely
        if (config.isCustomDrawnBody) {
            val safeProfile = config.getSafeCustomProfile()
            for (i in 0 until binding.carDrawCanvas.numPoints) {
                if (i < safeProfile.size) {
                    binding.carDrawCanvas.profilePoints[i] = safeProfile[i]
                }
            }
            binding.carDrawCanvas.invalidate()
        }

        binding.btnPresetSupercar.setOnClickListener {
            binding.carDrawCanvas.applyPreset("SUPERCAR")
        }
        binding.btnPresetCyber.setOnClickListener {
            binding.carDrawCanvas.applyPreset("CYBERTRUCK")
        }
        binding.btnPresetSuv.setOnClickListener {
            binding.carDrawCanvas.applyPreset("SUV")
        }
        binding.btnPresetFormula.setOnClickListener {
            binding.carDrawCanvas.applyPreset("FORMULA")
        }

        binding.btnSaveCustomCar.setOnClickListener {
            config.isCustomDrawnBody = true
            config.customProfile = binding.carDrawCanvas.profilePoints.clone()
            saveManager.saveVehicleConfig(config)

            Toast.makeText(this, "Custom 3D Car Body Applied!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, GarageActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
