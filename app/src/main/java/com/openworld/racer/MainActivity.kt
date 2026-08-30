package com.openworld.racer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.openworld.racer.databinding.ActivityMainBinding
import com.openworld.racer.ui.DriveActivity
import com.openworld.racer.ui.GarageActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartDrive.setOnClickListener {
            val intent = Intent(this, DriveActivity::class.java)
            startActivity(intent)
        }

        binding.btnOpenGarage.setOnClickListener {
            val intent = Intent(this, GarageActivity::class.java)
            startActivity(intent)
        }
    }
}
