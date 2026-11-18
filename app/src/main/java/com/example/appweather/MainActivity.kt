package com.example.appweather

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.appweather.data.AppSettings
import com.example.appweather.notifications.NotificationHelper
import com.example.appweather.notifications.NotificationScheduler
import com.example.appweather.ui.screens.WeatherScreen
import com.example.appweather.ui.theme.AppWeatherTheme
import com.example.appweather.viewmodel.WeatherViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ViewModel создаётся в Activity (а не в setContent), чтобы можно было вызвать
    // loadWeatherFromNotification() из handleIntent() до композиции
    private val weatherViewModel: WeatherViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = AppSettings.load(newBase).language
        val locale = Locale(languageCode)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        applyOverrideConfiguration(config)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannels(this)

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val settings = AppSettings.load(this)
        NotificationScheduler.reschedule(this, settings.notificationsEnabled, settings.notificationTime)
        NotificationScheduler.rescheduleAlerts(
            this,
            settings.weatherChangeNotificationsEnabled || settings.severeWeatherNotificationsEnabled
        )

        setContent {
            AppWeatherTheme {
                WeatherScreen(viewModel = weatherViewModel)
            }
        }

        // если приложение открыто кликом по уведомлению — загружаем город из extra
        handleIntent(intent)
    }

    // если приложение уже было в памяти и пришло новое уведомление
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val city = intent?.getStringExtra(NotificationHelper.EXTRA_CITY) ?: return
        // предотвращаем перезапись города из initializeApp
        weatherViewModel.loadWeatherFromNotification(city)
        // очищаем extra, чтобы повторный вызов onCreate с тем же intent не грузил город снова
        intent.removeExtra(NotificationHelper.EXTRA_CITY)
    }
}