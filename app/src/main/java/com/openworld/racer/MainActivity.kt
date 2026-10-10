package com.openworld.racer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.openworld.racer.databinding.ActivityMainBinding
import com.openworld.racer.model.GameMode
import com.openworld.racer.model.GameModeManager
import com.openworld.racer.ui.DriveActivity
import com.openworld.racer.ui.GarageActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartDrive.setOnClickListener {
            GameModeManager.setMode(GameMode.FREE_RIDE)
            val intent = Intent(this, DriveActivity::class.java)
            startActivity(intent)
        }

        binding.btnPoliceChase.setOnClickListener {
            GameModeManager.setMode(GameMode.POLICE_CHASE)
            val intent = Intent(this, DriveActivity::class.java)
            startActivity(intent)
        }

        binding.btnTimeAttack.setOnClickListener {
            GameModeManager.setMode(GameMode.TIME_ATTACK)
            val intent = Intent(this, DriveActivity::class.java)
            startActivity(intent)
        }

        binding.btnOpenGarage.setOnClickListener {
            val intent = Intent(this, GarageActivity::class.java)
            startActivity(intent)
        }
    }
}
