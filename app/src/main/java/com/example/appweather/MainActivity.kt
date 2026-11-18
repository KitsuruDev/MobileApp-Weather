package com.example.appweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appweather.data.AppSettings
import com.example.appweather.notifications.NotificationHelper
import com.example.appweather.notifications.NotificationScheduler
import com.example.appweather.ui.screens.WeatherScreen
import com.example.appweather.ui.theme.AppWeatherTheme
import com.example.appweather.viewmodel.WeatherViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // обработчик запроса разрешения на уведомления (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат нам не нужен: если отклонили, просто не будет пушей */ }

    override fun attachBaseContext(newBase: Context) {
        // 1. Загружаем настройки и определяем локаль
        val languageCode = AppSettings.load(newBase).language
        val locale = Locale(languageCode)

        // 2. Создаем конфигурацию с нужной локалью
        val config = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }

        // 3. Применяем конфигурацию к контексту Activity
        applyOverrideConfiguration(config)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // канал уведомлений можно создавать всегда — повторный вызов безопасен
        NotificationHelper.createChannel(this)

        // на Android 13+ без разрешения уведомления не показываются — запрашиваем
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // переустанавливаем расписание, если пользователь его включал ранее
        val settings = AppSettings.load(this)
        NotificationScheduler.reschedule(this, settings.notificationsEnabled, settings.notificationTime)

        setContent {
            val weatherViewModel: WeatherViewModel = viewModel()

            AppWeatherTheme {
                WeatherScreen(viewModel = weatherViewModel)
            }
        }
    }
}