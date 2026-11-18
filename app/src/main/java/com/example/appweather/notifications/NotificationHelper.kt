package com.example.appweather.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.appweather.MainActivity
import com.example.appweather.R
import com.example.appweather.data.WeatherResponse
import com.example.appweather.data.WeatherUtils
import kotlin.math.roundToInt

object NotificationHelper {

    const val CHANNEL_ID = "daily_forecast_channel"
    const val NOTIFICATION_ID = 1001

    // создаётся при старте приложения, повторный вызов безопасен
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    // показывает ежедневный прогноз по уже загруженным данным
    fun showDailyForecast(context: Context, weather: WeatherResponse) {
        // на Android 13+ без разрешения показывать нельзя
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val current = weather.current
        val today = weather.forecast.forecastday.firstOrNull()

        // форматируем температуру по настройкам пользователя
        val unit = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            .getString("temp_unit", "C") ?: "C"
        val tempNow = WeatherUtils.formatTemp(current.temperature, unit)
        val tempMin = today?.day?.tempMin?.let { WeatherUtils.formatTemp(it, unit) } ?: "--"
        val tempMax = today?.day?.tempMax?.let { WeatherUtils.formatTemp(it, unit) } ?: "--"

        val title = "${weather.location.name}: $tempNow"
        val text = "${current.condition.text} · $tempMin / $tempMax · ${context.getString(R.string.notification_rain_chance)} ${today?.day?.chanceOfRain ?: 0}%"

        // по клику открываем приложение
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            android.util.Log.d("NotificationHelper", "Showing notification for ${weather.location.name}")
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // разрешение могли отозвать между проверкой и вызовом — молча игнорируем
        }
    }

    // утилита: склонение «градус» не нужно — используем формат из WeatherUtils
    @Suppress("unused")
    fun tempWithUnit(celsius: Double, unit: String): String =
        if (unit == "F") "${((celsius * 9 / 5) + 32).roundToInt()}°F"
        else "${celsius.roundToInt()}°C"
}