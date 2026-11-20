package com.example.appweather.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME_DAILY = "daily_forecast_work"
    private const val WORK_NAME_ALERTS = "weather_alerts_work"

    // инициализация воркера ежедневного прогноза
    fun schedule(context: Context, time: String) {
        val (hour, minute) = parseTime(time)
        val now = Calendar.getInstance()             // текущее время
        val target = Calendar.getInstance().apply {  // время публикации уведомления в XX ч XX мин 0 с 0 мс
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // время публикации меньше текущего - это следующий день
        if (target.timeInMillis <= now.timeInMillis)
            target.add(Calendar.DAY_OF_MONTH, 1)

        // сколько миллисекунд ждать до запуска
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        // установка воркера с этой задержкой в очередь
        val request = OneTimeWorkRequestBuilder<DailyForecastWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        // KEEP - если задача уже запланирована, новая не создастся
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_DAILY, ExistingWorkPolicy.KEEP, request
        )
    }

    // отмена воркера
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY)
    }

    // пересборка воркера с новыми параметрами
    fun reschedule(context: Context, enabled: Boolean, time: String) {
        cancel(context)
        if (enabled)
            schedule(context, time)
    }

    // изменение погоды + плохие условия
    fun scheduleAlerts(context: Context) {
        // воркер запускается каждые ~30 минут, пока его не отменят
        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(30, TimeUnit.MINUTES).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_ALERTS, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancelAlerts(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ALERTS)
    }

    fun rescheduleAlerts(context: Context, enabled: Boolean) {
        if (enabled)
            scheduleAlerts(context)
        else
            cancelAlerts(context)
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        return try {
            val parts = time.split(":")
            parts[0].toInt() to parts[1].toInt() // to - пара (Pair)
        } catch (e: Exception) {
            8 to 0
        }
    }
}