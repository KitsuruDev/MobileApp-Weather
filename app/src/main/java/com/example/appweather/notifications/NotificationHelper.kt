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

object NotificationHelper {

    // каналы
    const val CHANNEL_ID_DAILY = "daily_forecast_channel"
    const val CHANNEL_ID_ALERTS = "weather_alerts_channel"

    // идентификаторы, чтобы уведомления не перезаписывали друг друга (до 999 - служебные уведомления)
    const val NOTIFICATION_ID_DAILY = 1001
    const val NOTIFICATION_ID_CHANGE = 1002
    const val NOTIFICATION_ID_SEVERE = 1003
    const val NOTIFICATION_ID_MOVEMENT = 1004

    // ключ для передачи города при клике на уведомление
    const val EXTRA_CITY = "extra_city"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager?.createNotificationChannel(
            // IMPORTANCE_DEFAULT - показывается со звуком, но не всплывает поверх экрана
            NotificationChannel(CHANNEL_ID_DAILY, context.getString(R.string.notification_channel_name),NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
            }
        )

        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_ALERTS, context.getString(R.string.alerts_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.alerts_channel_description)
            }
        )
    }

    // определение города для уведомлений
    // (учитывается GPS-разрешение: выдано - используется GPS, иначе цепочка "последний открытый -> первый избранный -> Москва")
    fun resolveNotificationCity(context: Context): String {
        val prefs = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)

        // запрос разрешения и сверка его с константой для проверки статуса
        val hasGps = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasGps) {
            prefs.getString("last_gps_city", null) ?:
                prefs.getString("current_city", "Москва") ?: "Москва"
        } else {
            val lastOpened = prefs.getString("last_weather_city", null)

            if (!lastOpened.isNullOrBlank()) {
                lastOpened
            } else {
                val favorites = prefs.getString("favorite_cities", "") ?: ""
                favorites.split(",").firstOrNull { it.isNotBlank() } ?: "Москва"
            }
        }
    }

    // ежедневный прогноз
    fun showDailyForecast(context: Context, weather: WeatherResponse) {
        if (!hasPermission(context))
            return

        val current = weather.current
        val today = weather.forecast.forecastday.firstOrNull()
        val unit = context.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
            .getString("temp_unit", "C") ?: "C"
        val tempNow = WeatherUtils.formatTemp(current.temperature, unit)
        val tempMin = today?.day?.tempMin?.let { WeatherUtils.formatTemp(it, unit) } ?: "--"
        val tempMax = today?.day?.tempMax?.let { WeatherUtils.formatTemp(it, unit) } ?: "--"

        val title = "${weather.location.name}: $tempNow"
        val text = "${current.condition.text} - $tempMin / $tempMax - ${context.getString(R.string.notification_rain_chance)} ${today?.day?.chanceOfRain ?: 0}%"

        post(
            context, CHANNEL_ID_DAILY, NOTIFICATION_ID_DAILY,
            weather.location.name, title, text,
            NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // изменение погоды
    fun showWeatherChange(
        context: Context,
        city: String,
        oldTemp: Double,
        newTemp: Double,
        conditionText: String,
        unit: String
    ) {
        if (!hasPermission(context))
            return

        val title = context.getString(R.string.notification_change_title, city)
        val oldStr = WeatherUtils.formatTemp(oldTemp, unit)
        val newStr = WeatherUtils.formatTemp(newTemp, unit)
        val text = context.getString(R.string.notification_change_text, oldStr, newStr, conditionText)

        post(
            context, CHANNEL_ID_ALERTS, NOTIFICATION_ID_CHANGE,
            city, title, text, NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // плохие условия
    fun showSevereWeather(context: Context, city: String, alertText: String, conditionText: String) {
        if (!hasPermission(context))
            return

        val title = context.getString(R.string.notification_severe_title, city)
        val text = "$alertText · $conditionText"

        post(
            context, CHANNEL_ID_ALERTS, NOTIFICATION_ID_SEVERE,
            city, title, text, NotificationCompat.PRIORITY_HIGH
        )
    }

    // смена города
    fun showMovement(context: Context, city: String, temperature: Double, conditionText: String, unit: String) {
        if (!hasPermission(context))
            return

        val title = context.getString(R.string.notification_movement_title)
        val temp = WeatherUtils.formatTemp(temperature, unit)
        val text = context.getString(R.string.notification_movement_text, city, temp, conditionText)

        post(
            context, CHANNEL_ID_ALERTS, NOTIFICATION_ID_MOVEMENT,
            city, title, text, NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // сборка уведомления
    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        city: String,
        title: String,
        text: String,
        priority: Int
    ) {
        // Intent(context, MainActivity::class.java) - намерение запустить MainActivity
        // FLAG_ACTIVITY_NEW_TASK - разрешает запуск из контекста, не связанного с Activity (например, из уведомления)
        // FLAG_ACTIVITY_CLEAR_TOP - если MainActivity уже открыта, она не создаётся заново, а очищается до неё.
        // putExtra(EXTRA_CITY, city) - чтобы MainActivity знала, какой город открыть.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CITY, city)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,   // чтобы интенты не перекрывались
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priority)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) { } // разрешение могли отозвать
    }

    // проверка, что разрешение на уведомления выдано
    private fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}