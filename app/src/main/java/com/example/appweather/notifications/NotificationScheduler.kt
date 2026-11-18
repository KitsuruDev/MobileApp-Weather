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

    // ===== ЕЖЕДНЕВНЫЙ ПРОГНОЗ =====

    fun schedule(context: Context, time: String) {
        val (hour, minute) = parseTime(time)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_MONTH, 1)
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<DailyForecastWorker>()
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_DAILY, ExistingWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY)
    }

    fun reschedule(context: Context, enabled: Boolean, time: String) {
        cancel(context)
        if (enabled) schedule(context, time)
    }

    // ===== УМНЫЕ УВЕДОМЛЕНИЯ (изменение погоды + плохие условия) =====

    fun scheduleAlerts(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(4, TimeUnit.HOURS)
            .build()

        // UPDATE позволяет при повторном вызове просто продлить существующее расписание
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_ALERTS, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancelAlerts(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ALERTS)
    }

    fun rescheduleAlerts(context: Context, enabled: Boolean) {
        if (enabled) scheduleAlerts(context) else cancelAlerts(context)
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        return try {
            val parts = time.split(":")
            parts[0].toInt() to parts[1].toInt()
        } catch (e: Exception) {
            8 to 0
        }
    }
}