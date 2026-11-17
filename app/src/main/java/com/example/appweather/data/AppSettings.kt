package com.example.appweather.data

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val tempUnit: String = "C",
    val windUnit: String = "kmh",
    val pressureUnit: String = "hPa",
    val language: String = "ru",
    val timeFormat: String = "24h"
) {
    // вызывает у созданного load-ом объекта
    fun save(context: Context) {
        val prefs = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        prefs.edit {
            putString("temp_unit", tempUnit)
            putString("wind_unit", windUnit)
            putString("pressure_unit", pressureUnit)
            putString("language", language)
            putString("time_format", timeFormat)
        }
    }

    // статическая функция (для вызова не надо объект класса создавать)
    companion object {
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            return AppSettings(
                tempUnit = prefs.getString("temp_unit", "C") ?: "C",
                windUnit = prefs.getString("wind_unit", "kmh") ?: "kmh",
                pressureUnit = prefs.getString("pressure_unit", "hPa") ?: "hPa",
                language = prefs.getString("language", "ru") ?: "ru",
                timeFormat = prefs.getString("time_format", "24h") ?: "24h"
            )
        }
    }
}