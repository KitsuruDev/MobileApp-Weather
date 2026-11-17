package com.example.appweather

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appweather.data.AppSettings
import com.example.appweather.ui.screens.WeatherScreen
import com.example.appweather.ui.theme.AppWeatherTheme
import com.example.appweather.viewmodel.WeatherViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

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

        setContent {
            val weatherViewModel: WeatherViewModel = viewModel()

            AppWeatherTheme {
                WeatherScreen(viewModel = weatherViewModel)
            }
        }
    }
}