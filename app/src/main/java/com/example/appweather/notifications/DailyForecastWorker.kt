package com.example.appweather.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appweather.network.RetrofitClient

class DailyForecastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            // текущий город сохраняется в prefs при каждой успешной загрузке в WeatherViewModel
            val city = prefs.getString("current_city", "Москва") ?: "Москва"
            val language = prefs.getString("language", "ru") ?: "ru"

            val response = RetrofitClient.api.getWeather(query = city, lang = language)
            NotificationHelper.showDailyForecast(applicationContext, response)
            Result.success()
        } catch (e: Exception) {
            // одна повторная попытка через стандартный backoff WorkManager (30 сек)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}