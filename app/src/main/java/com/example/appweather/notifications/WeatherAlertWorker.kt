package com.example.appweather.notifications

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appweather.data.CurrentWeather
import com.example.appweather.network.RetrofitClient
import java.util.Locale
import kotlin.math.abs

class WeatherAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        android.util.Log.d("WeatherAlertWorker", "doWork() STARTED")
        return try {
            val prefs = applicationContext.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            val language = prefs.getString("language", "ru") ?: "ru"
            val unit = prefs.getString("temp_unit", "C") ?: "C"

            val severeEnabled = prefs.getBoolean("severe_weather_notifications_enabled", false)
            val changeEnabled = prefs.getBoolean("weather_change_notifications_enabled", false)
            if (!severeEnabled && !changeEnabled) {
                android.util.Log.d("WeatherAlertWorker", "Both flags disabled, exiting.")
                return Result.success()
            }

            // определяем город с учётом GPS
            val cityBefore = NotificationHelper.resolveNotificationCity(applicationContext)
            android.util.Log.d("WeatherAlertWorker", "Resolved city: $cityBefore")

            val config = Configuration(applicationContext.resources.configuration).apply {
                setLocale(Locale(language))
            }
            val localizedContext = applicationContext.createConfigurationContext(config)

            val response = RetrofitClient.api.getWeather(query = cityBefore, lang = language)
            val current = response.current
            val realCity = response.location.name

            // если город сменился, пока шёл запрос — не показываем уведомление
            val cityAfter = NotificationHelper.resolveNotificationCity(applicationContext)
            if (cityAfter.isNotBlank() && cityAfter != cityBefore) {
                android.util.Log.d(
                    "WeatherAlertWorker",
                    "City changed during fetch ($cityBefore → $cityAfter), skipping"
                )
                return Result.success()
            }

            // --- ПЛОХИЕ УСЛОВИЯ (с дедупликацией) ---
            if (severeEnabled) {
                val alert = detectSevere(current, localizedContext)
                val lastAlert = prefs.getString("last_severe_alert", null)
                val lastAlertTime = prefs.getLong("last_severe_alert_time", 0L)
                val hoursSinceLast = (System.currentTimeMillis() - lastAlertTime) / 3_600_000L

                android.util.Log.d(
                    "WeatherAlertWorker",
                    "Alert=$alert, lastAlert=$lastAlert, hoursSinceLast=$hoursSinceLast"
                )

                if (alert != null && (alert != lastAlert || hoursSinceLast >= 6)) {
                    NotificationHelper.showSevereWeather(
                        localizedContext, realCity, alert, current.condition.text
                    )
                    prefs.edit {
                        putString("last_severe_alert", alert)
                        putLong("last_severe_alert_time", System.currentTimeMillis())
                    }
                } else if (alert == null) {
                    prefs.edit { remove("last_severe_alert") }
                }
            }

            // --- ИЗМЕНЕНИЕ ПОГОДЫ (только если снимок от того же города) ---
            if (changeEnabled) {
                val lastCity = prefs.getString("last_weather_city", null)
                val lastTemp = prefs.getFloat("last_weather_temp", Float.NaN)
                val lastConditionCode = prefs.getInt("last_weather_condition", -1)

                android.util.Log.d(
                    "WeatherAlertWorker",
                    "Change check: lastCity=$lastCity, currentCity=$realCity"
                )

                if (lastCity == realCity && !lastTemp.isNaN()) {
                    val deltaTemp = abs(current.temperature - lastTemp)
                    val conditionChanged =
                        lastConditionCode != -1 && lastConditionCode != current.condition.code

                    if (deltaTemp >= 5.0 || conditionChanged) {
                        NotificationHelper.showWeatherChange(
                            localizedContext,
                            realCity,
                            lastTemp.toDouble(),
                            current.temperature,
                            current.condition.text,
                            unit
                        )
                    }
                }
            }

            // обновляем снимок — следующий запуск будет сравнивать с текущим состоянием
            prefs.edit {
                putString("last_weather_city", realCity)
                putFloat("last_weather_temp", current.temperature.toFloat())
                putInt("last_weather_condition", current.condition.code)
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeatherAlertWorker", "Error: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun detectSevere(current: CurrentWeather, context: Context): String? {
        return when {
            current.windSpeedKmh >= 40.0 ->
                context.getString(com.example.appweather.R.string.severe_wind)

            current.condition.code in setOf(1087, 1273, 1276, 1279, 1282) ->
                context.getString(com.example.appweather.R.string.severe_thunderstorm)

            current.condition.code in setOf(1114, 1117, 1222, 1225, 1255, 1258) ->
                context.getString(com.example.appweather.R.string.severe_snow)

            current.condition.code in setOf(1192, 1195, 1243, 1246) ->
                context.getString(com.example.appweather.R.string.severe_rain)

            current.uv >= 8.0 ->
                context.getString(com.example.appweather.R.string.severe_uv)

            current.temperature >= 38.0 ->
                context.getString(com.example.appweather.R.string.severe_heat)

            current.temperature <= -25.0 ->
                context.getString(com.example.appweather.R.string.severe_cold)

            else -> null
        }
    }
}