package com.example.appweather.data

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val tempUnit: String = "C",
    val windUnit: String = "kmh",
    val pressureUnit: String = "hPa",
    val language: String = "ru",
    val timeFormat: String = "24h",
    val notificationsEnabled: Boolean = false,
    val notificationTime: String = "08:00",
    val weatherChangeNotificationsEnabled: Boolean = false,
    val severeWeatherNotificationsEnabled: Boolean = false,
    val movementNotificationsEnabled: Boolean = false,
    val refreshIntervalMinutes: Int = 30,
    val gpsHighAccuracy: Boolean = true
) {
    fun save(context: Context) {
        val prefs = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        prefs.edit {
            putString("temp_unit", tempUnit)
            putString("wind_unit", windUnit)
            putString("pressure_unit", pressureUnit)
            putString("language", language)
            putString("time_format", timeFormat)
            putBoolean("notifications_enabled", notificationsEnabled)
            putString("notification_time", notificationTime)
            putBoolean("weather_change_notifications_enabled", weatherChangeNotificationsEnabled)
            putBoolean("severe_weather_notifications_enabled", severeWeatherNotificationsEnabled)
            putBoolean("movement_notifications_enabled", movementNotificationsEnabled)
            putInt("refresh_interval", refreshIntervalMinutes)
            putBoolean("gps_high_accuracy", gpsHighAccuracy)
        }
    }

    companion object {
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)

            // если 0 или меньше - принудительно 30 минут
            val rawRefresh = prefs.getInt("refresh_interval", 30)
            val safeRefresh = if (rawRefresh <= 0) 30 else rawRefresh

            return AppSettings(
                tempUnit = prefs.getString("temp_unit", "C") ?: "C",
                windUnit = prefs.getString("wind_unit", "kmh") ?: "kmh",
                pressureUnit = prefs.getString("pressure_unit", "hPa") ?: "hPa",
                language = prefs.getString("language", "ru") ?: "ru",
                timeFormat = prefs.getString("time_format", "24h") ?: "24h",
                notificationsEnabled = prefs.getBoolean("notifications_enabled", false),
                notificationTime = prefs.getString("notification_time", "08:00") ?: "08:00",
                weatherChangeNotificationsEnabled = prefs.getBoolean("weather_change_notifications_enabled", false),
                severeWeatherNotificationsEnabled = prefs.getBoolean("severe_weather_notifications_enabled", false),
                movementNotificationsEnabled = prefs.getBoolean("movement_notifications_enabled", false),
                refreshIntervalMinutes = safeRefresh,
                gpsHighAccuracy = prefs.getBoolean("gps_high_accuracy", true)
            )
        }
    }
}