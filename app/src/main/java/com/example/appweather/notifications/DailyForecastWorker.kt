package com.example.appweather.notifications

import android.content.Context
import android.content.res.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appweather.network.RetrofitClient
import java.util.Locale

class DailyForecastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            val city = NotificationHelper.resolveNotificationCity(applicationContext)
            val language = prefs.getString("language", "ru") ?: "ru"
            val notificationTime = prefs.getString("notification_time", "08:00") ?: "08:00"

            android.util.Log.d("DailyForecastWorker", "Worker started. City: $city, lang: $language")

            // контекст с нужной локалью для правильного перевода
            val config = Configuration(applicationContext.resources.configuration).apply { setLocale(Locale(language)) }
            val localizedContext = applicationContext.createConfigurationContext(config)

            val response = RetrofitClient.api.getWeather(query = city, lang = language)

            android.util.Log.d("DailyForecastWorker", "Weather loaded. Attempting to show notification.")

            NotificationHelper.showDailyForecast(localizedContext, response)

            // планировка следующей задачи на завтра
            NotificationScheduler.schedule(applicationContext, notificationTime)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyForecastWorker", "Error: ${e.message}", e)

            if (runAttemptCount < 3)
                Result.retry()
            else
                Result.failure()
        }
    }
}