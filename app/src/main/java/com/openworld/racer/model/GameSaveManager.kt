package com.openworld.racer.model

import android.content.Context
import com.google.gson.Gson

class GameSaveManager(context: Context) {

    private val prefs = context.getSharedPreferences("OPEN_WORLD_RACER_SAVE", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveVehicleConfig(config: VehicleConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString("CURRENT_VEHICLE_CONFIG", json).apply()
    }

    fun loadVehicleConfig(): VehicleConfig {
        val json = prefs.getString("CURRENT_VEHICLE_CONFIG", null)
        return if (json != null) {
            try {
                gson.fromJson(json, VehicleConfig::class.java)
            } catch (e: Exception) {
                VehicleConfig().apply { applyEasyPreset("City Commuter EV") }
            }
        } else {
            VehicleConfig().apply { applyEasyPreset("City Commuter EV") }
        }
    }
}
