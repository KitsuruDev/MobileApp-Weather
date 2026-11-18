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
        return try {
            val prefs = applicationContext.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            val city = prefs.getString("current_city", "Москва") ?: "Москва"
            val language = prefs.getString("language", "ru") ?: "ru"
            val unit = prefs.getString("temp_unit", "C") ?: "C"

            val severeEnabled = prefs.getBoolean("severe_weather_notifications_enabled", false)
            val changeEnabled = prefs.getBoolean("weather_change_notifications_enabled", false)
            if (!severeEnabled && !changeEnabled) return Result.success()

            // контекст с локалью для перевода строк уведомления
            val config = Configuration(applicationContext.resources.configuration).apply {
                setLocale(Locale(language))
            }
            val localizedContext = applicationContext.createConfigurationContext(config)

            val response = RetrofitClient.api.getWeather(query = city, lang = language)
            val current = response.current
            val realCity = response.location.name

            // --- ПЛОХИЕ УСЛОВИЯ ---
            if (severeEnabled) {
                val alert = detectSevere(current, localizedContext)
                if (alert != null) {
                    NotificationHelper.showSevereWeather(
                        localizedContext, realCity, alert, current.condition.text
                    )
                }
            }

            // --- ИЗМЕНЕНИЕ ПОГОДЫ ---
            if (changeEnabled) {
                val lastTemp = prefs.getFloat("last_weather_temp", Float.NaN)
                val lastConditionCode = prefs.getInt("last_weather_condition", -1)

                if (!lastTemp.isNaN()) {
                    val deltaTemp = abs(current.temperature - lastTemp)
                    val conditionChanged = lastConditionCode != -1 && lastConditionCode != current.condition.code

                    // значимое изменение: температура на 5+ градусов или сменился тип погоды
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

                // обновляем сохранённый снимок
                prefs.edit {
                    putFloat("last_weather_temp", current.temperature.toFloat())
                    putInt("last_weather_condition", current.condition.code)
                }
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeatherAlertWorker", "Error: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    // возвращает локализованное описание «плохих условий» или null, если всё нормально
    private fun detectSevere(current: CurrentWeather, context: Context): String? {
        return when {
            current.windSpeedKmh >= 5.0 ->
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