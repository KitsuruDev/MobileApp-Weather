package com.example.appweather

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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

    // ViewModel создаётся в Activity, чтобы можно было вызвать loadWeatherFromNotification() из handleIntent() до Composite()
    private val weatherViewModel: WeatherViewModel by viewModels()

    // регистрирует обработчик, который сработает после принятия диалога "Разрешить уведомления"
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = AppSettings.load(newBase).language
        val locale = Locale(languageCode)
        val config = Configuration(newBase.resources.configuration).apply { setLocales(LocaleList(locale)) }
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

        // если приложение открыто кликом по уведомлению - загружается город из extra
        handleIntent(intent)
    }

    // если приложение уже было в памяти и пришло новое уведомление
    // (без этого повторный клик по уведомлению при открытом приложении не переключил бы город)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val city = intent?.getStringExtra(NotificationHelper.EXTRA_CITY) ?: return

        // предотвращение перезаписи города из initializeApp
        weatherViewModel.loadWeatherFromNotification(city)

        // очистка extra
        intent.removeExtra(NotificationHelper.EXTRA_CITY)
    }
}