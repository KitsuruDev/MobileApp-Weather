package com.example.appweather.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val WORK_NAME = "daily_forecast_work"

    // планирует ежедневный запуск в HH:mm
    fun schedule(context: Context, time: String) {
        val (hour, minute) = parseTime(time)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // если сегодня это время уже прошло — стартуем завтра
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailyForecastWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        // UPDATE: если задача уже есть — пересоздаётся с новым временем
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    // снимает запланированную задачу
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    // пересборка плана: отменить и заново поставить, если включено
    fun reschedule(context: Context, enabled: Boolean, time: String) {
        cancel(context)
        if (enabled) schedule(context, time)
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